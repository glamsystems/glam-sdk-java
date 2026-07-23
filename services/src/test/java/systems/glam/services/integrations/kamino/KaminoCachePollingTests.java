package systems.glam.services.integrations.kamino;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.sava.core.accounts.PublicKey;
import software.sava.core.encoding.ByteUtil;
import software.sava.idl.clients.kamino.KaminoAccounts;
import software.sava.idl.clients.kamino.lend.gen.types.Reserve;
import software.sava.idl.clients.kamino.lend.gen.types.ReserveConfig;
import software.sava.idl.clients.kamino.lend.gen.types.TokenInfo;
import software.sava.idl.clients.kamino.scope.gen.types.Configuration;
import software.sava.idl.clients.kamino.scope.gen.types.OracleMappings;
import software.sava.idl.clients.kamino.scope.gen.types.OracleType;
import software.sava.idl.clients.kamino.lend.gen.types.ScopeConfiguration;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.Context;
import systems.glam.services.oracles.scope.ScopeFeedContext;
import systems.glam.services.rpc.AccountResult;
import systems.glam.services.tests.LogCapture;
import systems.glam.services.tests.ResourceUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;

final class KaminoCachePollingTests {

  private static final PublicKey SOL_RESERVE_KEY = fromBase58Encoded("d4A2prbA2whesmvHaL88BH6Ewn5N4bTSU2Ze8P6Bc4Q");
  private static final PublicKey VAULT_KEY = fromBase58Encoded("5YxwKgsvyTdT8q2CBgwA4L9BKbnKNrB66K9wUzij5wH");

  private static final PublicKey CONFIG2_KEY = key(61);
  private static final PublicKey MAPPINGS2_KEY = key(62);
  private static final PublicKey PRICES2_KEY = key(63);
  private static final PublicKey ORACLE = key(64);
  private static final PublicKey RESERVE_A_KEY = key(65);
  private static final PublicKey FEEDLESS_KEY = key(66);
  private static final PublicKey NIL_KEY = key(67);

  private static final int SCOPE_CONFIG_BASE =
      Reserve.CONFIG_OFFSET + ReserveConfig.TOKEN_INFO_OFFSET + TokenInfo.SCOPE_CONFIGURATION_OFFSET;
  private static final int COLLATERAL_SUPPLY_OFFSET =
      Reserve.COLLATERAL_OFFSET + software.sava.idl.clients.kamino.lend.gen.types.ReserveCollateral.MINT_TOTAL_SUPPLY_OFFSET;

  private static byte[] reserveFixture;
  private static byte[] vaultFixture;
  private static byte[] config2Data;
  private static byte[] mappings2Data;

  private static PublicKey key(final int id) {
    final byte[] bytes = new byte[PublicKey.PUBLIC_KEY_LENGTH];
    bytes[0] = (byte) (id >> 8);
    bytes[1] = (byte) id;
    return PublicKey.createPubKey(bytes);
  }

  @BeforeAll
  static void beforeAll() throws IOException {
    reserveFixture = ResourceUtil.readResource("accounts/kamino/" + SOL_RESERVE_KEY + ".dat.gz");
    vaultFixture = ResourceUtil.readResource("accounts/kamino/" + VAULT_KEY + ".dat.gz");

    config2Data = new byte[Configuration.BYTES];
    System.arraycopy(Configuration.DISCRIMINATOR.data(), 0, config2Data, 0, 8);
    MAPPINGS2_KEY.write(config2Data, Configuration.ORACLE_MAPPINGS_OFFSET);
    PRICES2_KEY.write(config2Data, Configuration.ORACLE_PRICES_OFFSET);

    mappings2Data = new byte[OracleMappings.BYTES];
    System.arraycopy(OracleMappings.DISCRIMINATOR.data(), 0, mappings2Data, 0, 8);
    for (final int index : new int[]{11, 12, 13}) {
      ORACLE.write(mappings2Data, OracleMappings.PRICE_INFO_ACCOUNTS_OFFSET + index * PublicKey.PUBLIC_KEY_LENGTH);
      mappings2Data[OracleMappings.PRICE_TYPES_OFFSET + index] = (byte) OracleType.SwitchboardOnDemand.ordinal();
    }
  }

  private static byte[] reserveOn(final int chainIndex, final long collateral) {
    final var data = reserveFixture.clone();
    PRICES2_KEY.write(data, SCOPE_CONFIG_BASE + ScopeConfiguration.PRICE_FEED_OFFSET);
    final int chainOffset = SCOPE_CONFIG_BASE + ScopeConfiguration.PRICE_CHAIN_OFFSET;
    ByteUtil.putInt16LE(data, chainOffset, chainIndex);
    ByteUtil.putInt16LE(data, chainOffset + 2, 65_535);
    ByteUtil.putInt16LE(data, chainOffset + 4, 65_535);
    ByteUtil.putInt16LE(data, chainOffset + 6, 65_535);
    ByteUtil.putInt64LE(data, COLLATERAL_SUPPLY_OFFSET, collateral);
    return data;
  }

  private static byte[] feedlessReserve(final long collateral) {
    final var data = reserveOn(11, collateral);
    Arrays.fill(data,
        SCOPE_CONFIG_BASE + ScopeConfiguration.PRICE_FEED_OFFSET,
        SCOPE_CONFIG_BASE + ScopeConfiguration.PRICE_FEED_OFFSET + PublicKey.PUBLIC_KEY_LENGTH,
        (byte) 0);
    return data;
  }

  private static byte[] nilReserve(final long collateral) {
    final var data = reserveOn(11, collateral);
    KaminoAccounts.NULL_KEY.write(data, SCOPE_CONFIG_BASE + ScopeConfiguration.PRICE_FEED_OFFSET);
    return data;
  }

  private static AccountInfo<byte[]> accountInfo(final PublicKey pubKey, final long slot, final byte[] data) {
    return new AccountInfo<>(
        pubKey, new Context(slot, null), false, 0, KaminoAccounts.MAIN_NET.kLendProgram(),
        BigInteger.ZERO, 0, data
    );
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

  private static software.sava.services.solana.remote.call.RpcCaller callerFor(
      final software.sava.rpc.json.http.client.SolanaRpcClient client) {
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

  /// A client for the RPC-only init: program accounts routed by program, the
  /// mappings by getAccounts. Any list may be overridden per test.
  private static software.sava.rpc.json.http.client.SolanaRpcClient routedClient(
      final List<AccountInfo<byte[]>> configAccounts,
      final List<AccountInfo<byte[]>> reserveAccounts,
      final List<AccountInfo<byte[]>> vaultAccounts,
      final List<AccountInfo<byte[]>> mappingAccounts) {
    final var kaminoAccounts = KaminoAccounts.MAIN_NET;
    return (software.sava.rpc.json.http.client.SolanaRpcClient) java.lang.reflect.Proxy.newProxyInstance(
        software.sava.rpc.json.http.client.SolanaRpcClient.class.getClassLoader(),
        new Class<?>[]{software.sava.rpc.json.http.client.SolanaRpcClient.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getProgramAccounts" -> {
            final var request = (software.sava.rpc.json.http.client.ProgramAccountsRequest<?>) args[0];
            final var program = request.programId();
            if (program.equals(kaminoAccounts.kVaultsProgram())) {
              yield CompletableFuture.completedFuture(vaultAccounts);
            } else if (program.equals(kaminoAccounts.kLendProgram())) {
              yield CompletableFuture.completedFuture(reserveAccounts);
            } else if (program.equals(kaminoAccounts.scopePricesProgram())) {
              yield CompletableFuture.completedFuture(configAccounts);
            }
            throw new UnsupportedOperationException("getProgramAccounts for " + program);
          }
          case "getAccounts" -> CompletableFuture.completedFuture(mappingAccounts);
          default -> throw new UnsupportedOperationException(method.getName());
        }
    );
  }

  private static systems.glam.services.rpc.AccountFetcher recordingFetcher(
      final AtomicReference<Object> listened,
      final List<List<PublicKey>> batchQueued,
      final java.util.function.Function<List<PublicKey>, AccountResult> priorityResults) {
    return (systems.glam.services.rpc.AccountFetcher) java.lang.reflect.Proxy.newProxyInstance(
        systems.glam.services.rpc.AccountFetcher.class.getClassLoader(),
        new Class<?>[]{systems.glam.services.rpc.AccountFetcher.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "listenToAll" -> {
            listened.set(args[0]);
            yield null;
          }
          case "priorityQueueBatchable" -> {
            @SuppressWarnings("unchecked") final var keys = (List<PublicKey>) args[0];
            batchQueued.add(List.copyOf(keys));
            yield null;
          }
          case "priorityQueue" -> {
            @SuppressWarnings("unchecked") final var keys = (List<PublicKey>) args[0];
            yield CompletableFuture.completedFuture(priorityResults.apply(keys));
          }
          default -> throw new UnsupportedOperationException(method.getName());
        }
    );
  }

  private static List<AccountInfo<byte[]>> defaultInitAccounts(final String which) {
    return switch (which) {
      case "configs" -> {
        final var configs = new ArrayList<AccountInfo<byte[]>>(2);
        configs.add(null); // a null program-account entry is skipped, not parsed
        configs.add(accountInfo(CONFIG2_KEY, 100L,
            Arrays.copyOf(config2Data, KaminoCacheImpl.MIN_CONFIGURATION_LENGTH)));
        yield configs;
      }
      case "reserves" -> List.of(
          accountInfo(RESERVE_A_KEY, 100L, Arrays.copyOf(reserveOn(11, 1_000L), KaminoCacheImpl.MIN_RESERVE_LENGTH)),
          accountInfo(FEEDLESS_KEY, 100L, Arrays.copyOf(feedlessReserve(5L), KaminoCacheImpl.MIN_RESERVE_LENGTH)),
          accountInfo(NIL_KEY, 100L, Arrays.copyOf(nilReserve(7L), KaminoCacheImpl.MIN_RESERVE_LENGTH))
      );
      case "vaults" -> List.of(accountInfo(VAULT_KEY, 100L,
          Arrays.copyOf(vaultFixture, KaminoCacheImpl.MIN_VAULT_STATE_LENGTH)));
      case "mappings" -> List.of(accountInfo(MAPPINGS2_KEY, 100L, mappings2Data));
      default -> throw new IllegalArgumentException(which);
    };
  }

  @Test
  void theRpcOnlyInitKeepsFeedlessReservesAndWiresTheFeedMaps() {
    final var listened = new AtomicReference<>();
    final var fetcher = recordingFetcher(listened, new ArrayList<>(), keys -> null);
    final var cache = KaminoCache.initService(
        callerFor(routedClient(
            defaultInitAccounts("configs"),
            defaultInitAccounts("reserves"),
            defaultInitAccounts("vaults"),
            defaultInitAccounts("mappings")
        )),
        fetcher,
        KaminoAccounts.MAIN_NET,
        Duration.ofSeconds(1)
    ).join();

    // this init keeps only the reserves WITHOUT a price feed; feed-priced
    // reserves arrive later through the poll loop
    assertNull(cache.reserveContext(RESERVE_A_KEY));
    assertNotNull(cache.reserveContext(FEEDLESS_KEY));
    assertNotNull(cache.reserveContext(NIL_KEY));
    assertEquals(2, cache.reserveContexts().size());

    // the fetched feed is wired: the mappings serve direct oracle indexes
    final var feed = cache.indexes(key(99), ORACLE, OracleType.SwitchboardOnDemand);
    assertNotNull(feed);
    assertArrayEquals(new short[]{11, 12, 13, -1}, feed.indexes());
    assertEquals(BigInteger.ZERO, feed.liquidity());
    assertNull(cache.indexes(key(99), key(98), OracleType.SwitchboardOnDemand));

    final var vault = cache.vaultForShareMint(PublicKey.readPubKey(
        vaultFixture, software.sava.idl.clients.kamino.vaults.gen.types.VaultState.SHARES_MINT_OFFSET));
    assertNotNull(vault);
    assertEquals(1, cache.vaultContexts().size());

    // nothing on disk in this mode
    assertNull(cache.reserveDataFilePath());
    assertNull(cache.mappingsPath());
    assertNull(cache.configurationsPath());

    assertSame(cache, listened.get(), "the cache must register for account updates");
  }

  private static void assertInitFails(final String expectedMessage,
                                      final List<AccountInfo<byte[]>> configs,
                                      final List<AccountInfo<byte[]>> reserves,
                                      final List<AccountInfo<byte[]>> vaults,
                                      final List<AccountInfo<byte[]>> mappings) {
    final var fetcher = recordingFetcher(new AtomicReference<>(), new ArrayList<>(), keys -> null);
    final var future = KaminoCache.initService(
        callerFor(routedClient(configs, reserves, vaults, mappings)),
        fetcher,
        KaminoAccounts.MAIN_NET,
        Duration.ofSeconds(1)
    );
    final var failure = assertThrows(java.util.concurrent.CompletionException.class, future::join);
    assertTrue(failure.getCause().getMessage().contains(expectedMessage), failure.getCause().getMessage());
  }

  @Test
  void theRpcOnlyInitRejectsInvalidAccounts() {
    // a mis-sized configuration
    assertInitFails("is not a valid Scope Configuration account.",
        List.of(accountInfo(CONFIG2_KEY, 100L, config2Data)), // full length, not the slice
        defaultInitAccounts("reserves"), defaultInitAccounts("vaults"), defaultInitAccounts("mappings"));

    // a missing mappings account
    final var missingMappings = new ArrayList<AccountInfo<byte[]>>(1);
    missingMappings.add(null);
    assertInitFails("Oracle Mappings account not found.",
        defaultInitAccounts("configs"), defaultInitAccounts("reserves"),
        defaultInitAccounts("vaults"), missingMappings);

    // an invalid mappings account
    assertInitFails("is not a valid Scope OracleMappings account.",
        defaultInitAccounts("configs"), defaultInitAccounts("reserves"),
        defaultInitAccounts("vaults"),
        List.of(accountInfo(MAPPINGS2_KEY, 100L, new byte[OracleMappings.BYTES])));

    // an invalid vault account
    assertInitFails("is not a valid Kamino Vault account.",
        defaultInitAccounts("configs"), defaultInitAccounts("reserves"),
        List.of(accountInfo(VAULT_KEY, 100L, new byte[32])),
        defaultInitAccounts("mappings"));

    // a right-length configuration with the wrong discriminator
    assertInitFails("is not a valid Scope Configuration account.",
        List.of(accountInfo(CONFIG2_KEY, 100L, new byte[KaminoCacheImpl.MIN_CONFIGURATION_LENGTH])),
        defaultInitAccounts("reserves"), defaultInitAccounts("vaults"), defaultInitAccounts("mappings"));

    // mappings with a valid discriminator but the wrong length
    assertInitFails("is not a valid Scope OracleMappings account.",
        defaultInitAccounts("configs"), defaultInitAccounts("reserves"),
        defaultInitAccounts("vaults"),
        List.of(accountInfo(MAPPINGS2_KEY, 100L, Arrays.copyOf(mappings2Data, OracleMappings.BYTES - 8))));

    // a right-length vault with the wrong discriminator
    assertInitFails("is not a valid Kamino Vault account.",
        defaultInitAccounts("configs"), defaultInitAccounts("reserves"),
        List.of(accountInfo(VAULT_KEY, 100L, new byte[KaminoCacheImpl.MIN_VAULT_STATE_LENGTH])),
        defaultInitAccounts("mappings"));

    // a valid-discriminator vault at the wrong length
    assertInitFails("is not a valid Kamino Vault account.",
        defaultInitAccounts("configs"), defaultInitAccounts("reserves"),
        List.of(accountInfo(VAULT_KEY, 100L, Arrays.copyOf(vaultFixture, KaminoCacheImpl.MIN_VAULT_STATE_LENGTH - 1))),
        defaultInitAccounts("mappings"));
  }

  private record Recorded(String event, PublicKey key) {
  }

  private static final class RecordingListener implements KaminoListener {

    final PublicKey listenerKey = KaminoCachePollingTests.key(90);
    final List<Recorded> events = new CopyOnWriteArrayList<>();

    @Override
    public PublicKey key() {
      return listenerKey;
    }

    @Override
    public void onScopeAccountDeleted(final PublicKey deletedAccount, final ScopeFeedContext scopeFeedContext) {
      events.add(new Recorded("scopeDeleted", deletedAccount));
    }

    @Override
    public void onNewReserve(final ReserveContext reserveContext) {
      events.add(new Recorded("newReserve", reserveContext.pubKey()));
    }

    @Override
    public void onMappingChange(final ScopeFeedContext scopeFeedContext,
                                final systems.glam.services.oracles.scope.MappingsContext witness,
                                final systems.glam.services.oracles.scope.MappingsContext mappingContext) {
      events.add(new Recorded("mappingChange", scopeFeedContext.oracleMappings()));
    }

    @Override
    public void onNewKaminoVault(final KaminoVaultContext vaultContext) {
      events.add(new Recorded("newVault", vaultContext.sharesMint()));
    }

    @Override
    public void onReserveChange(final ReserveContext previous,
                                final ReserveContext reserveContext,
                                final Set<ReserveChange> changes) {
      events.add(new Recorded("reserveChange", reserveContext.pubKey()));
    }
  }

  /// A disk-backed cache (so deletions are observable as file removals) whose
  /// poll loop is driven by scripted rpc and fetcher responses.
  @Test
  void thePollLoopAppliesUpdatesAndDropsVanishedScopeAccounts(@TempDir final Path tempDir) throws Exception {
    final var kaminoAccounts = KaminoAccounts.MAIN_NET;
    final var configurationsPath = tempDir.resolve("scope").resolve("configurations");
    final var mappingsPath = tempDir.resolve("scope").resolve("mappings");
    try {
      Files.createDirectories(configurationsPath);
      Files.createDirectories(mappingsPath);
      Files.createDirectories(tempDir.resolve("reserves"));
      systems.glam.services.io.FileUtils.writeCompressedAccountData(configurationsPath, CONFIG2_KEY, config2Data);
      systems.glam.services.io.FileUtils.writeCompressedAccountData(mappingsPath, MAPPINGS2_KEY, mappings2Data);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    final var dropConfig = new AtomicBoolean(false);
    final var running = new AtomicBoolean(false);
    final var reserveData = reserveOn(11, 2_000L);
    final var updatedMappings = mappings2Data.clone();
    ORACLE.write(updatedMappings, OracleMappings.PRICE_INFO_ACCOUNTS_OFFSET + 14 * PublicKey.PUBLIC_KEY_LENGTH);
    updatedMappings[OracleMappings.PRICE_TYPES_OFFSET + 14] = (byte) OracleType.SwitchboardOnDemand.ordinal();
    final var client = (software.sava.rpc.json.http.client.SolanaRpcClient) java.lang.reflect.Proxy.newProxyInstance(
        software.sava.rpc.json.http.client.SolanaRpcClient.class.getClassLoader(),
        new Class<?>[]{software.sava.rpc.json.http.client.SolanaRpcClient.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getProgramAccounts" -> {
            final var request = (software.sava.rpc.json.http.client.ProgramAccountsRequest<?>) args[0];
            final var program = request.programId();
            if (program.equals(kaminoAccounts.kVaultsProgram())) {
              yield CompletableFuture.completedFuture(List.of());
            } else if (program.equals(kaminoAccounts.kLendProgram())) {
              // the reserve only exists once run() is polling: its arrival,
              // indexing and persistence are all attributable to the loop
              yield CompletableFuture.completedFuture(running.get()
                  ? List.of(accountInfo(RESERVE_A_KEY, 200L, reserveData))
                  : List.of());
            }
            throw new UnsupportedOperationException("getProgramAccounts for " + program);
          }
          default -> throw new UnsupportedOperationException(method.getName());
        }
    );
    final var requestedKeys = new CopyOnWriteArrayList<List<PublicKey>>();
    final var fetcher = recordingFetcher(new AtomicReference<>(), new ArrayList<>(), keys -> {
      requestedKeys.add(List.copyOf(keys));
      final var map = new HashMap<PublicKey, AccountInfo<byte[]>>(keys.size());
      for (final var requested : keys) {
        if (dropConfig.get()) {
          continue; // every scope account has vanished
        }
        if (requested.equals(CONFIG2_KEY)) {
          map.put(requested, accountInfo(CONFIG2_KEY, 150L, config2Data));
        } else if (requested.equals(MAPPINGS2_KEY)) {
          // an oracle entry was appended on chain: the poll must apply it
          map.put(requested, accountInfo(MAPPINGS2_KEY, 150L, updatedMappings));
        }
      }
      return new AccountResult(List.copyOf(map.values()), map);
    });

    final var cache = (KaminoCacheImpl) KaminoCache.initService(
        tempDir,
        callerFor(client),
        fetcher,
        kaminoAccounts,
        Duration.ofMillis(30)
    ).join();
    assertEquals(configurationsPath, cache.configurationsPath());
    assertEquals(mappingsPath, cache.mappingsPath());
    assertEquals(tempDir.resolve("reserves"), cache.reserveDataFilePath());

    final var listener = new RecordingListener();
    cache.subscribeToAll(listener);

    running.set(true);
    final var logs = LogCapture.attach(KaminoCache.class.getName());
    final var runner = new Thread(cache::run);
    runner.start();

    // the polled reserve joins the cache with its feed indexes and liquidity
    final long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
    while (cache.reserveContext(RESERVE_A_KEY) == null) {
      assertTrue(System.nanoTime() < deadline, "the poll loop never applied the reserve");
      //noinspection BusyWait
      Thread.sleep(1L);
    }
    final var reserve = cache.reserveContext(RESERVE_A_KEY);
    final var feed = cache.indexes(reserve.mint(), ORACLE, OracleType.SwitchboardOnDemand);
    assertNotNull(feed);
    assertEquals(BigInteger.valueOf(2_000L), feed.liquidity());
    assertTrue(listener.events.contains(new Recorded("newReserve", RESERVE_A_KEY)));
    // the fetched mappings update flowed through accept: the appended oracle
    // entry at chain index 14 is now served on the mappings fallback
    while (true) {
      final var fallback = cache.indexes(key(99), ORACLE, OracleType.SwitchboardOnDemand);
      if (fallback != null && fallback.indexes()[3] == 14) {
        break;
      }
      assertTrue(System.nanoTime() < deadline, "the polled mappings update was never applied");
      //noinspection BusyWait
      Thread.sleep(1L);
    }
    // the polled reserve was persisted under its market
    final var persistedReserve = tempDir.resolve("reserves")
        .resolve(reserve.market().toBase58())
        .resolve(RESERVE_A_KEY.toBase58() + ".dat.gz");
    final long persistDeadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
    while (!Files.exists(persistedReserve)) {
      assertTrue(System.nanoTime() < persistDeadline, "the polled reserve was never persisted");
      //noinspection BusyWait
      Thread.sleep(1L);
    }

    // the scope accounts vanish: the config is dropped with its files
    dropConfig.set(true);
    while (cache.indexes(reserve.mint(), ORACLE, OracleType.SwitchboardOnDemand) != null) {
      assertTrue(System.nanoTime() < deadline, "the vanished config was never dropped");
      //noinspection BusyWait
      Thread.sleep(1L);
    }
    assertTrue(listener.events.contains(new Recorded("scopeDeleted", CONFIG2_KEY)));
    // deletion must target the compressed names the cache persists, or the
    // dropped configuration would resurrect from disk on the next start
    while (Files.exists(configurationsPath.resolve(CONFIG2_KEY.toBase58() + ".dat.gz"))
        || Files.exists(mappingsPath.resolve(MAPPINGS2_KEY.toBase58() + ".dat.gz"))) {
      assertTrue(System.nanoTime() < deadline, "the persisted scope files were not deleted");
      //noinspection BusyWait
      Thread.sleep(1L);
    }
    // and once dropped, the accounts leave the poll set
    final int requestsAtDrop = requestedKeys.size();
    while (requestedKeys.size() == requestsAtDrop) {
      assertTrue(System.nanoTime() < deadline, "the poll loop stopped fetching");
      //noinspection BusyWait
      Thread.sleep(1L);
    }
    assertEquals(List.of(), requestedKeys.getLast(), "dropped scope accounts must leave the fetch list");

    // one of the two vanished accounts is always reported as a bare mappings
    // deletion: whichever is processed second finds its feed context gone
    logs.assertLogged("has been deleted");

    runner.interrupt();
    runner.join(5_000L);
    assertFalse(runner.isAlive());
    assertFalse(cache.lock.isWriteLocked(), "the poll loop must release the write lock on exit");
    assertEquals(0, cache.lock.getReadLockCount());
    logs.close();
  }

  @Test
  void aPollFailureIsLoggedAndEndsTheLoop() {
    final var throwingClient = (software.sava.rpc.json.http.client.SolanaRpcClient) java.lang.reflect.Proxy.newProxyInstance(
        software.sava.rpc.json.http.client.SolanaRpcClient.class.getClassLoader(),
        new Class<?>[]{software.sava.rpc.json.http.client.SolanaRpcClient.class},
        (proxy, method, args) -> {
          throw new IllegalStateException("rpc down");
        }
    );
    final var kaminoAccounts = KaminoAccounts.MAIN_NET;
    final var cache = new KaminoCacheImpl(
        callerFor(throwingClient), null,
        kaminoAccounts.kLendProgram(),
        kaminoAccounts.scopePricesProgram(),
        kaminoAccounts.kVaultsProgram(),
        null, null,
        Duration.ofMillis(10),
        null, null, null,
        Map.of(),
        new java.util.concurrent.ConcurrentHashMap<>(),
        new java.util.concurrent.ConcurrentHashMap<>(),
        new java.util.concurrent.ConcurrentHashMap<>()
    );
    try (final var logs = LogCapture.attach(KaminoCache.class.getName())) {
      cache.run();
      logs.assertLogged("Failed to poll Scope accounts.");
    }
  }

  @Test
  void refreshVaultsForgetsAndRequeuesTheStateAccounts() {
    final var listened = new AtomicReference<>();
    final var batchQueued = new ArrayList<List<PublicKey>>();
    final var fetcher = recordingFetcher(listened, batchQueued, keys -> null);
    final var cache = KaminoCache.initService(
        callerFor(routedClient(
            defaultInitAccounts("configs"),
            defaultInitAccounts("reserves"),
            defaultInitAccounts("vaults"),
            defaultInitAccounts("mappings")
        )),
        fetcher,
        KaminoAccounts.MAIN_NET,
        Duration.ofSeconds(1)
    ).join();

    final var sharesMint = PublicKey.readPubKey(
        vaultFixture, software.sava.idl.clients.kamino.vaults.gen.types.VaultState.SHARES_MINT_OFFSET);
    assertNotNull(cache.vaultForShareMint(sharesMint));

    cache.refreshVaults(Set.of(sharesMint, key(88)));
    assertNull(cache.vaultForShareMint(sharesMint), "a refreshed vault must be forgotten until refetched");
    // only the known vault's state account is requeued; the unknown mint is dropped
    assertEquals(1, batchQueued.size());
    assertEquals(List.of(VAULT_KEY), batchQueued.getFirst());
  }

  @Test
  void reserveListenersCanTargetASingleReserveAndUnsubscribe() {
    final var fetcher = recordingFetcher(new AtomicReference<>(), new ArrayList<>(), keys -> null);
    final var cache = KaminoCache.initService(
        callerFor(routedClient(
            defaultInitAccounts("configs"),
            defaultInitAccounts("reserves"),
            defaultInitAccounts("vaults"),
            defaultInitAccounts("mappings")
        )),
        fetcher,
        KaminoAccounts.MAIN_NET,
        Duration.ofSeconds(1)
    ).join();

    final var all = new RecordingListener();
    cache.subscribeToAll(all);
    // the feed-priced reserve was skipped at init: its first sighting is new,
    // and with no disk paths the persist must be a quiet no-op
    cache.acceptReserve(accountInfo(RESERVE_A_KEY, 210L, reserveOn(11, 3_000L)));
    assertTrue(all.events.contains(new Recorded("newReserve", RESERVE_A_KEY)));
    // a collateral-only change resorts quietly; a chain change notifies
    cache.acceptReserve(accountInfo(RESERVE_A_KEY, 211L, reserveOn(12, 3_000L)));
    assertTrue(all.events.contains(new Recorded("reserveChange", RESERVE_A_KEY)));

    // unsubscribed: further changes are silent
    all.events.clear();
    cache.unsubscribeFromAll(all);
    cache.acceptReserve(accountInfo(RESERVE_A_KEY, 212L, reserveOn(11, 3_000L)));
    assertEquals(List.of(), all.events);

    // a single-reserve subscription hears only its reserve
    final var specific = new RecordingListener();
    cache.subscribeToReserve(RESERVE_A_KEY, specific);
    cache.acceptReserve(accountInfo(RESERVE_A_KEY, 213L, reserveOn(12, 3_000L)));
    assertTrue(specific.events.contains(new Recorded("reserveChange", RESERVE_A_KEY)));

    specific.events.clear();
    cache.unSubscribeToReserve(RESERVE_A_KEY, specific);
    cache.acceptReserve(accountInfo(RESERVE_A_KEY, 214L, reserveOn(11, 3_000L)));
    assertEquals(List.of(), specific.events);

    // unsubscribing from a reserve nobody watches is a no-op, not a throw
    cache.unSubscribeToReserve(key(89), specific);
  }

  @Test
  void subscribeToAllCoversScopeAndVaultEventsToo() {
    final var fetcher = recordingFetcher(new AtomicReference<>(), new ArrayList<>(), keys -> null);
    final var cache = KaminoCache.initService(
        callerFor(routedClient(
            defaultInitAccounts("configs"),
            defaultInitAccounts("reserves"),
            defaultInitAccounts("vaults"),
            defaultInitAccounts("mappings")
        )),
        fetcher,
        KaminoAccounts.MAIN_NET,
        Duration.ofSeconds(1)
    ).join();
    final var impl = (KaminoCacheImpl) cache;

    final var all = new RecordingListener();
    cache.subscribeToAll(all);

    // a mappings change reaches the scope subscription
    final var changedMappings = mappings2Data.clone();
    ORACLE.write(changedMappings, OracleMappings.PRICE_INFO_ACCOUNTS_OFFSET + 14 * PublicKey.PUBLIC_KEY_LENGTH);
    changedMappings[OracleMappings.PRICE_TYPES_OFFSET + 14] = (byte) OracleType.SwitchboardOnDemand.ordinal();
    impl.accept(List.of(accountInfo(MAPPINGS2_KEY, 300L, changedMappings)), Map.of());
    assertTrue(all.events.contains(new Recorded("mappingChange", MAPPINGS2_KEY)), all.events::toString);

    // a new vault reaches the vault subscription
    final var newVaultData = vaultFixture.clone();
    final var newSharesMint = key(70);
    newSharesMint.write(newVaultData, software.sava.idl.clients.kamino.vaults.gen.types.VaultState.SHARES_MINT_OFFSET);
    impl.accept(List.of(accountInfo(key(71), 301L, newVaultData)), Map.of());
    assertTrue(all.events.contains(new Recorded("newVault", newSharesMint)), all.events::toString);

    // fully unsubscribed: scope and vault events go quiet as well
    all.events.clear();
    cache.unsubscribeFromAll(all);
    final var quietMappings = changedMappings.clone();
    ORACLE.write(quietMappings, OracleMappings.PRICE_INFO_ACCOUNTS_OFFSET + 15 * PublicKey.PUBLIC_KEY_LENGTH);
    quietMappings[OracleMappings.PRICE_TYPES_OFFSET + 15] = (byte) OracleType.SwitchboardOnDemand.ordinal();
    impl.accept(List.of(accountInfo(MAPPINGS2_KEY, 302L, quietMappings)), Map.of());
    final var anotherVault = vaultFixture.clone();
    key(72).write(anotherVault, software.sava.idl.clients.kamino.vaults.gen.types.VaultState.SHARES_MINT_OFFSET);
    impl.accept(List.of(accountInfo(key(73), 303L, anotherVault)), Map.of());
    assertEquals(List.of(), all.events);
  }
}
