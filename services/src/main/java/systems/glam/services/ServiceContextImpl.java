package systems.glam.services;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.token.TokenAccount;
import software.sava.core.tx.Instruction;
import software.sava.core.tx.Transaction;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.services.core.net.http.NotifyClient;
import software.sava.services.core.remote.call.Backoff;
import software.sava.services.solana.epoch.EpochInfoService;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.sdk.GlamAccounts;
import systems.glam.services.execution.InstructionProcessor;

import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

public final class ServiceContextImpl implements ServiceContext {

  private static final System.Logger logger = System.getLogger(ServiceContextImpl.class.getName());

  private final PublicKey serviceKey;
  private final BigInteger warnFeePayerBalance;
  private final BigInteger minFeePayerBalance;
  private final long minCheckStateDelayNanos;
  private final long maxCheckStateDelayNanos;
  private final Backoff backoff;
  private final EpochInfoService epochInfoService;
  private final SolanaAccounts solanaAccounts;
  private final GlamAccounts glamAccounts;
  private final NotifyClient notifyClient;
  private final RpcCaller rpcCaller;
  private final InstructionProcessor instructionProcessor;
  private final Function<List<Instruction>, Transaction> transactionFactory;

  public ServiceContextImpl(final PublicKey serviceKey,
                            final BigInteger warnFeePayerBalance, final BigInteger minFeePayerBalance,
                            final Duration minCheckStateDelay, final Duration maxCheckStateDelay,
                            final Backoff backoff,
                            final EpochInfoService epochInfoService,
                            final SolanaAccounts solanaAccounts,
                            final GlamAccounts glamAccounts,
                            final NotifyClient notifyClient,
                            final RpcCaller rpcCaller,
                            final InstructionProcessor instructionProcessor,
                            final Function<List<Instruction>, Transaction> transactionFactory) {
    this.serviceKey = serviceKey;
    this.warnFeePayerBalance = warnFeePayerBalance;
    this.minFeePayerBalance = minFeePayerBalance;
    this.minCheckStateDelayNanos = minCheckStateDelay.toNanos();
    this.maxCheckStateDelayNanos = maxCheckStateDelay.toNanos();
    this.backoff = backoff;
    this.epochInfoService = epochInfoService;
    this.solanaAccounts = solanaAccounts;
    this.glamAccounts = glamAccounts;
    this.notifyClient = notifyClient;
    this.rpcCaller = rpcCaller;
    this.instructionProcessor = instructionProcessor;
    this.transactionFactory = transactionFactory;
  }

  @Override
  public PublicKey clockSysVar() {
    return solanaAccounts.clockSysVar();
  }

  @Override
  public PublicKey tokenProgram() {
    return solanaAccounts.tokenProgram();
  }

  @Override
  public PublicKey token2022Program() {
    return solanaAccounts.token2022Program();
  }

  @Override
  public boolean isTokenAccount(final AccountInfo<byte[]> accountInfo) {
    final var programOwner = accountInfo.owner();
    if (programOwner.equals(tokenProgram()) && accountInfo.data().length == TokenAccount.BYTES) {
      return true;
    } else {
      return programOwner.equals(token2022Program());
    }
  }

  @Override
  public PublicKey glamMintProgram() {
    return glamAccounts.mintProgram();
  }

  @Override
  public long medianMillisPerSlot() {
    return epochInfoService.epochInfo().medianMillisPerSlot();
  }

  @Override
  public void backoff(final long failureCount) throws InterruptedException {
    NANOSECONDS.sleep(Math.max(minCheckStateDelayNanos, backoff.delay(failureCount, NANOSECONDS)));
  }

  @Override
  public boolean processInstructions(final String logContext,
                                     final List<Instruction> instructions) throws InterruptedException {
    return instructionProcessor.processInstructions(logContext, instructions, transactionFactory);
  }

  // TODO: Move to dedicated service
//  private boolean feePayerBalanceLow() throws InterruptedException {
//    final var feePayerAccountInfo = accountsNeededMap.get(serviceKey);
//    final var feePayerBalance = feePayerAccountInfo.amount();
//    if (feePayerBalance.compareTo(minFeePayerBalance) < 0) {
//      notifyLowBalance(feePayerBalance, notifyLowBalance);
//      if (notifyLowBalance) {
//        notifyLowBalance = false;
//      }
//      return true;
//    } else if (feePayerBalance.compareTo(warnFeePayerBalance) < 0) {
//      notifyLowBalance(feePayerBalance, notifyLowBalance);
//      if (notifyLowBalance) {
//        notifyLowBalance = false;
//      }
//    } else if (!notifyLowBalance) {
//      notifyLowBalance = true;
//    }
//    return false;
//  }
//
//  private void notifyLowBalance(final BigInteger lamports, final boolean notify) {
//    final var sol = LamportDecimal.toBigDecimal(lamports).stripTrailingZeros();
//    final var msg = String.format("""
//            {
//             "event": "Low Fee Payer Balance",
//             "key": "%s",
//             "balance": "%s"
//            }
//            """,
//        serviceKey.toBase58(),
//        sol.toPlainString()
//    );
//    logger.log(WARNING, msg);
//    if (notify) {
//      for (final var stringCompletableFuture : notifyClient.postMsg(msg)) {
//        logger.log(INFO, stringCompletableFuture.join());
//      }
//    }
//  }

  @Override
  public boolean feePayerBalanceLow() {
    return false;
  }

  @Override
  public PublicKey serviceKey() {
    return serviceKey;
  }

  @Override
  public long minCheckStateDelayNanos() {
    return minCheckStateDelayNanos;
  }

  @Override
  public long maxCheckStateDelayNanos() {
    return maxCheckStateDelayNanos;
  }

  @Override
  public SolanaAccounts solanaAccounts() {
    return solanaAccounts;
  }

  @Override
  public GlamAccounts glamAccounts() {
    return glamAccounts;
  }

  @Override
  public NotifyClient notifyClient() {
    return notifyClient;
  }

  @Override
  public RpcCaller rpcCaller() {
    return rpcCaller;
  }

  @Override
  public String toString() {
    return "ServiceContext[" +
        "serviceKey=" + serviceKey + ", " +
        "warnFeePayerBalance=" + warnFeePayerBalance + ", " +
        "minFeePayerBalance=" + minFeePayerBalance + ", " +
        "minCheckStateDelayNanos=" + minCheckStateDelayNanos + ", " +
        "maxCheckStateDelayNanos=" + maxCheckStateDelayNanos + ", " +
        "backoff=" + backoff + ", " +
        "epochInfoService=" + epochInfoService + ", " +
        "solanaAccounts=" + solanaAccounts + ", " +
        "glamAccounts=" + glamAccounts + ", " +
        "notifyClient=" + notifyClient + ", " +
        "rpcCaller=" + rpcCaller + ", " +
        "instructionProcessor=" + instructionProcessor + ", " +
        "transactionFactory=" + transactionFactory + ']';
  }
}
