package systems.glam.services.fulfillment;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.sysvar.Clock;
import software.sava.core.accounts.token.TokenAccount;
import software.sava.core.tx.Instruction;
import software.sava.core.tx.Transaction;
import software.sava.core.util.LamportDecimal;
import software.sava.idl.clients.spl.token.gen.types.Mint;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import software.sava.services.core.net.http.NotifyClient;
import software.sava.services.core.remote.call.Backoff;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.sdk.idl.programs.glam.mint.gen.types.RequestQueue;
import systems.glam.services.execution.InstructionProcessor;
import systems.glam.services.tokens.MintContext;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.lang.Long.toUnsignedString;
import static java.lang.System.Logger.Level.*;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

final class SingleAssetFulfillmentService implements FulfillmentService, Consumer<AccountInfo<byte[]>> {

  private static final System.Logger logger = System.getLogger(FulfillmentService.class.getName());

  private final GlamAccountClient glamAccountClient;
  private final PublicKey glamMintProgram;
  private final String vaultName;
  private final MintContext baseAssetMintContext;
  private final PublicKey baseAssetTokenAccountKey;
  private final PublicKey clockSysVar;
  private final boolean isSoftRedeem;
  private final boolean softRedeem;
  private final PublicKey requestQueueKey;
  private final long redeemNoticePeriod;
  private final boolean redeemWindowInSeconds;
  private final MintContext vaultMintContext;
  private final List<PublicKey> accountsNeededList;
  private final Map<PublicKey, AccountInfo<byte[]>> accountsNeededMap;
  private final NotifyClient notifyClient;
  private final RpcCaller rpcCaller;
  private final List<Instruction> fulFillInstructions;
  private final InstructionProcessor instructionProcessor;
  private final Function<List<Instruction>, Transaction> transactionFactory;
  private final PublicKey feePayerKey;
  private final BigInteger warnFeePayerBalance;
  private final BigInteger minFeePayerBalance;
  private final long minCheckStateDelayNanos;
  private final long maxCheckStateDelayNanos;
  private final Backoff backoff;

  private final AtomicReference<TokenBalance> baseAssetTokenBalance;
  private final AtomicReference<RedemptionSummary> redemptionSummary;
  private final ReentrantLock lock;
  private final Condition stateChange;

  SingleAssetFulfillmentService(final GlamAccountClient glamAccountClient,
                                final PublicKey glamMintProgram,
                                final String vaultName,
                                final MintContext vaultMintContext,
                                final MintContext baseAssetMintContext,
                                final PublicKey clockSysVar,
                                final boolean isSoftRedeem,
                                final boolean softRedeem,
                                final PublicKey requestQueueKey,
                                final long redeemNoticePeriod,
                                final boolean redeemWindowInSeconds,
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
    this.glamAccountClient = glamAccountClient;
    this.glamMintProgram = glamMintProgram;
    this.vaultName = vaultName;
    this.vaultMintContext = vaultMintContext;
    this.baseAssetMintContext = baseAssetMintContext;
    this.baseAssetTokenAccountKey = baseAssetMintContext.vaultATA();
    this.clockSysVar = clockSysVar;
    this.isSoftRedeem = isSoftRedeem;
    this.softRedeem = softRedeem;
    this.requestQueueKey = requestQueueKey;
    this.redeemNoticePeriod = redeemNoticePeriod;
    this.redeemWindowInSeconds = redeemWindowInSeconds;
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
    this.baseAssetTokenBalance = new AtomicReference<>();
    this.redemptionSummary = new AtomicReference<>();
    this.lock = new ReentrantLock();
    this.stateChange = lock.newCondition();
  }

  private void backoff(final long failureCount) throws InterruptedException {
    NANOSECONDS.sleep(Math.max(minCheckStateDelayNanos, backoff.delay(failureCount, NANOSECONDS)));
  }

  private void fetchAccounts() {
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

  private void notifyLowBalance(final BigInteger lamports, final boolean notify) {
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

  @Override
  public void run() {
    try {
      final boolean softRedeem = this.isSoftRedeem && this.softRedeem;
      boolean notifyLowBalance = true;
      for (long failureCount = 0; ; ) {
        fetchAccounts();

        final var feePayerAccountInfo = accountsNeededMap.get(feePayerKey);
        final var feePayerBalance = feePayerAccountInfo.amount();
        if (feePayerBalance.compareTo(minFeePayerBalance) < 0) {
          notifyLowBalance(feePayerBalance, notifyLowBalance);
          if (notifyLowBalance) {
            notifyLowBalance = false;
          }
          awaitChange();
          continue;
        } else if (feePayerBalance.compareTo(warnFeePayerBalance) < 0) {
          notifyLowBalance(feePayerBalance, notifyLowBalance);
          if (notifyLowBalance) {
            notifyLowBalance = false;
          }
        } else if (!notifyLowBalance) {
          notifyLowBalance = true;
        }

        final var tokenAccountInfo = accountsNeededMap.get(baseAssetTokenAccountKey);
        if (tokenAccountInfo == null) { // May be null if yet to receive a deposit.
          awaitChange();
          continue;
        }

        final var vaultMint = Mint.read(accountsNeededMap.get(vaultMintContext.mint()));
        final var vaultMintSupply = new BigDecimal(toUnsignedString(vaultMint.supply()))
            .movePointLeft(vaultMintContext.decimals())
            .stripTrailingZeros();

        final var baseAssetTokenAccount = TokenAccount.read(tokenAccountInfo.pubKey(), tokenAccountInfo.data());
        compareAndSet(new TokenBalance(tokenAccountInfo.context().slot(), baseAssetTokenAccount.amount()));
        final var vaultHoldings = new BigDecimal(toUnsignedString(baseAssetTokenAccount.amount()))
            .movePointLeft(baseAssetMintContext.decimals())
            .stripTrailingZeros();

        // TODO: account for vault fees
        // SEVERE: Vault mint supply 999000 is greater than holdings 1
//        if (vaultMintSupply.compareTo(vaultHoldings) > 0) {
//          final var msg = String.format("Vault mint supply %s is greater than holdings %s", vaultMintSupply, vaultHoldings);
//          logger.log(ERROR, msg);
//          throw new IllegalStateException(msg);
//        }

        final var clockAccount = accountsNeededMap.get(clockSysVar);
        final var clock = Clock.read(clockSysVar, clockAccount.data());
        final var redemptionSummary = RedemptionSummary.createSummary(
            clock.unixTimestamp() + 200,
            accountsNeededMap.get(requestQueueKey),
            redeemNoticePeriod,
            redeemWindowInSeconds
        );
        compareAndSet(redemptionSummary);

        final var totalOutstandingShares = redemptionSummary.outstandingShares();
        final var fulfillableShares = redemptionSummary.fulfillableShares();
        if (fulfillableShares.signum() > 0 || (softRedeem && totalOutstandingShares.signum() > 0)) {
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
          final boolean fulfilled = instructionProcessor.processInstructions(
              vaultName + " Fulfill Redemptions",
              fulfillInstructions,
              transactionFactory
          );
          if (fulfilled) {
            failureCount = 0;
          } else {
            backoff(++failureCount);
          }
          continue;
        }

        final var nav = vaultHoldings.divide(vaultMintSupply, 4, RoundingMode.HALF_EVEN).stripTrailingZeros();
        logger.log(INFO, String.format("""
                    {
                     "name": "%s",
                     "mintSupply": %s,
                     "baseSupply": %s,
                     "nav": "%s"
                    }""",
                vaultName,
                vaultMintSupply.toPlainString(),
                vaultHoldings.toPlainString(),
                nav.toPlainString()
            )
        );

        awaitChange();
      }
    } catch (final InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void subscribe(final SolanaRpcWebsocket websocket) {
    websocket.accountSubscribe(requestQueueKey, this);
    websocket.accountSubscribe(baseAssetTokenAccountKey, this);
  }

  record TokenBalance(long slot, long amount) {

  }

  private void wakeUp() {
    lock.lock();
    try {
      stateChange.signalAll();
    } finally {
      lock.unlock();
    }
  }

  private void awaitChange() throws InterruptedException {
    lock.lock();
    try {
      final long remainingNanos = stateChange.awaitNanos(maxCheckStateDelayNanos);
      final long sleptNanos = maxCheckStateDelayNanos - remainingNanos;
      if (sleptNanos < minCheckStateDelayNanos) {
        NANOSECONDS.sleep(minCheckStateDelayNanos - sleptNanos);
      }
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void accept(final AccountInfo<byte[]> accountInfo) {
    try {
      final long slot = accountInfo.context().slot();
      final byte[] data = accountInfo.data();
      final var owner = accountInfo.owner();

      if (RequestQueue.DISCRIMINATOR.equals(data, 0) && owner.equals(glamMintProgram)) {
        final var redemptionSummary = RedemptionSummary.createSummary(
            Instant.now().getEpochSecond(), slot,
            RequestQueue.read(accountInfo),
            redeemNoticePeriod, redeemWindowInSeconds
        );
        final var previousAmount = compareAndSet(redemptionSummary);
        if (previousAmount != null && previousAmount.compareTo(redemptionSummary.outstandingShares()) != 0) {
          wakeUp();
        }
      } else if (data.length == TokenAccount.BYTES && owner.equals(baseAssetMintContext.tokenProgram())) {
        final var tokenAccount = TokenAccount.read(accountInfo.pubKey(), data);
        if (tokenAccount.mint().equals(baseAssetMintContext.mint())) {
          final var baseAssetAmount = tokenAccount.amount();
          final var tokenBalance = new TokenBalance(slot, baseAssetAmount);
          final long previousAmount = compareAndSet(tokenBalance);
          if (Long.compareUnsigned(baseAssetAmount, previousAmount) > 0) {
            final var redemptionSummary = this.redemptionSummary.get();
            if (redemptionSummary != null && redemptionSummary.outstandingShares().signum() > 0) {
              wakeUp();
            }
          }
        }
      }
    } catch (final RuntimeException ex) {
      logger.log(ERROR, "Failed to process Glam State Change: " + Base64.getEncoder().encodeToString(accountInfo.data()), ex);
    }
  }

  private BigDecimal compareAndSet(final RedemptionSummary redemptionSummary) {
    final long slot = redemptionSummary.slot();
    for (RedemptionSummary previous = this.redemptionSummary.get(), witness; ; ) {
      if (previous == null) {
        witness = this.redemptionSummary.compareAndExchange(null, redemptionSummary);
        if (witness == null) {
          return BigDecimal.ZERO;
        } else {
          previous = witness;
        }
      } else if (Long.compareUnsigned(slot, previous.slot()) > 0) {
        witness = this.redemptionSummary.compareAndExchange(previous, redemptionSummary);
        if (witness == previous) {
          return previous.outstandingShares();
        } else {
          previous = witness;
        }
      } else {
        return null;
      }
    }
  }

  private long compareAndSet(final TokenBalance tokenBalance) {
    final long slot = tokenBalance.slot();
    for (TokenBalance previous = this.baseAssetTokenBalance.get(), witness; ; ) {
      if (previous == null) {
        witness = this.baseAssetTokenBalance.compareAndExchange(null, tokenBalance);
        if (witness == null) {
          return 0;
        } else {
          previous = witness;
        }
      } else if (Long.compareUnsigned(slot, previous.slot()) > 0) {
        witness = this.baseAssetTokenBalance.compareAndExchange(previous, tokenBalance);
        if (witness == previous) {
          return previous.amount();
        } else {
          previous = witness;
        }
      } else {
        return -1;
      }
    }
  }
}
