package systems.glam.services.fulfillment;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.sysvar.Clock;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.spl.token.gen.types.Mint;
import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.sdk.StateAccountClient;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateAccount;
import systems.glam.services.ServiceContext;
import systems.glam.services.fulfillment.accounting.RedemptionSummary;
import systems.glam.services.tokens.MintContext;

import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static java.lang.System.Logger.Level.WARNING;
import static java.util.concurrent.TimeUnit.*;

public abstract class BaseFulfillmentService implements FulfillmentService, Consumer<AccountInfo<byte[]>> {

  protected static final System.Logger logger = System.getLogger(FulfillmentService.class.getName());

  protected final ServiceContext context;
  protected final GlamAccountClient glamAccountClient;
  protected final String vaultName;
  protected final boolean softRedeem;
  protected final long redeemNoticePeriod;
  protected final boolean redeemWindowInSeconds;
  protected final MintContext baseAssetMintContext;
  protected final PublicKey baseAssetVaultAta;
  protected final boolean isSoftRedeem;
  protected final PublicKey requestQueueKey;
  protected final MintContext vaultMintContext;
  protected final List<PublicKey> accountsNeededList;
  protected final Map<PublicKey, AccountInfo<byte[]>> accountsNeededMap;
  protected final List<Instruction> fulFillInstructions;
  protected final ReentrantLock lock;
  protected final Condition stateChange;

  protected BaseFulfillmentService(final ServiceContext context,
                                   final GlamAccountClient glamAccountClient,
                                   final StateAccountClient stateAccountClient,
                                   final MintContext baseAssetMintContext,
                                   final PublicKey baseAssetVaultAta,
                                   final boolean softRedeem,
                                   final PublicKey requestQueueKey,
                                   final MintContext vaultMintContext,
                                   final List<PublicKey> accountsNeededList,
                                   final List<Instruction> fulFillInstructions) {
    this.context = context;
    this.glamAccountClient = glamAccountClient;
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

  protected final Clock clock() {
    final var clockSysVar = context.clockSysVar();
    final var clockAccount = accountsNeededMap.get(clockSysVar);
    return Clock.read(clockSysVar, clockAccount.data());
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
    final var clock = clock();
    return redemptionSummary(clock);
  }

  protected final PublicKey stateKey() {
    return glamAccountClient.vaultAccounts().glamStateKey();
  }

  protected final StateAccount stateAccount() {
    return StateAccount.read(accountsNeededMap.get(stateKey()));
  }

  protected final Mint vaultMint() {
    return Mint.read(accountsNeededMap.get(vaultMintContext.mint()));
  }

  protected long failureCount = 0;

  protected abstract void handleVault() throws InterruptedException;

  @Override
  public final void run() {
    try {
      for (; ; ) {
        fetchAccounts();
        if (context.feePayerBalanceLow()) {
          awaitChange();
          continue;
        }
        handleVault();
      }
    } catch (final InterruptedException ex) {
      Thread.currentThread().interrupt();
    } catch (final Throwable ex) {
      logger.log(WARNING, "Unexpected service failure.", ex);
    }
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
    final long minCheckStateDelayNanos = context.minCheckStateDelayNanos();
    final long maxCheckStateDelayNanos = context.maxCheckStateDelayNanos();
    lock.lock();
    try {
      final long remainingNanos = stateChange.awaitNanos(Math.min(Math.max(delayNanos, minCheckStateDelayNanos), maxCheckStateDelayNanos));
      final long sleptNanos = maxCheckStateDelayNanos - remainingNanos;
      if (sleptNanos < minCheckStateDelayNanos) {
        NANOSECONDS.sleep(minCheckStateDelayNanos - sleptNanos);
      }
    } finally {
      lock.unlock();
    }
  }

  protected final void awaitChange() throws InterruptedException {
    awaitChange(context.maxCheckStateDelayNanos());
  }

  protected final void fetchAccounts() {
    final var accountsNeeded = context.rpcCaller().courteousGet(
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

  protected final long redemptionAvailableIn(final RedemptionSummary redemptionSummary) {
    final var softFulfillable = redemptionSummary.softFulfillable();
    if (softFulfillable.isEmpty()) {
      return context.maxCheckStateDelayNanos();
    }
    final long availableAt = softFulfillable.getFirst().createdAt() + redeemNoticePeriod;
    if (redeemWindowInSeconds) {
      return SECONDS.toNanos(availableAt - redemptionSummary.epochSeconds());
    } else {
      final long millisPerSlot = context.medianMillisPerSlot();
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
        this.fulFillInstructions.stream().limit(numInstructions - 1).forEach(fulFillInstructions::add);
        final var fulFillIx = glamAccountClient.fulfill(
            0,
            baseAssetMintContext.mint(), baseAssetMintContext.tokenProgram(),
            OptionalInt.of(redemptionSummary.fulfillable().size())
        );
        fulfillInstructions.add(fulFillIx);
      }
    } else {
      fulfillInstructions = new ArrayList<>(this.fulFillInstructions);
    }
    return context.processInstructions(vaultName + " Fulfill Redemptions", fulfillInstructions);
  }


}
