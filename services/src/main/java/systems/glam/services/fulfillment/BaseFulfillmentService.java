package systems.glam.services.fulfillment;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.sysvar.Clock;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.spl.token.gen.types.Mint;
import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.sdk.StateAccountClient;
import systems.glam.services.BaseDelegateService;
import systems.glam.services.execution.ExecutionServiceContext;
import systems.glam.services.fulfillment.accounting.RedemptionSummary;
import systems.glam.services.mints.MintContext;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static java.lang.System.Logger.Level.ERROR;
import static java.util.concurrent.TimeUnit.*;

public abstract class BaseFulfillmentService extends BaseDelegateService
    implements FulfillmentService, Consumer<AccountInfo<byte[]>> {

  protected static final System.Logger logger = System.getLogger(FulfillmentService.class.getName());

  /// How long [#run] awaits the posts of the page of its end before it returns: the fulfillment
  /// entrypoint exits the process as soon as run() returns, and ravina's NotifyClientImpl posts
  /// asynchronously, so the page must leave first.
  private static final long END_PAGE_DELIVERY_SECONDS = 30L;

  /// The most code points of a failure's message the page of the loop's end carries.
  private static final int MAX_FAILURE_EXCERPT = 200;

  /// What sava's HTTP clients write before the response body in the message of a failed request:
  /// `HTTP request failed with [httpCode:%d], [body=%s]`, and `Failed to parse` in its place when
  /// the body did not parse.
  private static final String HTTP_BODY_MARKER = "[body=";

  protected final ExecutionServiceContext serviceContext;
  protected final List<PublicKey> accountsNeededList;
  protected final Map<PublicKey, AccountInfo<byte[]>> accountsNeededMap;
  protected final boolean softRedeem;
  protected final long redeemNoticePeriod;
  protected final boolean redeemWindowInSeconds;
  protected final String vaultName;
  protected final MintContext baseAssetMintContext;
  protected final PublicKey baseAssetVaultAta;
  protected final boolean isSoftRedeem;
  protected final PublicKey requestQueueKey;
  protected final MintContext vaultMintContext;
  protected final List<Instruction> fulFillInstructions;
  protected final ReentrantLock lock;
  protected final Condition stateChange;

  protected BaseFulfillmentService(final GlamAccountClient glamAccountClient,
                                   final ExecutionServiceContext serviceContext,
                                   final StateAccountClient stateAccountClient,
                                   final MintContext baseAssetMintContext,
                                   final PublicKey baseAssetVaultAta,
                                   final boolean softRedeem,
                                   final PublicKey requestQueueKey,
                                   final MintContext vaultMintContext,
                                   final List<PublicKey> accountsNeededList,
                                   final List<Instruction> fulFillInstructions) {
    super(glamAccountClient);
    this.serviceContext = serviceContext;
    this.baseAssetVaultAta = baseAssetVaultAta;
    this.vaultName = stateAccountClient.name();
    this.isSoftRedeem = stateAccountClient.softRedeem();
    this.redeemNoticePeriod = stateAccountClient.redeemNoticePeriod();
    this.redeemWindowInSeconds = stateAccountClient.redeemWindowInSeconds();
    this.baseAssetMintContext = baseAssetMintContext;
    this.softRedeem = softRedeem;
    this.requestQueueKey = requestQueueKey;
    this.vaultMintContext = vaultMintContext;
    this.accountsNeededList = accountsNeededList;
    this.accountsNeededMap = HashMap.newHashMap(accountsNeededList.size());
    this.fulFillInstructions = fulFillInstructions;
    this.lock = new ReentrantLock();
    this.stateChange = lock.newCondition();
  }

  protected abstract void handleVault() throws InterruptedException;

  protected final void fetchAccounts() {
    final var accountsNeeded = serviceContext.rpcCaller().courteousGet(
        rpcClient -> rpcClient.getAccounts(accountsNeededList),
        "rpcClient::getPositionRelatedAccounts"
    );
    accountsNeededMap.clear();
    for (final var accountInfo : accountsNeeded) {
      if (accountInfo != null) {
        accountsNeededMap.put(accountInfo.pubKey(), accountInfo);
      }
    }
  }

  protected final RedemptionSummary redemptionSummary(final Clock clock) {
    return RedemptionSummary.createSummary(
        clock.unixTimestamp(),
        accountsNeededMap.get(requestQueueKey),
        redeemNoticePeriod,
        redeemWindowInSeconds
    );
  }

  protected final RedemptionSummary redemptionSummary() {
    final var clock = serviceContext.clock(accountsNeededMap);
    return redemptionSummary(clock);
  }

  protected final Mint vaultMint() {
    return Mint.read(accountsNeededMap.get(vaultMintContext.mint()));
  }

  protected long failureCount = 0;

  /// Fetches the accounts, checks the fee payer and handles the vault, round after round, until
  /// the loop fails or this thread is interrupted. An interrupt is a shutdown: the loop ends
  /// quietly, and the interrupt is kept. Any other end is a failure the loop does not survive:
  /// it is logged at ERROR, naming it, and posted as one page saying that the loop has ended on
  /// it and that the process exits for a supervisor to restart it, which is what the fulfillment
  /// entrypoint does as soon as this returns; the page's posts are awaited for at most
  /// [#END_PAGE_DELIVERY_SECONDS] first, so that the exit does not cut them off. A failure the
  /// instruction processor has already paged (a stopped transaction monitor, a send no peer
  /// accepted whose expiry was not verified, any other failure of a send) is therefore paged
  /// twice, its own page and then this one: a duplicate the owner accepts.
  @Override
  public final void run() {
    try {
      for (; ; ) {
        fetchAccounts();
        if (serviceContext.feePayerBalanceLow()) {
          awaitChange();
          continue;
        }
        handleVault();
      }
    } catch (final InterruptedException ex) {
      Thread.currentThread().interrupt();
    } catch (final Throwable ex) {
      logger.log(ERROR, "The fulfillment loop has ended: " + ex, ex);
      pageTheEnd(ex);
    }
  }

  /// Posts the page that the loop has ended on `failure` and awaits its posts. A notify client
  /// that throws (ravina's does, with RejectedExecutionException, once its executor has shut
  /// down) loses the page and nothing else: the loss is logged at ERROR.
  private void pageTheEnd(final Throwable failure) {
    final List<CompletableFuture<String>> posts;
    try {
      posts = serviceContext.instructionProcessor().notifyClient().postMsg(String.format(
          "The %s fulfillment loop has ended, and the process exits for a supervisor to restart it. Failure: %s",
          vaultName, describeFailure(failure)
      ));
    } catch (final RuntimeException notifyFailure) {
      logger.log(ERROR, "Failed to post the page that the fulfillment loop has ended.", notifyFailure);
      return;
    }
    awaitDelivery(posts);
  }

  /// Awaits `posts` for at most [#END_PAGE_DELIVERY_SECONDS]; a null list, or a null element of
  /// one, is no post. A post that fails or outlasts the bound is logged at ERROR; an interrupt
  /// ends the wait and is kept. InstructionProcessorImpl awaits its fatal pages the same way, in a
  /// helper private to the execution package: these few lines are duplicated rather than shared.
  private static void awaitDelivery(final List<CompletableFuture<String>> posts) {
    if (posts == null) {
      return;
    }
    final var delivering = posts.stream().filter(Objects::nonNull).toArray(CompletableFuture<?>[]::new);
    try {
      CompletableFuture.allOf(delivering).get(END_PAGE_DELIVERY_SECONDS, SECONDS);
    } catch (final TimeoutException | ExecutionException ex) {
      logger.log(ERROR, "The page that the fulfillment loop has ended may not have been delivered.", ex);
    } catch (final InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  /// `failure` as the page of the loop's end names it: its class, and the first line of its
  /// message with any response body sava's HTTP clients wrote into it cut off, its control
  /// characters stripped and at most [#MAX_FAILURE_EXCERPT] code points of it kept. A page is an
  /// alert, not a log: the full failure is in the ERROR record [#run] writes beside it. The class
  /// alone when nothing of the message is left.
  static String describeFailure(final Throwable failure) {
    final var className = failure.getClass().getName();
    var excerpt = Objects.requireNonNullElse(failure.getMessage(), "").lines().findFirst().orElse("");
    final int body = excerpt.indexOf(HTTP_BODY_MARKER);
    if (body != -1) {
      excerpt = excerpt.substring(0, body) + "[body omitted]";
    }
    excerpt = excerpt.replaceAll("\\p{Cc}", "");
    if (excerpt.codePointCount(0, excerpt.length()) > MAX_FAILURE_EXCERPT) {
      excerpt = excerpt.substring(0, excerpt.offsetByCodePoints(0, MAX_FAILURE_EXCERPT)) + "...";
    }
    return excerpt.isEmpty() ? className : className + ": " + excerpt;
  }


  protected final void wakeUp() {
    lock.lock();
    try {
      stateChange.signalAll();
    } finally {
      lock.unlock();
    }
  }

  protected final void awaitChange(final long delayNanos) throws InterruptedException {
    final long minCheckStateDelayNanos = serviceContext.minCheckStateDelayNanos();
    final long maxCheckStateDelayNanos = serviceContext.maxCheckStateDelayNanos();
    lock.lock();
    try {
      final long waitNanos = Math.min(Math.max(delayNanos, minCheckStateDelayNanos), maxCheckStateDelayNanos);
      final long remainingNanos = stateChange.awaitNanos(waitNanos);
      // what passed of this wait, not of the ceiling: a shorter wait woken early has slept
      // only part of its own delay, and the floor still applies to it
      final long sleptNanos = waitNanos - remainingNanos;
      if (sleptNanos < minCheckStateDelayNanos) {
        NANOSECONDS.sleep(floorTopUpNanos(sleptNanos, minCheckStateDelayNanos));
      }
    } finally {
      lock.unlock();
    }
  }

  /// The rest of the minimum delay a wait that slept `sleptNanos` short of it still owes: the
  /// floor less what passed, not the floor plus it.
  static long floorTopUpNanos(final long sleptNanos, final long minCheckStateDelayNanos) {
    return minCheckStateDelayNanos - sleptNanos;
  }

  protected final void awaitChange() throws InterruptedException {
    awaitChange(serviceContext.maxCheckStateDelayNanos());
  }

  protected final long redemptionAvailableIn(final RedemptionSummary redemptionSummary) {
    final var softFulfillable = redemptionSummary.softFulfillable();
    if (softFulfillable.isEmpty()) {
      return serviceContext.maxCheckStateDelayNanos();
    }
    final long availableAt = softFulfillable.getFirst().createdAt() + redeemNoticePeriod;
    if (redeemWindowInSeconds) {
      return SECONDS.toNanos(availableAt - redemptionSummary.epochSeconds());
    } else {
      final long millisPerSlot = serviceContext.medianMillisPerSlot();
      return MILLISECONDS.toNanos((availableAt - redemptionSummary.slot()) * millisPerSlot);
    }
  }

  protected final boolean hasFulfillableRedemptions(final RedemptionSummary redemptionSummary) {
    return redemptionSummary.fulfillableShares().signum() > 0
        || ((this.isSoftRedeem && this.softRedeem) && redemptionSummary.softFulfillableShares().signum() > 0);
  }

  protected final boolean executeRedemptions(final RedemptionSummary redemptionSummary) throws InterruptedException {
    final List<Instruction> fulfillInstructions;
    if (isSoftRedeem) {
      if (this.softRedeem) {
        fulfillInstructions = new ArrayList<>(this.fulFillInstructions);
      } else {
        final int numInstructions = this.fulFillInstructions.size();
        fulfillInstructions = new ArrayList<>(numInstructions);
        this.fulFillInstructions.stream().limit(numInstructions - 1).forEach(fulfillInstructions::add);
        final var fulFillIx = glamAccountClient.fulfill(
            0,
            baseAssetMintContext.mint(), baseAssetMintContext.tokenProgram(),
            OptionalLong.of(redemptionSummary.fulfillable().size())
        );
        fulfillInstructions.add(fulFillIx);
      }
    } else {
      fulfillInstructions = new ArrayList<>(this.fulFillInstructions);
    }
    return serviceContext.processInstructions(vaultName + " Fulfill Redemptions", fulfillInstructions);
  }
}
