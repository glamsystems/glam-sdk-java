package systems.glam.services.integrations;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.lookup.AddressLookupTable;
import software.sava.core.encoding.ByteUtil;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.Context;
import systems.glam.services.io.FileUtils;
import systems.glam.services.rpc.AccountFetcher;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.lookup.AddressLookupTable.DEACTIVATION_SLOT_OFFSET;
import static software.sava.core.accounts.lookup.AddressLookupTable.LOOKUP_TABLE_META_SIZE;

final class IntegLookupTableCacheTests {

  @Test
  void rejectsAFetchDelayThatWouldNotSleep(@TempDir final Path tempDir) {
    // the run loop sleeps for this delay between passes, so anything under a
    // millisecond rounds to no sleep and spins a core
    for (final var tooSmall : new Duration[]{Duration.ZERO, Duration.ofNanos(999_999), Duration.ofMillis(-1)}) {
      final var ex = assertThrows(
          IllegalArgumentException.class,
          () -> new IntegLookupTableCacheImpl(tooSmall, tempDir, new ConcurrentHashMap<>(), null)
      );
      assertTrue(ex.getMessage().contains("at least one millisecond"), ex.getMessage());
    }
    // exactly the floor is accepted
    assertNotNull(new IntegLookupTableCacheImpl(Duration.ofMillis(1), tempDir, new ConcurrentHashMap<>(), null));
  }

  private static PublicKey key(final int id) {
    final byte[] bytes = new byte[PublicKey.PUBLIC_KEY_LENGTH];
    bytes[0] = (byte) (id >> 8);
    bytes[1] = (byte) id;
    bytes[31] = 5;
    return PublicKey.createPubKey(bytes);
  }

  /// An active (never-deactivated) table holding `numAccounts` distinct keys.
  private static byte[] tableData(final int numAccounts) {
    final byte[] data = new byte[LOOKUP_TABLE_META_SIZE + numAccounts * PublicKey.PUBLIC_KEY_LENGTH];
    ByteUtil.putInt64LE(data, DEACTIVATION_SLOT_OFFSET, -1L);
    for (int i = 0; i < numAccounts; ++i) {
      key(1_000 + i).write(data, LOOKUP_TABLE_META_SIZE + i * PublicKey.PUBLIC_KEY_LENGTH);
    }
    return data;
  }

  private static AccountInfo<byte[]> accountInfo(final PublicKey pubKey, final byte[] data) {
    return new AccountInfo<>(
        pubKey, new Context(1L, null), false, 0, pubKey, BigInteger.ZERO, 0, data
    );
  }

  private static IntegLookupTableCacheImpl cache(final Path directory,
                                                 final ConcurrentMap<PublicKey, AddressLookupTable> tables,
                                                 final AccountFetcher fetcher) {
    return new IntegLookupTableCacheImpl(Duration.ofMillis(1), directory, tables, fetcher);
  }

  @Test
  void acceptedTablesAreServedAndOnlyGrowDeeper(@TempDir final Path tempDir) {
    final var tables = new ConcurrentHashMap<PublicKey, AddressLookupTable>();
    final var cache = cache(tempDir, tables, null);
    final var tableKey = key(1);

    final var accepted = cache.acceptTableAccount(accountInfo(tableKey, tableData(3)));
    assertNotNull(accepted);
    assertSame(accepted, cache.table(tableKey));
    assertEquals(3, accepted.numUniqueAccounts());

    // a deeper snapshot replaces it; a shallower one is ignored
    final var deeper = cache.acceptTableAccount(accountInfo(tableKey, tableData(5)));
    assertEquals(5, deeper.numUniqueAccounts());
    assertSame(deeper, cache.table(tableKey));
    final var kept = cache.acceptTableAccount(accountInfo(tableKey, tableData(2)));
    assertSame(deeper, kept);
    assertSame(deeper, cache.table(tableKey));
    // equal depth is not deeper: the existing table is kept by identity
    assertSame(deeper, cache.acceptTableAccount(accountInfo(tableKey, tableData(5))));
    assertSame(deeper, cache.table(tableKey));

    // deactivation forgets the table
    final byte[] deactivated = tableData(5);
    ByteUtil.putInt64LE(deactivated, DEACTIVATION_SLOT_OFFSET, 123L);
    assertNull(cache.acceptTableAccount(accountInfo(tableKey, deactivated)));
    assertNull(cache.table(tableKey));
  }

  @Test
  void polledUpdatesGrowDeleteAndPersist(@TempDir final Path tempDir) {
    final var tables = new ConcurrentHashMap<PublicKey, AddressLookupTable>();
    final var cache = cache(tempDir, tables, null);
    final var grows = key(1);
    final var shrinks = key(2);
    final var vanishes = key(3);
    final var deactivates = key(4);
    for (final var tableKey : List.of(grows, shrinks, vanishes, deactivates)) {
      cache.acceptTableAccount(accountInfo(tableKey, tableData(3)));
      // pre-existing persisted data, so deletions are observable
      IntegLookupTableCacheImpl.writeTableData(tempDir, accountInfo(tableKey, tableData(3)));
    }
    final var shrunkBefore = cache.table(shrinks);

    final byte[] deactivatedData = tableData(3);
    ByteUtil.putInt64LE(deactivatedData, DEACTIVATION_SLOT_OFFSET, 99L);
    final byte[] grown = tableData(6);
    cache.accept(
        List.of(),
        Map.of(
            grows, accountInfo(grows, grown),
            shrinks, accountInfo(shrinks, tableData(2)),
            deactivates, accountInfo(deactivates, deactivatedData)
            // 'vanishes' is absent from the map entirely
        )
    );

    // grown: replaced and re-persisted with the new bytes
    assertEquals(6, cache.table(grows).numUniqueAccounts());
    assertArrayEquals(grown, readTableFile(tempDir, grows));
    // shrunk: the deeper table is kept, untouched by identity
    assertSame(shrunkBefore, cache.table(shrinks));
    // vanished from the chain: forgotten and its file deleted
    assertNull(cache.table(vanishes));
    assertFalse(Files.exists(FileUtils.resolveAccountPath(tempDir, vanishes)));
    // deactivated on the chain: forgotten and its file deleted
    assertNull(cache.table(deactivates));
    assertFalse(Files.exists(FileUtils.resolveAccountPath(tempDir, deactivates)));
  }

  private static byte[] readTableFile(final Path directory, final PublicKey tableKey) {
    try {
      return Files.readAllBytes(FileUtils.resolveAccountPath(directory, tableKey));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Test
  void theRunLoopPollsTheTrackedTables(@TempDir final Path tempDir) {
    final var tables = new ConcurrentHashMap<PublicKey, AddressLookupTable>();
    final var queued = new ArrayList<List<PublicKey>>();
    final var fetcher = (AccountFetcher) Proxy.newProxyInstance(
        AccountFetcher.class.getClassLoader(),
        new Class<?>[]{AccountFetcher.class},
        (proxy, method, args) -> {
          if (method.getName().equals("queueBatchable")) {
            @SuppressWarnings("unchecked") final var keys = (List<PublicKey>) args[0];
            queued.add(keys);
            if (queued.size() == 2) {
              Thread.currentThread().interrupt();
            }
            return null;
          }
          throw new UnsupportedOperationException(method.getName());
        }
    );
    final var cache = cache(tempDir, tables, fetcher);
    cache.acceptTableAccount(accountInfo(key(1), tableData(3)));

    cache.run();

    assertEquals(2, queued.size());
    assertEquals(List.of(key(1)), queued.getFirst());
  }

  @Test
  void persistenceFailuresAreLoggedNotFatal(@TempDir final Path tempDir) throws IOException {
    // a file where the directory should be: writes fail softly
    final var blocked = tempDir.resolve("tables");
    Files.createFile(blocked);
    final var tables = new ConcurrentHashMap<PublicKey, AddressLookupTable>();
    final var cache = cache(blocked, tables, null);
    cache.acceptTableAccount(accountInfo(key(1), tableData(3)));

    try (final var log = systems.glam.services.tests.LogCapture.attach(IntegLookupTableCache.class.getName())) {
      assertDoesNotThrow(() -> cache.accept(List.of(), Map.of(key(1), accountInfo(key(1), tableData(9)))));
      log.assertLogged("Failed to write integration lookup table data");
    }
    // the in-memory table still grew despite the failed persist
    assertEquals(9, cache.table(key(1)).numUniqueAccounts());
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

  private static software.sava.services.solana.remote.call.RpcCaller rpcCaller(
      final List<List<PublicKey>> requested,
      final List<AccountInfo<byte[]>> response) {
    final var client = (software.sava.rpc.json.http.client.SolanaRpcClient) Proxy.newProxyInstance(
        software.sava.rpc.json.http.client.SolanaRpcClient.class.getClassLoader(),
        new Class<?>[]{software.sava.rpc.json.http.client.SolanaRpcClient.class},
        (proxy, method, args) -> {
          if (method.getName().equals("getAccounts")) {
            @SuppressWarnings("unchecked") final var keys = (List<PublicKey>) args[0];
            requested.add(List.copyOf(keys));
            return java.util.concurrent.CompletableFuture.completedFuture(response);
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
    return new software.sava.services.solana.remote.call.RpcCaller(
        java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor(),
        software.sava.services.core.remote.load_balance.LoadBalancer.createBalancer(item),
        software.sava.services.solana.remote.call.CallWeights.createDefault()
    );
  }

  @Test
  void initCacheLoadsPersistedTablesAndFetchesOnlyTheMissing(@TempDir final Path tempDir) throws IOException {
    final var onDisk = key(11);
    final var fetched = key(12);
    final var missing = key(13);
    final var directory = tempDir.resolve("tables");
    Files.createDirectories(directory);
    Files.write(directory.resolve(onDisk.toBase58() + ".dat"), tableData(3));
    // a foreign file in the directory is ignored, not parsed
    Files.write(directory.resolve("README.md"), new byte[]{42});

    final var requested = new ArrayList<List<PublicKey>>();
    final var response = new ArrayList<AccountInfo<byte[]>>();
    response.add(accountInfo(fetched, tableData(4)));
    response.add(null);
    // a present account with null data is a miss, not a table
    response.add(new AccountInfo<>(missing, new Context(1L, null), false, 0, missing, BigInteger.ZERO, 0, null));

    try (final var logs = systems.glam.services.tests.LogCapture.attach(IntegLookupTableCache.class.getName())) {
      final var cache = IntegLookupTableCache.initCache(
          Duration.ofMillis(1),
          directory,
          java.util.Set.of(onDisk, fetched, missing),
          rpcCaller(requested, response),
          null
      ).join();

      // the persisted table came from disk, the missing one from the fetch
      assertEquals(3, cache.table(onDisk).numUniqueAccounts());
      assertEquals(4, cache.table(fetched).numUniqueAccounts());
      assertNull(cache.table(missing));
      logs.assertLogged("Integration lookup table does not exist: " + missing.toBase58());
      // and only the genuinely missing key is warned about
      assertTrue(logs.messages().stream()
              .filter(m -> m != null && m.contains("does not exist"))
              .noneMatch(m -> m.contains(onDisk.toBase58()) || m.contains(fetched.toBase58())),
          () -> logs.messages().toString());

      // only the keys the disk did not carry were requested
      assertEquals(1, requested.size());
      assertEquals(java.util.Set.of(fetched, missing), java.util.Set.copyOf(requested.getFirst()));

      // the fetched table was persisted for the next start
      assertArrayEquals(tableData(4), Files.readAllBytes(directory.resolve(fetched.toBase58() + ".dat")));
    }
  }

  @Test
  void initCacheCompletesWithoutFetchingWhenNothingIsMissing(@TempDir final Path tempDir) throws IOException {
    final var onDisk = key(21);
    final var directory = tempDir.resolve("fresh").resolve("tables"); // parents are created
    Files.createDirectories(directory);
    Files.write(directory.resolve(onDisk.toBase58() + ".dat"), tableData(5));

    // a null caller proves the fetch path is never taken
    final var cache = IntegLookupTableCache.initCache(
        Duration.ofMillis(1),
        directory,
        java.util.Set.of(onDisk),
        null,
        null
    ).join();
    assertEquals(5, cache.table(onDisk).numUniqueAccounts());
  }
}
