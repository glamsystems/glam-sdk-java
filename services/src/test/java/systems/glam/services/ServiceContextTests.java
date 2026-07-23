package systems.glam.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.token.Mint;
import software.sava.core.accounts.token.TokenAccount;
import software.sava.core.encoding.ByteUtil;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.Context;
import software.sava.services.core.remote.call.Backoff;
import systems.glam.sdk.GlamAccounts;
import systems.glam.sdk.GlamEnv;

import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;

final class ServiceContextTests {

  private static final PublicKey SERVICE_KEY = fromBase58Encoded("F1oQY1jbdiJyxxeeuMBF2NsUckboyWo6TSXNqzJbrhxs");
  private static final SolanaAccounts SOLANA = SolanaAccounts.MAIN_NET;

  private static ServiceContextImpl context(final Path cacheDirectory,
                                            final Duration minCheckDelay,
                                            final java.util.concurrent.ExecutorService executor,
                                            final Backoff backoff) {
    return new ServiceContextImpl(
        SERVICE_KEY,
        BigInteger.valueOf(50_000_000L), BigInteger.valueOf(10_000_000L),
        cacheDirectory,
        minCheckDelay, Duration.ofMinutes(5),
        executor,
        backoff,
        SOLANA,
        GlamAccounts.MAIN_NET, GlamAccounts.MAIN_NET_STAGING,
        null, null, null
    );
  }

  private static AccountInfo<byte[]> account(final PublicKey owner, final byte[] data) {
    return new AccountInfo<>(
        SERVICE_KEY, new Context(1L, null), false, 0, owner, BigInteger.ZERO, 0, data
    );
  }

  @Test
  void tokenMintsAndAccountsAreTypedByOwnerLengthAndExtension(@TempDir final Path tempDir) {
    final var context = context(tempDir, Duration.ofSeconds(15), null, null);
    final var tokenProgram = SOLANA.tokenProgram();
    final var token2022 = SOLANA.token2022Program();

    // classic token program: length is the whole answer
    assertTrue(context.isTokenMint(account(tokenProgram, new byte[Mint.BYTES])));
    assertFalse(context.isTokenMint(account(tokenProgram, new byte[TokenAccount.BYTES])));
    assertTrue(context.isTokenAccount(account(tokenProgram, new byte[TokenAccount.BYTES])));
    assertFalse(context.isTokenAccount(account(tokenProgram, new byte[Mint.BYTES])));

    // 2022 without extensions falls back to lengths too
    assertTrue(context.isTokenMint(account(token2022, new byte[Mint.BYTES])));
    assertTrue(context.isTokenAccount(account(token2022, new byte[TokenAccount.BYTES])));
    assertFalse(context.isTokenMint(account(token2022, new byte[TokenAccount.BYTES])));
    assertFalse(context.isTokenAccount(account(token2022, new byte[Mint.BYTES])));

    // 2022 with extensions: the account-type byte after the base account decides
    final byte[] extendedMint = new byte[TokenAccount.BYTES + 8];
    extendedMint[TokenAccount.BYTES] = 1; // AccountType.Mint
    assertTrue(context.isTokenMint(account(token2022, extendedMint)));
    assertFalse(context.isTokenAccount(account(token2022, extendedMint)));
    final byte[] extendedAccount = new byte[TokenAccount.BYTES + 8];
    extendedAccount[TokenAccount.BYTES] = 2; // AccountType.Account
    assertTrue(context.isTokenAccount(account(token2022, extendedAccount)));
    assertFalse(context.isTokenMint(account(token2022, extendedAccount)));

    // any other owner is neither
    assertFalse(context.isTokenMint(account(SOLANA.systemProgram(), new byte[Mint.BYTES])));
    assertFalse(context.isTokenAccount(account(SOLANA.systemProgram(), new byte[TokenAccount.BYTES])));
  }

  @Test
  void theClockIsParsedFromTheNeededAccounts(@TempDir final Path tempDir) {
    final var context = context(tempDir, Duration.ofSeconds(15), null, null);
    final byte[] clockData = new byte[40];
    ByteUtil.putInt64LE(clockData, 0, 4_242L);
    ByteUtil.putInt64LE(clockData, 8, 100L);
    ByteUtil.putInt64LE(clockData, 16, 512L);
    ByteUtil.putInt64LE(clockData, 24, 513L);
    ByteUtil.putInt64LE(clockData, 32, 1_650_000_000L);

    final var clock = context.clock(Map.of(SOLANA.clockSysVar(), account(SOLANA.clockSysVar(), clockData)));
    assertEquals(4_242L, clock.slot());
    assertEquals(512L, clock.epoch());
    assertEquals(1_650_000_000L, clock.unixTimestamp());
    assertEquals(SOLANA.clockSysVar(), clock.address());
  }

  @Test
  void backoffSleepsAtLeastTheMinimumCheckDelay(@TempDir final Path tempDir) throws InterruptedException {
    // a backoff shorter than the floor: the floor wins
    final var context = context(tempDir, Duration.ofMillis(60), null, Backoff.single(TimeUnit.MILLISECONDS, 1));
    final long start = System.nanoTime();
    context.backoff(1L);
    final long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;
    assertTrue(elapsedMillis >= 45, () -> "slept only " + elapsedMillis + "ms");

    // a backoff longer than the floor: the backoff wins
    final var slowBackoff = context(tempDir, Duration.ofMillis(1), null, Backoff.single(TimeUnit.MILLISECONDS, 60));
    final long slowStart = System.nanoTime();
    slowBackoff.backoff(1L);
    final long slowElapsed = (System.nanoTime() - slowStart) / 1_000_000L;
    assertTrue(slowElapsed >= 45, () -> "slept only " + slowElapsed + "ms");
  }

  @Test
  void pathsExecutionAndAccessorsRoundTrip(@TempDir final Path tempDir) {
    final var backoff = Backoff.single(TimeUnit.MILLISECONDS, 1);
    final var context = context(tempDir, Duration.ofSeconds(15), null, backoff);

    // the cache layout: accounts under the cache root, glam state beneath it
    assertEquals(tempDir, context.cacheDirectory());
    assertEquals(tempDir.resolve("accounts"), context.accountsCacheDirectory());
    assertEquals(tempDir.resolve("accounts").resolve("glam/state"), context.glamStateAccountCacheDirectory());
    final var stateKey = fromBase58Encoded("3H7XbyVaYusyzQCncfRSBx3zgvfmjGG7wrr3ARtXF1o7");
    final var statePath = context.resolveGlamStateFilePath(GlamEnv.PRODUCTION, stateKey);
    assertTrue(statePath.startsWith(tempDir.resolve("accounts").resolve("glam/state").resolve(GlamEnv.PRODUCTION.name())),
        statePath::toString);
    assertTrue(statePath.getFileName().toString().startsWith(stateKey.toBase58()), statePath::toString);
    assertEquals(
        tempDir.resolve("accounts").resolve("glam/global/")
            .resolve(GlamAccounts.MAIN_NET.globalConfigPDA().publicKey().toBase58() + ".dat.gz"),
        context.globalConfigCacheFile()
    );

    assertEquals(SERVICE_KEY, context.serviceKey());
    assertEquals(Duration.ofSeconds(15).toNanos(), context.minCheckStateDelayNanos());
    assertEquals(Duration.ofMinutes(5).toNanos(), context.maxCheckStateDelayNanos());
    assertSame(SOLANA, context.solanaAccounts());
    assertSame(GlamAccounts.MAIN_NET, context.glamAccounts());
    assertSame(GlamAccounts.MAIN_NET_STAGING, context.glamStagingAccounts());
    assertEquals(SOLANA.clockSysVar(), context.clockSysVar());
    assertEquals(SOLANA.clockSysVar(), context.readClockSysVar().publicKey());
    assertEquals(SOLANA.tokenProgram(), context.tokenProgram());
    assertEquals(SOLANA.token2022Program(), context.token2022Program());
    assertEquals(GlamAccounts.MAIN_NET.mintProgram(), context.glamMintProgram());
    assertFalse(context.feePayerBalanceLow());
    assertTrue(context.toString().contains(SERVICE_KEY.toBase58()));

    final software.sava.services.core.net.http.NotifyClient notifyClient = msg -> java.util.List.of();
    final var rpcCaller = new software.sava.services.solana.remote.call.RpcCaller(null, null, null);
    final var datasource = (javax.sql.DataSource) java.lang.reflect.Proxy.newProxyInstance(
        javax.sql.DataSource.class.getClassLoader(),
        new Class<?>[]{javax.sql.DataSource.class},
        (proxy, method, args) -> {
          throw new UnsupportedOperationException(method.getName());
        }
    );
    final var withExecutor = new ServiceContextImpl(
        SERVICE_KEY, BigInteger.ONE, BigInteger.ONE, tempDir,
        Duration.ofSeconds(1), Duration.ofSeconds(2),
        java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor(),
        backoff, SOLANA, GlamAccounts.MAIN_NET, GlamAccounts.MAIN_NET_STAGING,
        notifyClient, rpcCaller, datasource
    );
    assertSame(notifyClient, withExecutor.notifyClient());
    assertSame(rpcCaller, withExecutor.rpcCaller());
    assertSame(datasource, withExecutor.primaryDatasource());
    assertNotNull(withExecutor.taskExecutor());
    final var ran = new java.util.concurrent.CountDownLatch(1);
    withExecutor.executeTask(ran::countDown);
    assertDoesNotThrow(() -> assertTrue(ran.await(5, TimeUnit.SECONDS)));
  }
}
