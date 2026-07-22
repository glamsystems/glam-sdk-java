package systems.glam.services.rpc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.encoding.ByteUtil;
import software.sava.rpc.json.http.client.SolanaRpcClient;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.Context;
import software.sava.services.core.NanoClock;
import software.sava.services.core.remote.call.Backoff;
import software.sava.services.core.remote.load_balance.BalancedItem;
import software.sava.services.core.remote.load_balance.LoadBalancer;
import software.sava.services.core.request_capacity.CapacityConfig;
import software.sava.services.core.request_capacity.CapacityState;
import software.sava.services.core.request_capacity.trackers.RootErrorTracker;
import software.sava.services.solana.remote.call.CallWeights;
import software.sava.services.solana.remote.call.RpcCaller;

import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.*;

/// Drives AccountFetcherImpl.run() deterministically on the test thread: a
/// Proxy-backed SolanaRpcClient serves canned batches (no request leaves the
/// JVM), the fetch delay is zero so sleeps are yields, and the fake interrupts
/// the thread on its final batch so run() exits through its InterruptedException
/// path instead of looping.
final class AccountFetcherTests {

  private static final class TestClock implements NanoClock {

    private long nanos = 2_718_281_828L;

    @Override
    public long nanoTime() {
      return nanos;
    }

    @Override
    public void sleep(final long millis) {
      nanos += millis * 1_000_000L;
    }
  }

  private static final class NoopTracker extends RootErrorTracker<SolanaRpcClient, byte[]> {

    NoopTracker(final CapacityState capacityState) {
      super(capacityState);
    }

    @Override
    protected boolean isServerError(final SolanaRpcClient response) {
      return false;
    }

    @Override
    protected boolean isRequestError(final SolanaRpcClient response) {
      return false;
    }

    @Override
    protected boolean isRateLimited(final SolanaRpcClient response) {
      return false;
    }

    @Override
    protected boolean updateGroupedErrorResponseCount(final long now,
                                                      final SolanaRpcClient response,
                                                      final byte[] body) {
      return false;
    }

    @Override
    protected void logResponse(final SolanaRpcClient response, final byte[] body) {
    }
  }

  /// Records each getAccounts key list, serves accounts from a fixed universe,
  /// and interrupts the running thread on batch number `interruptOnCall`.
  private static final class RecordingRpc {

    final List<List<PublicKey>> calls = new ArrayList<>();
    final Map<PublicKey, AccountInfo<byte[]>> universe = new HashMap<>();
    int interruptOnCall = 1;

    SolanaRpcClient client() {
      return (SolanaRpcClient) Proxy.newProxyInstance(
          SolanaRpcClient.class.getClassLoader(),
          new Class<?>[]{SolanaRpcClient.class},
          (proxy, method, args) -> {
            if (method.getName().equals("getAccounts")) {
              @SuppressWarnings("unchecked") final var keys = (List<PublicKey>) args[0];
              calls.add(List.copyOf(keys));
              if (calls.size() >= interruptOnCall) {
                Thread.currentThread().interrupt();
              }
              final var accounts = new ArrayList<AccountInfo<byte[]>>(keys.size());
              for (final var key : keys) {
                accounts.add(universe.get(key));
              }
              return java.util.concurrent.CompletableFuture.completedFuture(accounts);
            }
            throw new UnsupportedOperationException(method.getName());
          }
      );
    }
  }

  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
    // clear the flag a test may have left set so later tests are unaffected
    Thread.interrupted();
  }

  private RpcCaller createCaller(final RecordingRpc rpc) {
    final var resetDuration = Duration.ofSeconds(1);
    final var config = new CapacityConfig(
        0, 1_000, resetDuration, 8, resetDuration, resetDuration, resetDuration, resetDuration);
    final var monitor = config.createMonitor("test", NoopTracker::new, new TestClock());
    final var item = BalancedItem.createItem(rpc.client(), monitor, Backoff.single(MILLISECONDS, 0));
    return new RpcCaller(executor, LoadBalancer.createBalancer(item), CallWeights.createDefault());
  }

  private static PublicKey key(final int id) {
    final byte[] bytes = new byte[PublicKey.PUBLIC_KEY_LENGTH];
    bytes[0] = (byte) (id >> 8);
    bytes[1] = (byte) id;
    return PublicKey.createPubKey(bytes);
  }

  private static AccountInfo<byte[]> account(final PublicKey pubKey, final long slot, final byte[] data) {
    return new AccountInfo<>(
        pubKey, new Context(slot, null), false, 0, SolanaAccounts.MAIN_NET.systemProgram(),
        BigInteger.ZERO, 0, data
    );
  }

  private static final class RecordingConsumer implements AccountConsumer {

    final List<Map<PublicKey, AccountInfo<byte[]>>> received = new ArrayList<>();
    boolean exceeded;

    @Override
    public void accept(final List<AccountInfo<byte[]>> accounts, final Map<PublicKey, AccountInfo<byte[]>> accountMap) {
      received.add(accountMap);
    }

    @Override
    public void mutableKeysExceededMaxSize() {
      exceeded = true;
    }
  }

  private AccountFetcher createFetcher(final RecordingRpc rpc, final Set<PublicKey> alwaysFetch) {
    return AccountFetcher.createFetcher(Duration.ZERO, false, createCaller(rpc), alwaysFetch);
  }

  @Test
  void deliversFetchedAndNullAccountsAndReadsTheClockSysVar() {
    final var rpc = new RecordingRpc();
    final var clockKey = SolanaAccounts.MAIN_NET.clockSysVar();
    final byte[] clockData = new byte[40];
    ByteUtil.putInt64LE(clockData, 32, 1_650_000_000L);
    rpc.universe.put(clockKey, account(clockKey, 4_242L, clockData));
    final var present = key(1);
    rpc.universe.put(present, account(present, 4_242L, new byte[]{5}));
    final var absent = key(2);

    final var fetcher = createFetcher(rpc, Set.of(clockKey));
    final var consumer = new RecordingConsumer();
    fetcher.queue(List.of(present, absent), consumer);
    fetcher.run();

    assertEquals(1, rpc.calls.size());
    // the batch carries the always-fetch keys alongside the queued ones
    assertTrue(rpc.calls.getFirst().containsAll(List.of(clockKey, present, absent)));

    assertEquals(1, consumer.received.size());
    final var accountMap = consumer.received.getFirst();
    assertArrayEquals(new byte[]{5}, accountMap.get(present).data());
    // a missing account maps to the NULL sentinel, not to an absent entry
    assertSame(AccountFetcher.NULL_ACCOUNT_INFO, accountMap.get(absent));
    assertTrue(AccountFetcher.isNull(accountMap.get(absent)));
    assertFalse(AccountFetcher.isNull(accountMap.get(present)));

    // slot and wall-clock time come from the clock sysvar when present
    final var recentSlot = fetcher.recentSlot();
    assertEquals(4_242L, recentSlot.slot());
    assertEquals(Instant.ofEpochSecond(1_650_000_000L), recentSlot.timestamp());
  }

  @Test
  void recentSlotFallsBackToAccountContexts() {
    final var rpc = new RecordingRpc();
    final var present = key(1);
    rpc.universe.put(present, account(present, 7_777L, new byte[]{1}));

    final var fetcher = createFetcher(rpc, Set.of());
    final var consumer = new RecordingConsumer();
    fetcher.queue(List.of(present), consumer);
    final var before = Instant.now();
    fetcher.run();
    final var after = Instant.now();

    final var recentSlot = fetcher.recentSlot();
    assertEquals(7_777L, recentSlot.slot());
    assertFalse(recentSlot.timestamp().isBefore(before.minusMillis(1)));
    assertFalse(recentSlot.timestamp().isAfter(after.plusMillis(1)));
  }

  @Test
  void batchableRequestsChunkAtTheRpcLimit() {
    final var rpc = new RecordingRpc();
    final int total = 250;
    final var keys = new ArrayList<PublicKey>(total);
    for (int i = 0; i < total; ++i) {
      final var accountKey = key(i + 10);
      keys.add(accountKey);
      rpc.universe.put(accountKey, account(accountKey, 1L, new byte[]{1}));
    }
    rpc.interruptOnCall = 3;

    final var fetcher = createFetcher(rpc, Set.of());
    final var consumer = new RecordingConsumer();
    fetcher.queueBatchable(keys, consumer);
    fetcher.run();

    assertEquals(3, rpc.calls.size());
    for (final var call : rpc.calls) {
      assertTrue(call.size() <= SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS, "call size " + call.size());
    }
    final var fetched = rpc.calls.stream().flatMap(List::stream).distinct().toList();
    assertEquals(total, fetched.size());
    // one callback per chunk
    assertEquals(3, consumer.received.size());
  }

  @Test
  void priorityBatchesJumpTheQueue() {
    final var rpc = new RecordingRpc();
    final var slowKeys = new ArrayList<PublicKey>(SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS);
    for (int i = 0; i < SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS; ++i) {
      slowKeys.add(key(1000 + i));
    }
    final var urgent = key(1);
    rpc.universe.put(urgent, account(urgent, 1L, new byte[]{1}));
    rpc.interruptOnCall = 2;

    final var fetcher = createFetcher(rpc, Set.of());
    final var slowConsumer = new RecordingConsumer();
    final var urgentConsumer = new RecordingConsumer();
    fetcher.queue(slowKeys, slowConsumer);
    fetcher.priorityQueue(List.of(urgent), urgentConsumer);
    fetcher.run();

    assertEquals(2, rpc.calls.size());
    assertTrue(rpc.calls.getFirst().contains(urgent), "priority batch must be fetched first");
    assertFalse(rpc.calls.getFirst().containsAll(slowKeys));
  }

  @Test
  void emptyBatchCompletesImmediatelyWithoutFetching() {
    final var rpc = new RecordingRpc();
    final var fetcher = createFetcher(rpc, Set.of());

    final var future = fetcher.queue(List.of());
    assertTrue(future.isDone());
    final var result = future.join();
    assertEquals(List.of(), result.accounts());
    assertEquals(Map.of(), result.accountMap());
    assertEquals(0, rpc.calls.size());
  }

  @Test
  void oversizedBatchesAreRejected() {
    final var rpc = new RecordingRpc();
    final var fetcher = createFetcher(rpc, Set.of());
    final var tooMany = new ArrayList<PublicKey>();
    for (int i = 0; i <= SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS; ++i) {
      tooMany.add(key(i + 1));
    }
    final var consumer = new RecordingConsumer();
    assertThrows(IllegalStateException.class, () -> fetcher.queue(tooMany, consumer));
    // batchable overloads are the escape hatch and must not throw
    assertDoesNotThrow(() -> fetcher.queueBatchable(tooMany, consumer));
  }

  @Test
  void futureBatchesCompleteWithTheResult() {
    final var rpc = new RecordingRpc();
    final var present = key(1);
    rpc.universe.put(present, account(present, 1L, new byte[]{9}));

    final var fetcher = createFetcher(rpc, Set.of());
    final var future = fetcher.priorityQueue(List.of(present));
    assertFalse(future.isDone());
    fetcher.run();

    final var result = future.join();
    assertArrayEquals(new byte[]{9}, result.accountMap().get(present).data());
    assertEquals(1, result.accounts().size());
  }

  @Test
  void uniqueConsumersAreNotDoubleQueued() {
    final var rpc = new RecordingRpc();
    final var first = key(1);
    final var second = key(2);
    rpc.universe.put(first, account(first, 1L, new byte[]{1}));

    final var fetcher = createFetcher(rpc, Set.of());
    final var consumer = new RecordingConsumer();
    fetcher.queueUnique(List.of(first), consumer);
    // same consumer still pending: this request is dropped
    fetcher.priorityQueueUnique(List.of(second), consumer);
    fetcher.run();

    assertEquals(1, rpc.calls.size());
    assertTrue(rpc.calls.getFirst().contains(first));
    assertFalse(rpc.calls.getFirst().contains(second));
    assertEquals(1, consumer.received.size());
  }

  @Test
  void listenToAllReceivesEveryBatchUntilStopped() {
    final var rpc = new RecordingRpc();
    final var present = key(1);
    rpc.universe.put(present, account(present, 1L, new byte[]{1}));

    final var fetcher = createFetcher(rpc, Set.of());
    final var listener = new RecordingConsumer();
    fetcher.listenToAll(listener);
    fetcher.queue(List.of(present), new RecordingConsumer());
    fetcher.run();
    assertEquals(1, listener.received.size());

    Thread.interrupted();
    fetcher.stopListening(listener);
    fetcher.queue(List.of(present), new RecordingConsumer());
    fetcher.run();
    assertEquals(1, listener.received.size());
  }
}
