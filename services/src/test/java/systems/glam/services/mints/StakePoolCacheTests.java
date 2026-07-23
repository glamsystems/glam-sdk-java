package systems.glam.services.mints;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.marinade.stake_pool.MarinadeAccounts;
import software.sava.idl.clients.spl.stakepool.StakePoolAccounts;
import software.sava.idl.clients.spl.stakepool.StakePoolState;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.Context;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.services.io.FileUtils;
import systems.glam.services.io.KeyedFlatFile;
import systems.glam.services.tests.LogCapture;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

final class StakePoolCacheTests {

  private static final StakePoolAccounts POOLS = StakePoolAccounts.MAIN_NET;
  private static final MarinadeAccounts MARINADE = MarinadeAccounts.MAIN_NET;

  @Test
  void rejectsAFetchDelayThatWouldNotSleep() {
    // the run loop sleeps for this delay between passes, so anything under a
    // millisecond rounds to no sleep and spins a core
    for (final var tooSmall : new Duration[]{Duration.ZERO, Duration.ofNanos(999_999), Duration.ofMillis(-1)}) {
      final var ex = assertThrows(
          IllegalArgumentException.class,
          () -> new StakePoolCacheImpl(tooSmall, null, List.of(), Map.of(), Map.of())
      );
      assertTrue(ex.getMessage().contains("at least one millisecond"), ex.getMessage());
    }
    // exactly the floor is accepted
    assertNotNull(new StakePoolCacheImpl(Duration.ofMillis(1), null, List.of(), Map.of(), Map.of()));
  }

  private static PublicKey key(final int id) {
    final byte[] bytes = new byte[PublicKey.PUBLIC_KEY_LENGTH];
    bytes[0] = (byte) id;
    bytes[31] = 7;
    return PublicKey.createPubKey(bytes);
  }

  private static AccountInfo<byte[]> poolAccount(final PublicKey program,
                                                 final PublicKey stateKey,
                                                 final PublicKey mintKey,
                                                 final int length) {
    final byte[] data = new byte[length];
    if (length > StakePoolState.POOL_MINT_OFFSET + PublicKey.PUBLIC_KEY_LENGTH) {
      mintKey.write(data, StakePoolState.POOL_MINT_OFFSET);
    }
    return new AccountInfo<>(stateKey, new Context(1L, null), false, 0, program, BigInteger.ZERO, 0, data);
  }

  private static AccountInfo<byte[]> poolAccount(final PublicKey program, final PublicKey stateKey, final PublicKey mintKey) {
    return poolAccount(program, stateKey, mintKey, StakePoolState.NEXT_EPOCH_FEE_OFFSET + 64);
  }

  private static final class NoopTracker extends software.sava.services.core.request_capacity.trackers.RootErrorTracker<software.sava.rpc.json.http.client.SolanaRpcClient, byte[]> {

    NoopTracker(final software.sava.services.core.request_capacity.CapacityState capacityState) {
      super(capacityState);
    }

    @Override
    protected boolean isServerError(final software.sava.rpc.json.http.client.SolanaRpcClient response) {
      return false;
    }

    @Override
    protected boolean isRequestError(final software.sava.rpc.json.http.client.SolanaRpcClient response) {
      return false;
    }

    @Override
    protected boolean isRateLimited(final software.sava.rpc.json.http.client.SolanaRpcClient response) {
      return false;
    }

    @Override
    protected boolean updateGroupedErrorResponseCount(final long now,
                                                      final software.sava.rpc.json.http.client.SolanaRpcClient response,
                                                      final byte[] body) {
      return false;
    }

    @Override
    protected void logResponse(final software.sava.rpc.json.http.client.SolanaRpcClient response, final byte[] body) {
    }
  }

  private static RpcCaller rpcCaller(final Function<PublicKey, List<AccountInfo<byte[]>>> accountsByProgram) {
    final var client = (software.sava.rpc.json.http.client.SolanaRpcClient) java.lang.reflect.Proxy.newProxyInstance(
        software.sava.rpc.json.http.client.SolanaRpcClient.class.getClassLoader(),
        new Class<?>[]{software.sava.rpc.json.http.client.SolanaRpcClient.class},
        (proxy, method, args) -> {
          if (method.getName().equals("getProgramAccounts")) {
            return java.util.concurrent.CompletableFuture.completedFuture(accountsByProgram.apply((PublicKey) args[0]));
          }
          throw new UnsupportedOperationException(method.getName());
        }
    );
    final var resetDuration = Duration.ofSeconds(1);
    final var config = new software.sava.services.core.request_capacity.CapacityConfig(
        0, 1_000, resetDuration, 8, resetDuration, resetDuration, resetDuration, resetDuration);
    final var monitor = config.createMonitor("test", NoopTracker::new);
    final var item = software.sava.services.core.remote.load_balance.BalancedItem.createItem(
        client, monitor, software.sava.services.core.remote.call.Backoff.single(java.util.concurrent.TimeUnit.MILLISECONDS, 0));
    return new RpcCaller(
        Executors.newVirtualThreadPerTaskExecutor(),
        software.sava.services.core.remote.load_balance.LoadBalancer.createBalancer(item),
        software.sava.services.solana.remote.call.CallWeights.createDefault()
    );
  }

  private static StakePoolCache initCache(final Path tempDir, final RpcCaller rpcCaller) {
    return StakePoolCache.initCache(
        Executors.newVirtualThreadPerTaskExecutor(),
        tempDir.resolve("pools"),
        POOLS,
        MARINADE,
        Duration.ofMillis(30),
        rpcCaller
    ).join();
  }

  @Test
  void aColdStartFetchesParsesSkipsShortAccountsAndPersists(@TempDir final Path tempDir) throws Exception {
    final var multi = POOLS.stakePoolProgram();
    final var sanctumMulti = POOLS.sanctumMultiValidatorStakePoolProgram();
    final var state1 = key(1);
    final var mint1 = key(2);
    final var state2 = key(4);
    final var mint2 = key(5);

    final var exactState = key(6);
    final var exactMint = key(7);
    try (final var cache = initCache(tempDir, rpcCaller(program -> {
      if (program.equals(multi)) {
        // the short account is parsed around, not over; exactly the minimum is kept
        return List.of(
            poolAccount(multi, state1, mint1),
            poolAccount(multi, key(3), key(9), StakePoolState.NEXT_EPOCH_FEE_OFFSET - 1),
            poolAccount(multi, exactState, exactMint, StakePoolState.NEXT_EPOCH_FEE_OFFSET)
        );
      }
      return program.equals(sanctumMulti) ? List.of(poolAccount(sanctumMulti, state2, mint2)) : List.of();
    }))) {
      assertNotNull(cache.get(exactMint), "an account at exactly the minimum length is valid");
      final var context1 = cache.get(mint1);
      assertNotNull(context1);
      assertEquals(multi, context1.program());
      assertEquals(state1, context1.stateKey());
      assertEquals(mint1, context1.mintKey());

      final var context2 = cache.get(mint2);
      assertNotNull(context2);
      assertEquals(sanctumMulti, context2.program());

      // marinade is always seeded
      final var marinade = cache.get(MARINADE.mSolTokenMint());
      assertNotNull(marinade);
      assertEquals(MARINADE.marinadeProgram(), marinade.program());
      assertEquals(MARINADE.stateAccount(), marinade.stateKey());

      assertNull(cache.get(key(9)), "a short account must not be indexed");

      // one entry persisted for the multi program: the short account was dropped
      final byte[] persisted = Files.readAllBytes(
          FileUtils.resolveAccountPath(tempDir.resolve("pools"), multi)
      );
      assertEquals(StakePoolContext.BYTES * 2, persisted.length);
      assertEquals(state1, PublicKey.readPubKey(persisted, 0));
      assertEquals(mint1, PublicKey.readPubKey(persisted, PublicKey.PUBLIC_KEY_LENGTH));
      assertEquals(exactState, PublicKey.readPubKey(persisted, StakePoolContext.BYTES));

      // nothing fetched for the single-validator sanctum program: its file stays empty
      assertEquals(0, Files.size(
          FileUtils.resolveAccountPath(tempDir.resolve("pools"), POOLS.sanctumSingleValidatorStakePoolProgram())
      ));
    }
  }

  @Test
  void aWarmStartReadsTheFlatFilesWithoutFetching(@TempDir final Path tempDir) throws Exception {
    final var multi = POOLS.stakePoolProgram();
    final var state1 = key(11);
    final var mint1 = key(12);
    final var state2 = key(13);
    final var mint2 = key(14);

    // the first run persists; the second must load that program from disk
    try (final var first = initCache(tempDir, rpcCaller(program ->
        program.equals(multi)
            ? List.of(poolAccount(multi, state1, mint1), poolAccount(multi, state2, mint2))
            : List.of()))) {
      assertNotNull(first.get(mint1));
    }

    final var fetched = new ArrayList<PublicKey>();
    try (final var cache = initCache(tempDir, rpcCaller(program -> {
      fetched.add(program);
      return List.of();
    }))) {
      assertFalse(fetched.contains(multi), "the persisted program must load from disk");
      final var context1 = cache.get(mint1);
      assertNotNull(context1);
      assertEquals(multi, context1.program());
      assertEquals(state1, context1.stateKey());
      final var context2 = cache.get(mint2);
      assertNotNull(context2);
      assertEquals(state2, context2.stateKey());
    }
  }

  @Test
  void acceptGatesOnOwnerLengthAndNovelty(@TempDir final Path tempDir) throws Exception {
    final var multi = POOLS.stakePoolProgram();
    try (final var cache = initCache(tempDir, rpcCaller(program -> List.of()))) {
      final var impl = (StakePoolCacheImpl) cache;
      final var filePath = FileUtils.resolveAccountPath(tempDir.resolve("pools"), multi);

      // a foreign owner is ignored
      impl.accept(poolAccount(key(99), key(21), key(22)));
      assertNull(cache.get(key(22)));

      // short data is ignored
      impl.accept(poolAccount(multi, key(23), key(24), StakePoolState.NEXT_EPOCH_FEE_OFFSET - 1));
      assertNull(cache.get(key(24)));

      // a new pool is indexed and appended
      impl.accept(poolAccount(multi, key(25), key(26)));
      final var context = cache.get(key(26));
      assertNotNull(context);
      assertEquals(key(25), context.stateKey());
      assertEquals(StakePoolContext.BYTES, context.l());
      assertEquals(StakePoolContext.BYTES, Files.size(filePath));

      // the same mint again is not re-appended, even from another state account
      impl.accept(poolAccount(multi, key(27), key(26)));
      assertSame(context, cache.get(key(26)));
      assertEquals(StakePoolContext.BYTES, Files.size(filePath));

      // exactly the minimum length is accepted
      impl.accept(poolAccount(multi, key(28), key(29), StakePoolState.NEXT_EPOCH_FEE_OFFSET));
      assertNotNull(cache.get(key(29)));
    }
  }

  @Test
  void aClosedCacheRejectsNewPools(@TempDir final Path tempDir) {
    final var multi = POOLS.stakePoolProgram();
    final var cache = initCache(tempDir, rpcCaller(program -> List.of()));
    cache.close();
    final var impl = (StakePoolCacheImpl) cache;
    assertThrows(RuntimeException.class, () -> impl.accept(poolAccount(multi, key(41), key(42))),
        "an append to a closed flat file must fail loudly");
  }

  @Test
  void theRunLoopPollsEveryProgramOnTheDelay(@TempDir final Path tempDir) throws Exception {
    final var multi = POOLS.stakePoolProgram();
    final var polled = new ConcurrentHashMap<PublicKey, Integer>();
    final var mint = key(32);
    final var running = new java.util.concurrent.atomic.AtomicBoolean(false);
    try (final var cache = initCache(tempDir, rpcCaller(program -> {
      polled.merge(program, 1, Integer::sum);
      // the pool only exists once the run loop is polling: finding it proves
      // the loop routes results through accept, not that init already did
      return running.get() && program.equals(multi) ? List.of(poolAccount(multi, key(31), mint)) : List.of();
    }))) {
      polled.clear();
      assertNull(cache.get(mint));
      running.set(true);
      final var runner = new Thread(cache::run);
      runner.start();
      final long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
      while (polled.size() < 3 || polled.values().stream().anyMatch(count -> count < 2)) {
        assertTrue(System.nanoTime() < deadline, () -> "programs polled: " + polled);
        //noinspection BusyWait
        Thread.sleep(1L);
      }
      // the poll results run through accept: the new pool is indexed
      assertNotNull(cache.get(mint));
      // the sleep paces the loop: watch a window, not an instant — without the
      // sleep these counts would grow by hundreds here
      final var counted = Map.copyOf(polled);
      Thread.sleep(150L);
      assertTrue(polled.values().stream().allMatch(count -> count < 40),
          () -> "the poll loop is spinning: " + polled + " after " + counted);

      runner.interrupt();
      runner.join(5_000L);
      assertFalse(runner.isAlive());
    }
  }

  @Test
  void aPollFailureIsLoggedAndEndsTheLoop(@TempDir final Path tempDir) {
    final var flatFile = KeyedFlatFile.<StakePoolContext>createFlatFile(
        StakePoolContext.BYTES,
        FileUtils.resolveAccountPath(tempDir, POOLS.stakePoolProgram())
    );
    try (flatFile) {
      final var failing = new StakePoolCacheImpl(
          Duration.ofMillis(10),
          rpcCaller(program -> {
            throw new IllegalStateException("rpc down");
          }),
          List.of(),
          Map.of(POOLS.stakePoolProgram(), flatFile),
          new ConcurrentHashMap<>()
      );
      try (final var logs = LogCapture.attach(StakePoolCache.class.getName())) {
        failing.run();
        logs.assertLogged("Unexpected error fetching stake pool accounts.");
      }
    }
  }
}
