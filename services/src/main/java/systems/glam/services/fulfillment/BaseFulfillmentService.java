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
import systems.glam.services.execution.Pages;
import systems.glam.services.fulfillment.accounting.RedemptionSummary;
import systems.glam.services.mints.MintContext;

import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.Logger.Level.ERROR;
import static java.util.concurrent.TimeUnit.*;

public abstract class BaseFulfillmentService extends BaseDelegateService
    implements FulfillmentService, Consumer<AccountInfo<byte[]>> {

  protected static final System.Logger logger = System.getLogger(FulfillmentService.class.getName());

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
  /// quietly, and the interrupt is kept, whether it arrived as an `InterruptedException` or
  /// wrapped in the failure of a fetch, which [#causedByInterrupt] tells from a failure (ravina's
  /// balanced call rethrows an interrupt of the fetch as a `RuntimeException` around it, with the
  /// flag cleared). Any other end is a failure the loop does not survive: it is logged at ERROR,
  /// naming it, and posted as one page saying that the loop has ended on it and that the process
  /// exits for a supervisor to restart it, which is what the fulfillment entrypoint does as soon
  /// as this returns; the page's posts are awaited for at most [Pages#PAGE_DELIVERY_SECONDS]
  /// first, so that the exit does not cut them off. A failure the instruction processor has
  /// already paged (a stopped transaction monitor, a send no peer accepted whose expiry was not
  /// verified, any other failure of a send) is therefore paged twice, its own page and then this
  /// one: a duplicate the owner accepts.
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
      if (causedByInterrupt(ex)) {
        logger.log(DEBUG, "The fulfillment loop was interrupted during a fetch, and ends quietly.", ex);
        Thread.currentThread().interrupt();
      } else {
        logger.log(ERROR, "The fulfillment loop has ended: " + ex, ex);
        pageTheEnd(ex);
      }
    }
  }

  /// Whether `failure` is, or wraps at any depth of its cause chain, an `InterruptedException`:
  /// the shutdown interrupt surfacing through a blocking fetch, which ravina's
  /// UncheckedBalancedCall rethrows as `RuntimeException(InterruptedException)` with the flag
  /// cleared, told the way AccountFetcherImpl tells it (ravina's transaction monitor reads only a
  /// cause and its cause). A cycle in the chain, which `initCause` permits between two or more
  /// throwables, ends the walk.
  static boolean causedByInterrupt(final Throwable failure) {
    return causedByInterrupt(failure, Collections.newSetFromMap(new IdentityHashMap<>()));
  }

  private static boolean causedByInterrupt(final Throwable cause, final Set<Throwable> seen) {
    if (cause == null || !seen.add(cause)) {
      return false;
    }
    return cause instanceof InterruptedException || causedByInterrupt(cause.getCause(), seen);
  }

  /// Posts the page that the loop has ended on `failure` through the context's notify client and
  /// awaits its posts for [Pages#PAGE_DELIVERY_SECONDS], under this module's one paging policy
  /// ([Pages]): the failure is named by its bounded summary, the whole of it being the ERROR
  /// record [#run] wrote beside it; a notify client that throws or answers null loses the page
  /// and nothing else, and the loss, like a post that fails or outlasts the bound, is logged at
  /// ERROR.
  private void pageTheEnd(final Throwable failure) {
    final var event = String.format("The %s fulfillment loop has ended", vaultName);
    final var posts = Pages.post(
        serviceContext.notifyClient(),
        event + ", and the process exits for a supervisor to restart it. Failure: " + Pages.summarize(failure),
        logger, event
    );
    Pages.awaitDelivery(event, posts, Pages.PAGE_DELIVERY_SECONDS, SECONDS, logger);
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
