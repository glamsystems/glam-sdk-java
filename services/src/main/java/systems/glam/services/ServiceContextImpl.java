package systems.glam.services;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.accounts.sysvar.Clock;
import software.sava.core.accounts.token.Mint;
import software.sava.core.accounts.token.TokenAccount;
import software.sava.core.accounts.token.extensions.AccountType;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.services.core.net.http.NotifyClient;
import software.sava.services.core.remote.call.Backoff;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.sdk.GlamAccounts;
import systems.glam.services.db.sql.SqlDataSource;
import systems.glam.services.io.FileUtils;

import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

public final class ServiceContextImpl implements ServiceContext {

  private final PublicKey serviceKey;
  private final BigInteger warnFeePayerBalance;
  private final BigInteger minFeePayerBalance;
  private final Path cacheDirectory;
  private final Path accountsCacheDirectory;
  private final Path glamMinStateAccountCacheDirectory;
  private final long minCheckStateDelayNanos;
  private final long maxCheckStateDelayNanos;
  private final ExecutorService taskExecutor;
  private final Backoff backoff;
  private final SolanaAccounts solanaAccounts;
  private final GlamAccounts glamAccounts;
  private final NotifyClient notifyClient;
  private final RpcCaller rpcCaller;
  private final SqlDataSource primaryDatasource;

  public ServiceContextImpl(final PublicKey serviceKey,
                            final BigInteger warnFeePayerBalance, final BigInteger minFeePayerBalance,
                            final Path cacheDirectory,
                            final Duration minCheckStateDelay, final Duration maxCheckStateDelay,
                            final ExecutorService taskExecutor,
                            final Backoff backoff,
                            final SolanaAccounts solanaAccounts,
                            final GlamAccounts glamAccounts,
                            final NotifyClient notifyClient,
                            final RpcCaller rpcCaller,
                            final SqlDataSource primaryDatasource) {
    this.serviceKey = serviceKey;
    this.warnFeePayerBalance = warnFeePayerBalance;
    this.minFeePayerBalance = minFeePayerBalance;
    this.cacheDirectory = cacheDirectory;
    this.accountsCacheDirectory = cacheDirectory.resolve("accounts");
    this.primaryDatasource = primaryDatasource;
    this.glamMinStateAccountCacheDirectory = accountsCacheDirectory.resolve("glam/min_state");
    this.minCheckStateDelayNanos = minCheckStateDelay.toNanos();
    this.maxCheckStateDelayNanos = maxCheckStateDelay.toNanos();
    this.taskExecutor = taskExecutor;
    this.backoff = backoff;
    this.solanaAccounts = solanaAccounts;
    this.glamAccounts = glamAccounts;
    this.notifyClient = notifyClient;
    this.rpcCaller = rpcCaller;
  }

  @Override
  public Path accountsCacheDirectory() {
    return accountsCacheDirectory;
  }

  @Override
  public PublicKey clockSysVar() {
    return solanaAccounts.clockSysVar();
  }

  @Override
  public AccountMeta readClockSysVar() {
    return solanaAccounts.readClockSysVar();
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
  public boolean isTokenMint(final AccountInfo<byte[]> accountInfo) {
    final var programOwner = accountInfo.owner();
    final byte[] data = accountInfo.data();
    if (programOwner.equals(tokenProgram())) {
      return data.length == Mint.BYTES;
    } else if (programOwner.equals(token2022Program())) {
      if (data.length > TokenAccount.BYTES) {
        final int accountType = data[TokenAccount.BYTES] & 0xFF;
        return accountType == AccountType.Mint.ordinal();
      } else {
        return data.length == Mint.BYTES;
      }
    } else {
      return false;
    }
  }

  @Override
  public boolean isTokenAccount(final AccountInfo<byte[]> accountInfo) {
    final var programOwner = accountInfo.owner();
    final byte[] data = accountInfo.data();
    if (programOwner.equals(tokenProgram())) {
      return data.length == TokenAccount.BYTES;
    } else if (programOwner.equals(token2022Program())) {
      if (data.length > TokenAccount.BYTES) {
        final int accountType = data[TokenAccount.BYTES] & 0xFF;
        return accountType == AccountType.Account.ordinal();
      } else {
        return data.length == TokenAccount.BYTES;
      }
    } else {
      return false;
    }
  }

  @Override
  public PublicKey glamMintProgram() {
    return glamAccounts.mintProgram();
  }

  @Override
  public void executeTask(final Runnable task) {
    taskExecutor.execute(task);
  }

  @Override
  public void backoff(final long failureCount) throws InterruptedException {
    NANOSECONDS.sleep(Math.max(minCheckStateDelayNanos, backoff.delay(failureCount, NANOSECONDS)));
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
  public SqlDataSource primaryDatasource() {
    return primaryDatasource;
  }

  @Override
  public Path cacheDirectory() {
    return cacheDirectory;
  }

  @Override
  public Path resolveGlamStateFilePath(final PublicKey glamStateKey) {
    return FileUtils.resolveAccountPath(accountsCacheDirectory, glamStateKey);
  }

  @Override
  public Path glamMinStateAccountCacheDirectory() {
    return glamMinStateAccountCacheDirectory;
  }

  @Override
  public Clock clock(final Map<PublicKey, AccountInfo<byte[]>> accountsNeededMap) {
    final var clockSysVar = clockSysVar();
    final var clockAccount = accountsNeededMap.get(clockSysVar);
    return Clock.read(clockSysVar, clockAccount.data());
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
        "solanaAccounts=" + solanaAccounts + ", " +
        "glamAccounts=" + glamAccounts + ", " +
        "glamMinStateAccountCacheDirectory=" + glamMinStateAccountCacheDirectory;
  }
}
