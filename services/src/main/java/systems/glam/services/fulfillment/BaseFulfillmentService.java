package systems.glam.services.fulfillment;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.sysvar.Clock;
import software.sava.core.tx.Instruction;
import software.sava.core.tx.Transaction;
import software.sava.core.util.LamportDecimal;
import software.sava.idl.clients.spl.token.gen.types.Mint;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.services.core.net.http.NotifyClient;
import software.sava.services.core.remote.call.Backoff;
import software.sava.services.solana.epoch.EpochInfoService;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.sdk.StateAccountClient;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateAccount;
import systems.glam.services.execution.InstructionProcessor;
import systems.glam.services.fulfillment.accounting.RedemptionSummary;
import systems.glam.services.tokens.MintContext;

import java.math.BigInteger;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.WARNING;
import static java.util.concurrent.TimeUnit.*;

public abstract class BaseFulfillmentService implements FulfillmentService, Consumer<AccountInfo<byte[]>> {

  protected static final System.Logger logger = System.getLogger(FulfillmentService.class.getName());

  protected final EpochInfoService epochInfoService;
  protected final GlamAccountClient glamAccountClient;
  protected final PublicKey glamMintProgram;
  protected final PublicKey stateKey;
  protected final String vaultName;
  protected final boolean softRedeem;
  protected final long redeemNoticePeriod;
  protected final boolean redeemWindowInSeconds;
  protected final MintContext baseAssetMintContext;
  protected final PublicKey baseAssetTokenAccountKey;
  protected final PublicKey clockSysVar;
  protected final boolean isSoftRedeem;
  protected final PublicKey requestQueueKey;
  protected final MintContext vaultMintContext;
  protected final List<PublicKey> accountsNeededList;
  protected final Map<PublicKey, AccountInfo<byte[]>> accountsNeededMap;
  protected final NotifyClient notifyClient;
  protected final RpcCaller rpcCaller;
  protected final List<Instruction> fulFillInstructions;
  protected final InstructionProcessor instructionProcessor;
  protected final Function<List<Instruction>, Transaction> transactionFactory;
  protected final PublicKey feePayerKey;
  protected final BigInteger warnFeePayerBalance;
  protected final BigInteger minFeePayerBalance;
  protected final long minCheckStateDelayNanos;
  protected final long maxCheckStateDelayNanos;
  protected final Backoff backoff;
  protected final ReentrantLock lock;
  protected final Condition stateChange;
  private boolean notifyLowBalance;

  protected BaseFulfillmentService(final EpochInfoService epochInfoService,
                                   final GlamAccountClient glamAccountClient,
                                   final PublicKey glamMintProgram,
                                   final StateAccountClient stateAccountClient,
                                   final MintContext baseAssetMintContext,
                                   final PublicKey clockSysVar,
                                   final boolean softRedeem,
                                   final PublicKey requestQueueKey,
                                   final MintContext vaultMintContext,
                                   final List<PublicKey> accountsNeededList,
                                   final RpcCaller rpcCaller,
                                   final List<Instruction> fulFillInstructions,
                                   final InstructionProcessor instructionProcessor,
                                   final Function<List<Instruction>, Transaction> transactionFactory,
                                   final PublicKey feePayerKey,
                                   final BigInteger warnFeePayerBalance,
                                   final BigInteger minFeePayerBalance,
                                   final Duration minCheckStateDelay,
                                   final Duration maxCheckStateDelay,
                                   final Backoff backoff) {
    this.epochInfoService = epochInfoService;
    this.glamAccountClient = glamAccountClient;
    this.glamMintProgram = glamMintProgram;
    this.stateKey = glamAccountClient.vaultAccounts().glamStateKey();
    this.vaultName = stateAccountClient.name();
    this.isSoftRedeem = stateAccountClient.softRedeem();
    this.redeemNoticePeriod = stateAccountClient.redeemNoticePeriod();
    this.redeemWindowInSeconds = stateAccountClient.redeemWindowInSeconds();
    this.baseAssetMintContext = baseAssetMintContext;
    this.baseAssetTokenAccountKey = baseAssetMintContext.vaultATA();
    this.clockSysVar = clockSysVar;
    this.softRedeem = softRedeem;
    this.requestQueueKey = requestQueueKey;
    this.vaultMintContext = vaultMintContext;
    this.accountsNeededList = accountsNeededList;
    this.accountsNeededMap = HashMap.newHashMap(accountsNeededList.size());
    this.notifyClient = instructionProcessor.notifyClient();
    this.rpcCaller = rpcCaller;
    this.fulFillInstructions = fulFillInstructions;
    this.instructionProcessor = instructionProcessor;
    this.transactionFactory = transactionFactory;
    this.feePayerKey = feePayerKey;
    this.warnFeePayerBalance = warnFeePayerBalance;
    this.minFeePayerBalance = minFeePayerBalance;
    this.minCheckStateDelayNanos = minCheckStateDelay.toNanos();
    this.maxCheckStateDelayNanos = maxCheckStateDelay.toNanos();
    this.backoff = backoff;
    this.lock = new ReentrantLock();
    this.stateChange = lock.newCondition();
  }

  protected final Clock clock() {
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

  protected final StateAccount stateAccount() {
    return StateAccount.read(accountsNeededMap.get(stateKey));
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
        if (feePayerBalanceLow()) {
          awaitChange();
          continue;
        }
        handleVault();
      }
    } catch (final InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  protected final void backoff(final long failureCount) throws InterruptedException {
    NANOSECONDS.sleep(Math.max(minCheckStateDelayNanos, backoff.delay(failureCount, NANOSECONDS)));
  }

  protected final void notifyLowBalance(final BigInteger lamports, final boolean notify) {
    final var sol = LamportDecimal.toBigDecimal(lamports).stripTrailingZeros();
    final var msg = String.format("""
            {
             "event": "Low Fee Payer Balance",
             "key": "%s",
             "balance": "%s"
            }
            """,
        feePayerKey.toBase58(),
        sol.toPlainString()
    );
    logger.log(WARNING, msg);
    if (notify) {
      for (final var stringCompletableFuture : notifyClient.postMsg(msg)) {
        logger.log(INFO, stringCompletableFuture.join());
      }
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
    awaitChange(maxCheckStateDelayNanos);
  }

  protected final void fetchAccounts() {
    final var accountsNeeded = rpcCaller.courteousGet(
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
      return maxCheckStateDelayNanos;
    }
    final long availableAt = softFulfillable.getFirst().createdAt() + redeemNoticePeriod;
    if (redeemWindowInSeconds) {
      return SECONDS.toNanos(availableAt - redemptionSummary.epochSeconds());
    } else {
      final long millisPerSlot = epochInfoService.epochInfo().medianMillisPerSlot();
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
    return instructionProcessor.processInstructions(
        vaultName + " Fulfill Redemptions",
        fulfillInstructions,
        transactionFactory
    );
  }

  private boolean feePayerBalanceLow() throws InterruptedException {
    final var feePayerAccountInfo = accountsNeededMap.get(feePayerKey);
    final var feePayerBalance = feePayerAccountInfo.amount();
    if (feePayerBalance.compareTo(minFeePayerBalance) < 0) {
      notifyLowBalance(feePayerBalance, notifyLowBalance);
      if (notifyLowBalance) {
        notifyLowBalance = false;
      }
      return true;
    } else if (feePayerBalance.compareTo(warnFeePayerBalance) < 0) {
      notifyLowBalance(feePayerBalance, notifyLowBalance);
      if (notifyLowBalance) {
        notifyLowBalance = false;
      }
    } else if (!notifyLowBalance) {
      notifyLowBalance = true;
    }
    return false;
  }
}
