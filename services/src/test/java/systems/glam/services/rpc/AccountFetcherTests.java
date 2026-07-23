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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.*;

/// Drives AccountFetcherImpl.run() deterministically on the test thread: a
/// Proxy-backed SolanaRpcClient serves canned batches (no request leaves the
/// JVM), the fetch delay sits at its one millisecond floor, and the fake
/// interrupts the thread on its final batch so run() exits through its
/// InterruptedException path -- the pending interrupt makes that sleep throw
/// immediately rather than wait.
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
    long respondDelayMillis;
    boolean returnNull;

    SolanaRpcClient client() {
      return (SolanaRpcClient) Proxy.newProxyInstance(
          SolanaRpcClient.class.getClassLoader(),
          new Class<?>[]{SolanaRpcClient.class},
          (proxy, method, args) -> {
            if (method.getName().equals("getAccounts")) {
              @SuppressWarnings("unchecked") final var keys = (List<PublicKey>) args[0];
              calls.add(List.copyOf(keys));
              if (respondDelayMillis > 0) {
                Thread.sleep(respondDelayMillis);
              }
              if (returnNull) {
                return java.util.concurrent.CompletableFuture.completedFuture(null);
              }
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

  private static class RecordingConsumer implements AccountConsumer {

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
    // one millisecond is the floor a polling fetcher accepts; the fake
    // interrupts the thread on its last batch, so the sleep throws at once
    // rather than actually waiting
    return AccountFetcher.createFetcher(Duration.ofMillis(1), false, createCaller(rpc), alwaysFetch);
  }

  @Test
  void pollingFetchersRejectADelayThatWouldNotSleep() {
    final var rpc = new RecordingRpc();
    // a polling fetcher sleeps between passes, so a sub-millisecond delay
    // would round to a spin
    for (final var tooSmall : new Duration[]{Duration.ZERO, Duration.ofNanos(999_999)}) {
      final var caller = createCaller(rpc);
      final var ex = assertThrows(
          IllegalArgumentException.class,
          () -> AccountFetcher.createFetcher(tooSmall, false, caller, Set.of())
      );
      assertTrue(ex.getMessage().contains("at least one millisecond"), ex.getMessage());
    }
    // exactly the floor is accepted
    assertNotNull(AccountFetcher.createFetcher(Duration.ofMillis(1), false, createCaller(rpc), Set.of()));
    // reactive fetchers wait on a condition instead of sleeping, so any delay
    // remains legal there
    assertNotNull(AccountFetcher.createFetcher(Duration.ZERO, true, createCaller(rpc), Set.of()));
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
    // queueing and the run loop both hand the lock back
    assertFalse(((AccountFetcherImpl) fetcher).lock.isLocked());
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
    assertFalse(((AccountFetcherImpl) fetcher).lock.isLocked());
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

  @Test
  void anEmptyBatchIsNeverDelivered() {
    final var rpc = new RecordingRpc();
    final var present = key(1);
    rpc.universe.put(present, account(present, 1L, new byte[]{1}));
    final var fetcher = createFetcher(rpc, Set.of());
    final var never = new RecordingConsumer();
    final var real = new RecordingConsumer();
    // empty collections are dropped at the door, for every queueing flavour
    fetcher.queue(List.of(), never);
    fetcher.priorityQueue(List.of(), never);
    fetcher.queueUnique(List.of(), never);
    fetcher.priorityQueueUnique(List.of(), never);
    fetcher.queue(List.of(present), real);
    fetcher.run();
    assertEquals(0, never.received.size());
    assertEquals(1, real.received.size());
  }

  @Test
  void smallBatchableListsAreQueuedWhole() {
    final var rpc = new RecordingRpc();
    final var a = key(1);
    final var b = key(2);
    rpc.universe.put(a, account(a, 1L, new byte[]{1}));
    final var fetcher = createFetcher(rpc, Set.of());
    final var consumer = new RecordingConsumer();
    fetcher.queueBatchable(List.of(a, b), consumer);
    fetcher.run();
    assertEquals(1, rpc.calls.size());
    assertEquals(1, consumer.received.size());
  }

  @Test
  void aFreshPriorityUniqueConsumerIsServed() {
    final var rpc = new RecordingRpc();
    final var present = key(1);
    rpc.universe.put(present, account(present, 1L, new byte[]{1}));
    final var fetcher = createFetcher(rpc, Set.of());
    final var consumer = new RecordingConsumer();
    fetcher.priorityQueueUnique(List.of(present), consumer);
    fetcher.run();
    assertEquals(1, consumer.received.size());
  }

  @Test
  void recentSlotSkipsNullAccountsNullContextsAndZeroSlots() {
    final var rpc = new RecordingRpc();
    final var zeroSlot = key(1);
    final var nullContext = key(2);
    final var absent = key(3);
    final var real = key(4);
    rpc.universe.put(zeroSlot, account(zeroSlot, 0L, new byte[]{1}));
    rpc.universe.put(nullContext, new AccountInfo<>(
        nullContext, null, false, 0, SolanaAccounts.MAIN_NET.systemProgram(),
        BigInteger.ZERO, 0, new byte[]{1}
    ));
    rpc.universe.put(real, account(real, 7_777L, new byte[]{1}));

    final var fetcher = createFetcher(rpc, Set.of());
    final var consumer = new RecordingConsumer();
    // the real slot comes first; every later account must be skipped, not
    // allowed to overwrite it with zero or blow up on a null
    fetcher.queue(List.of(real, zeroSlot, nullContext, absent), consumer);
    fetcher.run();
    assertEquals(1, consumer.received.size());
    assertEquals(7_777L, fetcher.recentSlot().slot());
  }

  @Test
  void aCallbackMayQueueIntoTheBatchInFlight() {
    final var rpc = new RecordingRpc();
    final var present = key(1);
    rpc.universe.put(present, account(present, 1L, new byte[]{1}));
    final var fetcher = createFetcher(rpc, Set.of());

    // a consumer re-queueing keys the in-flight batch already covers is served
    // from that same batch — one RPC call, both consumers, the same result map
    final var second = new RecordingConsumer();
    final var first = new RecordingConsumer() {
      @Override
      public void accept(final List<AccountInfo<byte[]>> accounts, final Map<PublicKey, AccountInfo<byte[]>> accountMap) {
        super.accept(accounts, accountMap);
        if (received.size() == 1) {
          fetcher.queue(List.of(present), second);
        }
      }
    };
    fetcher.queue(List.of(present), first);
    fetcher.run();
    assertEquals(1, rpc.calls.size());
    assertEquals(1, first.received.size());
    assertEquals(1, second.received.size());
    assertSame(first.received.getFirst(), second.received.getFirst());
  }

  @Test
  void alwaysFetchKeysAreRestoredBetweenCycles() {
    final var rpc = new RecordingRpc();
    final var always = key(90);
    final var first = key(1);
    final var secondKey = key(2);
    rpc.universe.put(always, account(always, 1L, new byte[]{1}));
    rpc.universe.put(first, account(first, 1L, new byte[]{1}));
    rpc.universe.put(secondKey, account(secondKey, 1L, new byte[]{1}));
    rpc.interruptOnCall = 2;

    final var fetcher = createFetcher(rpc, Set.of(always));
    // a consumer that queues the SECOND batch from inside the first cycle's
    // callback, after createBatch has already run — forcing a second cycle
    final var second = new RecordingConsumer();
    final var firstConsumer = new RecordingConsumer() {
      @Override
      public void accept(final List<AccountInfo<byte[]>> accounts, final Map<PublicKey, AccountInfo<byte[]>> accountMap) {
        super.accept(accounts, accountMap);
        if (received.size() == 1) {
          fetcher.queue(List.of(secondKey), second);
        }
      }
    };
    fetcher.queue(List.of(first), firstConsumer);
    fetcher.run();
    // after the first cycle trims the batch back down, the always-fetch keys
    // must still ride along in the second cycle
    assertEquals(2, rpc.calls.size());
    assertTrue(rpc.calls.getFirst().contains(always));
    assertTrue(rpc.calls.get(1).contains(always));
    assertTrue(rpc.calls.get(1).contains(secondKey));
    assertFalse(rpc.calls.get(1).contains(first), "the first cycle's keys must be trimmed");
    assertEquals(1, second.received.size());
  }

  @Test
  void aFullBatchAbsorbsOverlapAndDefersTheRest() {
    final var rpc = new RecordingRpc();
    final var bigKeys = new ArrayList<PublicKey>(SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS);
    for (int i = 0; i < SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS; ++i) {
      final var accountKey = key(500 + i);
      bigKeys.add(accountKey);
      rpc.universe.put(accountKey, account(accountKey, 1L, new byte[]{1}));
    }
    final var overlap = List.of(bigKeys.get(0), bigKeys.get(1));
    final var laterOverlap = List.of(bigKeys.get(2), bigKeys.get(3));
    final var distinct = key(2);
    rpc.universe.put(distinct, account(distinct, 1L, new byte[]{1}));
    rpc.interruptOnCall = 2;

    final var fetcher = createFetcher(rpc, Set.of());
    final var bigConsumer = new RecordingConsumer();
    final var overlapConsumer = new RecordingConsumer();
    final var laterOverlapConsumer = new RecordingConsumer();
    final var distinctConsumer = new RecordingConsumer();
    fetcher.queue(bigKeys, bigConsumer);
    fetcher.queue(overlap, overlapConsumer);
    fetcher.queue(List.of(distinct), distinctConsumer);
    fetcher.queue(laterOverlap, laterOverlapConsumer);
    fetcher.run();

    assertEquals(2, rpc.calls.size());
    // the full first batch absorbed every 100%-overlapping request, even one
    // queued behind the non-overlapping batch...
    assertEquals(SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS, rpc.calls.getFirst().size());
    assertEquals(1, bigConsumer.received.size());
    assertEquals(1, overlapConsumer.received.size());
    assertSame(bigConsumer.received.getFirst(), overlapConsumer.received.getFirst());
    assertEquals(1, laterOverlapConsumer.received.size());
    assertSame(bigConsumer.received.getFirst(), laterOverlapConsumer.received.getFirst());
    // ...while the non-overlapping batch waited for the next cycle, which must
    // actually contain its key
    assertEquals(1, distinctConsumer.received.size());
    assertTrue(rpc.calls.get(1).contains(distinct));
    assertNotSame(bigConsumer.received.getFirst(), distinctConsumer.received.getFirst());
    // and the map it was served from holds its account: being absorbed into a
    // batch that never fetched the key would serve a hole
    assertNotNull(distinctConsumer.received.getFirst().get(distinct));
  }

  @Test
  void aThrowingConsumerDoesNotStopTheFetcher() {
    final var rpc = new RecordingRpc();
    final var present = key(1);
    final var secondKey = key(2);
    rpc.universe.put(present, account(present, 1L, new byte[]{1}));
    rpc.universe.put(secondKey, account(secondKey, 1L, new byte[]{1}));
    rpc.interruptOnCall = 2;

    final var fetcher = createFetcher(rpc, Set.of());
    final var throwingListener = new RecordingConsumer() {
      @Override
      public void accept(final List<AccountInfo<byte[]>> accounts, final Map<PublicKey, AccountInfo<byte[]>> accountMap) {
        throw new IllegalStateException("listener boom");
      }
    };
    final var healthy = new RecordingConsumer();
    final var secondCycle = new RecordingConsumer();
    // one bad tenant in every dispatch flavour: an always-call listener, a
    // batch consumer served before the healthy one, and a unique consumer —
    // each failure is its own, and the poll loop outlives all of them
    fetcher.listenToAll(throwingListener);
    fetcher.queueUnique(List.of(present), new RecordingConsumer() {
      @Override
      public void accept(final List<AccountInfo<byte[]>> accounts, final Map<PublicKey, AccountInfo<byte[]>> accountMap) {
        // queue the second cycle before dying, proving the loop continues past
        fetcher.queue(List.of(secondKey), secondCycle);
        throw new IllegalStateException("unique boom");
      }
    });
    fetcher.queue(List.of(present), new RecordingConsumer() {
      @Override
      public void accept(final List<AccountInfo<byte[]>> accounts, final Map<PublicKey, AccountInfo<byte[]>> accountMap) {
        throw new IllegalStateException("batch boom");
      }
    });
    fetcher.queue(List.of(present), healthy);

    try (final var log = systems.glam.services.tests.LogCapture.attach(AccountFetcher.class.getName())) {
      fetcher.run();
      // each failure is reported, and none is the loop-fatal variant
      log.assertLogged("Account consumer failed");
      assertFalse(
          log.messages().stream().anyMatch(m -> m != null && m.contains("Unexpected error fetching accounts")),
          () -> log.messages().toString()
      );
    }

    // the healthy consumer in the same batch was still served, and the loop
    // survived into a second cycle
    assertEquals(1, healthy.received.size());
    assertEquals(1, secondCycle.received.size());
    assertEquals(2, rpc.calls.size());
  }

  @Test
  void anOversizedFirstCycleServesTheBatchAndPreservesAlwaysFetch() {
    final var rpc = new RecordingRpc();
    rpc.interruptOnCall = 2;
    final var alwaysFetch = new LinkedHashSet<PublicKey>();
    for (int i = 0; i < 70; ++i) {
      alwaysFetch.add(key(100 + i));
    }
    // 20 keys shared with the always-fetch set, 40 fresh: the union with the
    // 70 always-fetch keys exceeds the 100-account RPC limit
    final var batchKeys = new ArrayList<PublicKey>(60);
    for (int i = 0; i < 20; ++i) {
      batchKeys.add(key(100 + i));
    }
    for (int i = 0; i < 40; ++i) {
      batchKeys.add(key(500 + i));
    }
    final var fetcher = createFetcher(rpc, alwaysFetch);

    final var tiny = key(9_000);
    final var secondCycle = new RecordingConsumer();
    final var consumer = new RecordingConsumer() {
      @Override
      public void accept(final List<AccountInfo<byte[]>> accounts, final Map<PublicKey, AccountInfo<byte[]>> accountMap) {
        received.add(accountMap);
        // drive a second cycle so the always-fetch base can be inspected
        fetcher.queue(List.of(tiny), secondCycle);
      }
    };
    fetcher.priorityQueue(batchKeys, consumer);

    fetcher.run();

    assertEquals(2, rpc.calls.size());
    // the first request serves the batch's own keys and tops up with as many
    // always-fetch keys as fit
    final var first = rpc.calls.get(0);
    assertEquals(SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS, first.size());
    assertTrue(first.containsAll(batchKeys));
    final var union = new LinkedHashSet<>(alwaysFetch);
    union.addAll(batchKeys);
    assertTrue(union.containsAll(first));
    // the batch was served on the cycle that fetched its keys, not starved
    assertEquals(1, consumer.received.size());
    // the always-fetch base survived intact into the next cycle
    final var second = rpc.calls.get(1);
    assertTrue(second.containsAll(alwaysFetch), "always-fetch keys were corrupted by the oversized cycle");
    assertTrue(second.contains(tiny));
    assertEquals(71, second.size());
    assertEquals(1, secondCycle.received.size());
  }

  @Test
  void theSlotTimestampEstimateSplitsTheRoundTrip() {
    final var rpc = new RecordingRpc();
    rpc.respondDelayMillis = 80;
    final var present = key(1);
    rpc.universe.put(present, account(present, 7_777L, new byte[]{1}));
    final var fetcher = createFetcher(rpc, Set.of());
    fetcher.queue(List.of(present), new RecordingConsumer());

    final long before = System.currentTimeMillis();
    fetcher.run();
    final long after = System.currentTimeMillis();

    // the estimate is the midpoint of an 80ms round trip: meaningfully after
    // the request went out and meaningfully before the response landed
    final long estimate = fetcher.recentSlot().timestamp().toEpochMilli();
    assertTrue(estimate >= before + 30, () -> "estimate " + estimate + " vs before " + before);
    assertTrue(estimate <= after - 30, () -> "estimate " + estimate + " vs after " + after);
  }

  private static void awaitTrue(final String what, final java.util.function.BooleanSupplier condition) throws InterruptedException {
    for (int i = 0; i < 5_000; ++i) {
      if (condition.getAsBoolean()) {
        return;
      }
      Thread.sleep(1);
    }
    fail("timed out awaiting " + what);
  }

  @Test
  void reactiveFetchersParkAndWakeOnQueueSignals() throws InterruptedException {
    final var rpc = new RecordingRpc();
    final var present = key(1);
    rpc.universe.put(present, account(present, 42L, new byte[]{1}));
    final var fetcher = AccountFetcher.createFetcher(Duration.ZERO, true, createCaller(rpc), Set.of());
    final var consumer = new RecordingConsumer();

    final var worker = new Thread(fetcher::run, "account-fetcher");
    worker.start();
    try {
      // a reactive fetcher parks on the condition; it must not busy-spin
      awaitTrue("the reactive fetcher parked", () -> worker.getState() == Thread.State.WAITING);

      // queueing must signal the parked fetcher awake
      fetcher.queue(List.of(present), consumer);
      worker.join(5_000);
      assertFalse(worker.isAlive(), "the queue signal never woke the reactive fetcher");
    } finally {
      worker.interrupt();
    }
    assertEquals(1, rpc.calls.size());
    assertEquals(1, consumer.received.size());
  }

  @Test
  void aPollingFetcherWaitsQuietlyThenServesLateWork() throws InterruptedException {
    final var rpc = new RecordingRpc();
    rpc.interruptOnCall = 2;
    final var always = key(1);
    final var late = key(2);
    rpc.universe.put(always, account(always, 42L, new byte[]{1}));
    rpc.universe.put(late, account(late, 43L, new byte[]{2}));
    final var fetcher = createFetcher(rpc, Set.of(always));
    final var consumer = new RecordingConsumer();
    final var lateConsumer = new RecordingConsumer();

    final var fresh = key(10);
    rpc.universe.put(fresh, account(fresh, 41L, new byte[]{3}));
    final var worker = new Thread(fetcher::run, "account-fetcher");
    worker.start();
    try {
      // a fresh key: a subset of the always-fetch set would ride the current
      // batch instead of queueing, and would not wake the poller
      fetcher.queue(List.of(fresh), consumer);
      awaitTrue("the first batch was fetched", () -> rpc.calls.size() == 1);

      // an empty queue means waiting, not free-running cycles of the
      // always-fetch keys
      Thread.sleep(60);
      assertEquals(1, rpc.calls.size(), "the poller cycled on an empty queue");

      // late work is picked up by the polling sleep, no signal involved
      fetcher.queue(List.of(late), lateConsumer);
      worker.join(5_000);
      assertFalse(worker.isAlive(), "the poller never picked up late work");
    } finally {
      worker.interrupt();
    }
    assertEquals(2, rpc.calls.size());
    assertTrue(rpc.calls.get(1).contains(late));
    assertEquals(1, lateConsumer.received.size());
  }

  @Test
  void aMutatedOversizedBatchIsDroppedAndReported() {
    final var rpc = new RecordingRpc();
    final var always = key(1);
    rpc.universe.put(always, account(always, 42L, new byte[]{1}));
    final var fetcher = createFetcher(rpc, Set.of(always));
    final var consumer = new RecordingConsumer();
    // batches hold the caller's collection by reference; growing it past the
    // RPC limit after queueing is the failure this path guards against
    final var mutableKeys = new ArrayList<PublicKey>();
    mutableKeys.add(key(2));
    fetcher.queue(mutableKeys, consumer);
    for (int i = 0; i < SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS; ++i) {
      mutableKeys.add(key(1_000 + i));
    }

    try (final var log = systems.glam.services.tests.LogCapture.attach(AccountFetcher.class.getName())) {
      fetcher.run();
      log.assertLogged("Ignoring batch because it exceeds the RPC limit");
      // dropping the only queued batch must not run the queue iterator dry
      assertFalse(
          log.messages().stream().anyMatch(m -> m != null && m.contains("Unexpected error fetching accounts")),
          () -> log.messages().toString()
      );
    }

    assertTrue(consumer.exceeded, "the batch owner was never told its batch was dropped");
    assertEquals(0, consumer.received.size());
    // the cycle fell back to the always-fetch base
    assertEquals(1, rpc.calls.size());
    assertEquals(List.of(always), rpc.calls.getFirst());
  }

  @Test
  void aDroppedOversizedBatchLeavesTheQueueAndItsNeighborsAlone() {
    final var rpc = new RecordingRpc();
    rpc.interruptOnCall = 2;
    final var always = key(1);
    final var small = key(2);
    final var fresh = key(4);
    rpc.universe.put(always, account(always, 42L, new byte[]{1}));
    rpc.universe.put(small, account(small, 43L, new byte[]{2}));
    rpc.universe.put(fresh, account(fresh, 44L, new byte[]{3}));
    final var fetcher = createFetcher(rpc, Set.of(always));
    final var oversizedConsumer = new RecordingConsumer();
    final var smallConsumer = new RecordingConsumer();
    final var freshConsumer = new RecordingConsumer();
    final var mutableKeys = new ArrayList<PublicKey>();
    mutableKeys.add(key(3));
    fetcher.queue(mutableKeys, oversizedConsumer);
    for (int i = 0; i < SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS; ++i) {
      mutableKeys.add(key(1_000 + i));
    }
    fetcher.queue(List.of(small), smallConsumer);
    // queue more work from inside the first cycle so a second cycle runs
    fetcher.listenToAll(new RecordingConsumer() {
      @Override
      public void accept(final List<AccountInfo<byte[]>> accounts, final Map<PublicKey, AccountInfo<byte[]>> accountMap) {
        if (received.isEmpty()) {
          fetcher.queue(List.of(fresh), freshConsumer);
        }
        received.add(accountMap);
      }
    });

    try (final var log = systems.glam.services.tests.LogCapture.attach(AccountFetcher.class.getName())) {
      fetcher.run();
      // dropped means dropped: the oversized batch must not be reprocessed on
      // the second cycle, so exactly one warning ever fires
      assertEquals(1,
          log.messages().stream().filter(m -> m != null && m.contains("Ignoring batch")).count(),
          () -> log.messages().toString());
    }

    assertTrue(oversizedConsumer.exceeded);
    // the batch queued behind the dropped one rides the same cycle
    assertEquals(2, rpc.calls.size());
    assertTrue(rpc.calls.getFirst().contains(small));
    assertEquals(1, smallConsumer.received.size());
    assertTrue(rpc.calls.get(1).contains(fresh));
    assertEquals(1, freshConsumer.received.size());
  }

  @Test
  void anOversizedUniqueBatchIsAlsoReported() {
    final var rpc = new RecordingRpc();
    final var always = key(1);
    rpc.universe.put(always, account(always, 42L, new byte[]{1}));
    final var fetcher = createFetcher(rpc, Set.of(always));
    final var consumer = new RecordingConsumer();
    final var mutableKeys = new ArrayList<PublicKey>();
    mutableKeys.add(key(2));
    fetcher.priorityQueueUnique(mutableKeys, consumer);
    for (int i = 0; i < SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS; ++i) {
      mutableKeys.add(key(1_000 + i));
    }

    fetcher.run();

    assertTrue(consumer.exceeded, "the unique batch owner was never told its batch was dropped");
    assertEquals(0, consumer.received.size());
  }

  @Test
  void theLastBatchDeferringEndsTheScan() {
    final var rpc = new RecordingRpc();
    rpc.interruptOnCall = 2;
    final var fetcher = createFetcher(rpc, Set.of());
    final var a = new ArrayList<PublicKey>(60);
    for (int i = 0; i < 60; ++i) {
      a.add(key(500 + i));
    }
    final var b = new ArrayList<PublicKey>(90);
    for (int i = 0; i < 90; ++i) {
      b.add(key(600 + i));
    }
    final var aConsumer = new RecordingConsumer();
    final var bConsumer = new RecordingConsumer();
    fetcher.queue(a, aConsumer);
    fetcher.queue(b, bConsumer);

    fetcher.run();

    // b cannot join a and nothing is queued behind it: the scan must stop
    // cleanly and serve b whole on the next cycle
    assertEquals(2, rpc.calls.size());
    assertEquals(60, rpc.calls.getFirst().size());
    assertTrue(rpc.calls.getFirst().containsAll(a));
    assertEquals(90, rpc.calls.get(1).size());
    assertTrue(rpc.calls.get(1).containsAll(b));
    assertEquals(1, aConsumer.received.size());
    assertEquals(1, bConsumer.received.size());
  }

  @Test
  void anExactlyFullBatchIsServedNotDropped() {
    final var rpc = new RecordingRpc();
    final var always = key(1);
    final var fetcher = createFetcher(rpc, Set.of(always));
    final var consumer = new RecordingConsumer();
    final var batchKeys = new ArrayList<PublicKey>(SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS);
    for (int i = 0; i < SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS; ++i) {
      batchKeys.add(key(1_000 + i));
    }
    fetcher.queue(batchKeys, consumer);

    fetcher.run();

    // exactly at the limit there is no room for always-fetch keys, but the
    // batch itself is fetched whole and served -- not treated as oversized
    assertEquals(1, rpc.calls.size());
    assertEquals(SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS, rpc.calls.getFirst().size());
    assertTrue(rpc.calls.getFirst().containsAll(batchKeys));
    assertEquals(1, consumer.received.size());
    assertFalse(consumer.exceeded);
  }

  @Test
  void deferredWorkIsStillMergedBehindAnOversizedMiss() {
    final var rpc = new RecordingRpc();
    rpc.interruptOnCall = 2;
    final var fetcher = createFetcher(rpc, Set.of());
    final var a = new ArrayList<PublicKey>(60);
    for (int i = 0; i < 60; ++i) {
      a.add(key(500 + i));
    }
    final var b = new ArrayList<PublicKey>(90);
    for (int i = 0; i < 90; ++i) {
      b.add(key(600 + i));
    }
    final var c = new ArrayList<PublicKey>(30);
    for (int i = 0; i < 30; ++i) {
      c.add(key(700 + i));
    }
    final var aConsumer = new RecordingConsumer();
    final var bConsumer = new RecordingConsumer();
    final var cConsumer = new RecordingConsumer();
    fetcher.queue(a, aConsumer);
    fetcher.queue(b, bConsumer);
    fetcher.queue(c, cConsumer);

    fetcher.run();

    // b does not fit next to a, but c does: deferring b must not also defer c
    assertEquals(2, rpc.calls.size());
    final var first = rpc.calls.getFirst();
    assertEquals(90, first.size());
    assertTrue(first.containsAll(a));
    assertTrue(first.containsAll(c));
    assertEquals(1, aConsumer.received.size());
    assertEquals(1, cConsumer.received.size());
    // the deferred batch is served whole on the next cycle
    assertEquals(90, rpc.calls.get(1).size());
    assertTrue(rpc.calls.get(1).containsAll(b));
    assertEquals(1, bConsumer.received.size());
  }

  @Test
  void theReactiveMinimumDelaySeparatesCycles() throws InterruptedException {
    final var rpc = new RecordingRpc();
    rpc.interruptOnCall = 2;
    final var first = key(1);
    final var second = key(2);
    rpc.universe.put(first, account(first, 42L, new byte[]{1}));
    rpc.universe.put(second, account(second, 43L, new byte[]{2}));
    final var fetcher = AccountFetcher.createFetcher(Duration.ofMillis(150), true, createCaller(rpc), Set.of());
    final var consumer = new RecordingConsumer();

    final var worker = new Thread(fetcher::run, "account-fetcher");
    worker.start();
    try {
      fetcher.queue(List.of(first), consumer);
      awaitTrue("the first batch was fetched", () -> rpc.calls.size() == 1);
      final long queuedAt = System.nanoTime();
      fetcher.queue(List.of(second), consumer);
      worker.join(10_000);
      assertFalse(worker.isAlive(), "the second cycle never ran");
      // a reactive fetcher still honors the minimum delay between cycles
      final long elapsedMillis = (System.nanoTime() - queuedAt) / 1_000_000L;
      assertTrue(elapsedMillis >= 120, () -> "second cycle ran after only " + elapsedMillis + "ms");
    } finally {
      worker.interrupt();
    }
    assertEquals(2, rpc.calls.size());
    assertEquals(2, consumer.received.size());
  }

  @Test
  void aFetchFailureIsLoggedAndEndsTheRunLoop() {
    final var rpc = new RecordingRpc();
    rpc.returnNull = true;
    final var present = key(1);
    final var fetcher = createFetcher(rpc, Set.of());
    fetcher.queue(List.of(present), new RecordingConsumer());

    try (final var log = systems.glam.services.tests.LogCapture.attach(AccountFetcher.class.getName())) {
      // the failure must not leak to the polling thread, and must be reported
      assertDoesNotThrow(fetcher::run);
      log.assertLogged("Unexpected error fetching accounts");
    }
  }

  @Test
  void aServedUniqueConsumerMayBeQueuedAgain() throws InterruptedException {
    final var rpc = new RecordingRpc();
    rpc.interruptOnCall = 99;
    final var present = key(1);
    rpc.universe.put(present, account(present, 42L, new byte[]{1}));
    final var fetcher = createFetcher(rpc, Set.of());
    final var consumer = new RecordingConsumer() {
      @Override
      public void accept(final List<AccountInfo<byte[]>> accounts, final Map<PublicKey, AccountInfo<byte[]>> accountMap) {
        received.add(accountMap);
        if (received.size() == 1) {
          // being served must clear the unique guard, or this re-queue is
          // silently refused and the consumer starves; the keys are covered
          // by the batch in flight, so it is served from the same cycle
          fetcher.priorityQueueUnique(List.of(present), this);
        }
      }
    };
    fetcher.priorityQueueUnique(List.of(present), consumer);

    final var worker = new Thread(fetcher::run, "account-fetcher");
    try {
      worker.start();
      awaitTrue("the re-queued unique consumer was served",
          () -> consumer.received.size() == 2);
    } finally {
      worker.interrupt();
    }
    worker.join(5_000);
    assertFalse(worker.isAlive());
    assertEquals(1, rpc.calls.size());
  }
}
