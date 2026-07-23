package systems.glam.services.integrations.kamino;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.sava.core.accounts.PublicKey;
import software.sava.core.encoding.ByteUtil;
import software.sava.idl.clients.kamino.KaminoAccounts;
import software.sava.idl.clients.kamino.lend.gen.types.Reserve;
import software.sava.idl.clients.kamino.lend.gen.types.ReserveCollateral;
import software.sava.idl.clients.kamino.lend.gen.types.ReserveConfig;
import software.sava.idl.clients.kamino.lend.gen.types.TokenInfo;
import software.sava.idl.clients.kamino.lend.gen.types.ScopeConfiguration;
import software.sava.idl.clients.kamino.scope.gen.types.Configuration;
import software.sava.idl.clients.kamino.scope.gen.types.OracleMappings;
import software.sava.idl.clients.kamino.scope.gen.types.OracleType;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.Context;
import systems.glam.services.tests.ResourceUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;

/// The mainnet fixture's SOL chain is a composite (MostRecentOf) head, so the
/// feed-indexed paths always fall back to the raw mappings scan and the feed's
/// reserve maps stay unobservable. This suite synthesizes the long-documented
/// escape: a second scope feed whose chain heads with a DIRECT oracle entry —
/// a zero-filled Configuration/OracleMappings pair with real discriminators,
/// plus the real SOL Reserve re-pointed at it by byte surgery. The feed path
/// is distinguishable from the fallback by liquidity: the fallback always
/// reports BigInteger.ZERO, the feed path sums reserve collateral.
final class KaminoCacheDirectFeedTests {

  private static final PublicKey SOL_RESERVE_KEY = fromBase58Encoded("d4A2prbA2whesmvHaL88BH6Ewn5N4bTSU2Ze8P6Bc4Q");

  private static final PublicKey CONFIG2_KEY = key(41);
  private static final PublicKey MAPPINGS2_KEY = key(42);
  private static final PublicKey PRICES2_KEY = key(43);
  private static final PublicKey ORACLE = key(44);
  private static final PublicKey RESERVE_A_KEY = key(45);
  private static final PublicKey RESERVE_B_KEY = key(46);

  private static final int SCOPE_CONFIG_BASE =
      Reserve.CONFIG_OFFSET + ReserveConfig.TOKEN_INFO_OFFSET + TokenInfo.SCOPE_CONFIGURATION_OFFSET;
  private static final int COLLATERAL_SUPPLY_OFFSET =
      Reserve.COLLATERAL_OFFSET + ReserveCollateral.MINT_TOTAL_SUPPLY_OFFSET;

  private static byte[] reserveFixture;
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

    config2Data = new byte[Configuration.BYTES];
    System.arraycopy(Configuration.DISCRIMINATOR.data(), 0, config2Data, 0, 8);
    MAPPINGS2_KEY.write(config2Data, Configuration.ORACLE_MAPPINGS_OFFSET);
    PRICES2_KEY.write(config2Data, Configuration.ORACLE_PRICES_OFFSET);

    mappings2Data = new byte[OracleMappings.BYTES];
    System.arraycopy(OracleMappings.DISCRIMINATOR.data(), 0, mappings2Data, 0, 8);
    // direct oracle entries at chain indexes 11, 12 and 13
    for (final int index : new int[]{11, 12, 13}) {
      ORACLE.write(mappings2Data, OracleMappings.PRICE_INFO_ACCOUNTS_OFFSET + index * PublicKey.PUBLIC_KEY_LENGTH);
      mappings2Data[OracleMappings.PRICE_TYPES_OFFSET + index] = (byte) OracleType.SwitchboardOnDemand.ordinal();
    }
  }

  /// The real SOL reserve, re-pointed at the synthetic feed with a direct
  /// single-entry chain and an explicit collateral supply.
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

  private static AccountInfo<byte[]> accountInfo(final PublicKey pubKey, final long slot, final byte[] data) {
    return new AccountInfo<>(
        pubKey, new Context(slot, null), false, 0, KaminoAccounts.MAIN_NET.kLendProgram(),
        BigInteger.ZERO, 0, data
    );
  }

  private static KaminoCacheImpl createCache(final Path tempDir) {
    try {
      Files.createDirectories(tempDir.resolve("configurations"));
      Files.createDirectories(tempDir.resolve("mappings"));
      Files.createDirectories(tempDir.resolve("reserves"));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    final var kaminoAccounts = KaminoAccounts.MAIN_NET;
    return new KaminoCacheImpl(
        null, null,
        kaminoAccounts.kLendProgram(),
        kaminoAccounts.scopePricesProgram(),
        kaminoAccounts.kVaultsProgram(),
        null, null,
        Duration.ofSeconds(1),
        tempDir.resolve("configurations"),
        tempDir.resolve("mappings"),
        tempDir.resolve("reserves"),
        Map.of(),
        new ConcurrentHashMap<>(),
        new ConcurrentHashMap<>(),
        new ConcurrentHashMap<>()
    );
  }

  @Test
  void aDirectChainFeedServesIndexesFromItsReserves(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);
    cache.accept(accountInfo(CONFIG2_KEY, 100L, config2Data));
    cache.accept(accountInfo(MAPPINGS2_KEY, 100L, mappings2Data));
    cache.accept(accountInfo(RESERVE_A_KEY, 100L, reserveOn(11, 1_000L)));
    final var reserveA = cache.reserveContext(RESERVE_A_KEY);
    assertNotNull(reserveA);
    final var mint = reserveA.mint();

    // served through the FEED's reserve maps: liquidity is the reserve's
    // collateral, where the raw-mappings fallback always reports zero
    var feed = cache.indexes(mint, ORACLE, OracleType.SwitchboardOnDemand);
    assertNotNull(feed);
    assertArrayEquals(new short[]{11, -1, -1, -1}, feed.indexes());
    assertEquals(BigInteger.valueOf(1_000L), feed.liquidity());

    // a second, deeper reserve on the same feed must be indexed on arrival
    // and served first
    cache.accept(accountInfo(RESERVE_B_KEY, 100L, reserveOn(12, 5_000L)));
    feed = cache.indexes(mint, ORACLE, OracleType.SwitchboardOnDemand);
    assertNotNull(feed);
    assertArrayEquals(new short[]{12, 11, -1, -1}, feed.indexes());
    assertEquals(BigInteger.valueOf(6_000L), feed.liquidity());
    assertUnlocked(cache);
  }

  @Test
  void reserveUpdatesFlowThroughToTheFeedMaps(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);
    cache.accept(accountInfo(CONFIG2_KEY, 100L, config2Data));
    cache.accept(accountInfo(MAPPINGS2_KEY, 100L, mappings2Data));
    cache.accept(accountInfo(RESERVE_A_KEY, 100L, reserveOn(11, 1_000L)));
    cache.accept(accountInfo(RESERVE_B_KEY, 100L, reserveOn(12, 5_000L)));
    final var mint = cache.reserveContext(RESERVE_A_KEY).mint();

    // a collateral-only update must re-sort AND re-source the feed's by-mint
    // entry: a stale context would keep reporting the old collateral
    cache.accept(accountInfo(RESERVE_A_KEY, 101L, reserveOn(11, 9_000L)));
    var feed = cache.indexes(mint, ORACLE, OracleType.SwitchboardOnDemand);
    assertNotNull(feed);
    assertArrayEquals(new short[]{11, 12, -1, -1}, feed.indexes());
    assertEquals(BigInteger.valueOf(14_000L), feed.liquidity());

    // a structural update (the chain moves 11 -> 13) must replace the feed's
    // by-mint entry: a stale context would keep serving chain index 11
    cache.accept(accountInfo(RESERVE_A_KEY, 102L, reserveOn(13, 9_000L)));
    feed = cache.indexes(mint, ORACLE, OracleType.SwitchboardOnDemand);
    assertNotNull(feed);
    assertArrayEquals(new short[]{13, 12, -1, -1}, feed.indexes());
    assertEquals(BigInteger.valueOf(14_000L), feed.liquidity());
    assertUnlocked(cache);
  }

  private static void assertUnlocked(final KaminoCacheImpl cache) {
    assertFalse(cache.lock.isWriteLocked());
    assertEquals(0, cache.lock.getReadLockCount());
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
      final java.util.List<AccountInfo<byte[]>> programAccounts) {
    return rpcCaller(programAccounts, new java.util.ArrayList<>());
  }

  private static software.sava.services.solana.remote.call.RpcCaller rpcCaller(
      final java.util.List<AccountInfo<byte[]>> programAccounts,
      final java.util.List<software.sava.rpc.json.http.client.ProgramAccountsRequest<?>> requests) {
    final var client = (software.sava.rpc.json.http.client.SolanaRpcClient) java.lang.reflect.Proxy.newProxyInstance(
        software.sava.rpc.json.http.client.SolanaRpcClient.class.getClassLoader(),
        new Class<?>[]{software.sava.rpc.json.http.client.SolanaRpcClient.class},
        (proxy, method, args) -> {
          if (method.getName().equals("getProgramAccounts")) {
            requests.add((software.sava.rpc.json.http.client.ProgramAccountsRequest<?>) args[0]);
            return java.util.concurrent.CompletableFuture.completedFuture(programAccounts);
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
  void initServiceRestoresThePersistedFeedAndReserves(@TempDir final Path tempDir) throws IOException {
    // seed the on-disk layout initService expects: reserves/, scope/configurations/,
    // scope/mappings/ — the synthetic feed and the re-pointed SOL reserve
    final var configurationsPath = tempDir.resolve("scope").resolve("configurations");
    final var mappingsPath = tempDir.resolve("scope").resolve("mappings");
    final var reservesPath = tempDir.resolve("reserves");
    Files.createDirectories(configurationsPath);
    Files.createDirectories(mappingsPath);
    Files.createDirectories(reservesPath);
    // the config is seeded UNCOMPRESSED, the legacy layout the loader must
    // migrate: after init it exists compressed and the legacy file is gone
    Files.write(configurationsPath.resolve(CONFIG2_KEY.toBase58() + ".dat"), config2Data);
    Files.write(mappingsPath.resolve(MAPPINGS2_KEY.toBase58() + ".dat"), mappings2Data);
    final var reserveContext = ReserveContext.createContext(
        accountInfo(RESERVE_A_KEY, 100L, reserveOn(11, 1_000L)), Map.of());
    final var marketPath = reservesPath.resolve(reserveContext.market().toBase58());
    Files.createDirectories(marketPath);
    Files.write(marketPath.resolve(RESERVE_A_KEY.toBase58() + ".dat"), reserveContext.data());
    // corrupted junk beside the good files: loaded past, never fatal
    Files.write(configurationsPath.resolve(key(90).toBase58() + ".dat"), new byte[]{1, 2, 3});
    systems.glam.services.io.FileUtils.writeCompressedAccountData(mappingsPath, key(91), new byte[]{1, 2, 3});
    systems.glam.services.io.FileUtils.writeCompressedAccountData(
        reservesPath.resolve(reserveContext.market().toBase58()), key(92), new byte[]{1, 2, 3});

    // the only network the warm start needs is the vault state scan
    final var vaultStateData = ResourceUtil.readResource(
        "accounts/kamino/5YxwKgsvyTdT8q2CBgwA4L9BKbnKNrB66K9wUzij5wH.dat.gz");
    final var vaultKey = fromBase58Encoded("5YxwKgsvyTdT8q2CBgwA4L9BKbnKNrB66K9wUzij5wH");
    final var listening = new java.util.ArrayList<Object>();
    final var fetcher = (systems.glam.services.rpc.AccountFetcher) java.lang.reflect.Proxy.newProxyInstance(
        systems.glam.services.rpc.AccountFetcher.class.getClassLoader(),
        new Class<?>[]{systems.glam.services.rpc.AccountFetcher.class},
        (proxy, method, args) -> {
          if (method.getName().equals("listenToAll")) {
            listening.add(args[0]);
            return null;
          }
          throw new UnsupportedOperationException(method.getName());
        }
    );

    // the vault scan requests a data slice: serve the account truncated the
    // way the RPC would
    final var slicedVaultState = Arrays.copyOf(vaultStateData, KaminoCacheImpl.MIN_VAULT_STATE_LENGTH);
    final var requests = new java.util.ArrayList<software.sava.rpc.json.http.client.ProgramAccountsRequest<?>>();
    final var cache = KaminoCache.initService(
        tempDir,
        rpcCaller(java.util.List.of(accountInfo(vaultKey, 100L, slicedVaultState)), requests),
        fetcher,
        KaminoAccounts.MAIN_NET,
        Duration.ofSeconds(1)
    ).join();

    // full persistence: the only fetch is the vault scan, sliced and filtered
    assertEquals(1, requests.size());
    final var vaultRequest = requests.getFirst();
    assertEquals(KaminoAccounts.MAIN_NET.kVaultsProgram(), vaultRequest.programId());
    assertEquals(KaminoCacheImpl.MIN_VAULT_STATE_LENGTH, vaultRequest.dataSliceLength());
    assertFalse(vaultRequest.filters().isEmpty());

    // the reserve came back from disk, wired to the persisted mappings
    final var restored = cache.reserveContext(RESERVE_A_KEY);
    assertNotNull(restored, "the persisted reserve was not restored");
    final var mint = restored.mint();
    // and the whole feed-indexed path works from the persisted pair alone
    final var feed = cache.indexes(mint, ORACLE, OracleType.SwitchboardOnDemand);
    assertNotNull(feed, "the persisted feed was not restored");
    assertArrayEquals(new short[]{11, -1, -1, -1}, feed.indexes());
    assertEquals(BigInteger.valueOf(1_000L), feed.liquidity());

    // the vault scan populated the vault map
    final var sharesMint = PublicKey.readPubKey(vaultStateData, software.sava.idl.clients.kamino.vaults.gen.types.VaultState.SHARES_MINT_OFFSET);
    assertNotNull(cache.vaultForShareMint(sharesMint));

    // the cache registered itself for account updates
    assertEquals(java.util.List.of(cache), listening);

    // the legacy uncompressed config was migrated -- content intact, not just
    // an empty file at the right name -- and the legacy file removed
    assertArrayEquals(config2Data,
        systems.glam.services.io.FileUtils.readAccountData(
            configurationsPath.resolve(CONFIG2_KEY.toBase58() + ".dat.gz")).data(),
        "the migrated config does not round-trip");
    assertFalse(Files.exists(configurationsPath.resolve(CONFIG2_KEY.toBase58() + ".dat")),
        "the legacy config file was not removed");
    assertFalse(Files.exists(configurationsPath.resolve(key(90).toBase58() + ".dat")),
        "the corrupted config file was not deleted");
    // the legacy mappings and reserve files were migrated to compressed form too
    assertArrayEquals(mappings2Data,
        systems.glam.services.io.FileUtils.readAccountData(
            mappingsPath.resolve(MAPPINGS2_KEY.toBase58() + ".dat.gz")).data(),
        "the migrated mappings do not round-trip");
    assertFalse(Files.exists(mappingsPath.resolve(MAPPINGS2_KEY.toBase58() + ".dat")));
    assertTrue(Files.exists(marketPath.resolve(RESERVE_A_KEY.toBase58() + ".dat.gz")),
        "the migrated reserve was not compressed");
    assertFalse(Files.exists(marketPath.resolve(RESERVE_A_KEY.toBase58() + ".dat")));
    // junk mappings and reserves were passed over, not fatal
    assertNull(cache.reserveContext(key(92)));
  }

  @Test
  void aColdStartFetchesEverythingAndPersistsIt(@TempDir final Path tempDir) {
    // nothing on disk: reserves, configurations and mappings all come from RPC,
    // routed by the program each request targets
    final var configurationsPath = tempDir.resolve("scope").resolve("configurations");
    final var mappingsPath = tempDir.resolve("scope").resolve("mappings");
    final var reservesPath = tempDir.resolve("reserves");

    final var kaminoAccounts = KaminoAccounts.MAIN_NET;
    final var vaultStateData = Arrays.copyOf(
        readVaultFixture(), KaminoCacheImpl.MIN_VAULT_STATE_LENGTH);
    final var vaultKey = fromBase58Encoded("5YxwKgsvyTdT8q2CBgwA4L9BKbnKNrB66K9wUzij5wH");
    // one feed-priced reserve, and one with no price feed at all (the NONE-key
    // fast path that skips the feed machinery)
    final var pricedReserve = Arrays.copyOf(reserveOn(11, 1_000L), KaminoCacheImpl.MIN_RESERVE_LENGTH);
    final var feedlessData = reserveOn(11, 5L);
    java.util.Arrays.fill(feedlessData,
        SCOPE_CONFIG_BASE + ScopeConfiguration.PRICE_FEED_OFFSET,
        SCOPE_CONFIG_BASE + ScopeConfiguration.PRICE_FEED_OFFSET + PublicKey.PUBLIC_KEY_LENGTH,
        (byte) 0);
    final var feedlessReserve = Arrays.copyOf(feedlessData, KaminoCacheImpl.MIN_RESERVE_LENGTH);
    final var feedlessKey = key(47);
    // the sentinel "nu11..." price feed is the other no-feed spelling
    final var nilData = reserveOn(11, 7L);
    KaminoAccounts.NULL_KEY.write(nilData, SCOPE_CONFIG_BASE + ScopeConfiguration.PRICE_FEED_OFFSET);
    final var nilReserve = Arrays.copyOf(nilData, KaminoCacheImpl.MIN_RESERVE_LENGTH);
    final var nilKey = key(48);

    final var client = (software.sava.rpc.json.http.client.SolanaRpcClient) java.lang.reflect.Proxy.newProxyInstance(
        software.sava.rpc.json.http.client.SolanaRpcClient.class.getClassLoader(),
        new Class<?>[]{software.sava.rpc.json.http.client.SolanaRpcClient.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "getProgramAccounts" -> {
            final var request = (software.sava.rpc.json.http.client.ProgramAccountsRequest<?>) args[0];
            final var program = request.programId();
            if (program.equals(kaminoAccounts.kVaultsProgram())) {
              yield java.util.concurrent.CompletableFuture.completedFuture(
                  java.util.List.of(accountInfo(vaultKey, 100L, vaultStateData)));
            } else if (program.equals(kaminoAccounts.kLendProgram())) {
              assertEquals(KaminoCacheImpl.MIN_RESERVE_LENGTH, request.dataSliceLength());
              assertFalse(request.filters().isEmpty());
              yield java.util.concurrent.CompletableFuture.completedFuture(java.util.List.of(
                  accountInfo(RESERVE_A_KEY, 100L, pricedReserve),
                  accountInfo(feedlessKey, 100L, feedlessReserve),
                  accountInfo(nilKey, 100L, nilReserve)));
            } else if (program.equals(kaminoAccounts.scopePricesProgram())) {
              assertEquals(KaminoCacheImpl.MIN_CONFIGURATION_LENGTH, request.dataSliceLength());
              assertFalse(request.filters().isEmpty());
              yield java.util.concurrent.CompletableFuture.completedFuture(java.util.List.of(
                  accountInfo(CONFIG2_KEY, 100L,
                      Arrays.copyOf(config2Data, KaminoCacheImpl.MIN_CONFIGURATION_LENGTH))));
            }
            throw new UnsupportedOperationException("getProgramAccounts for " + program);
          }
          case "getAccounts" -> {
            @SuppressWarnings("unchecked") final var keys = (java.util.List<PublicKey>) args[0];
            assertEquals(java.util.List.of(MAPPINGS2_KEY), keys);
            yield java.util.concurrent.CompletableFuture.completedFuture(
                java.util.List.of(accountInfo(MAPPINGS2_KEY, 100L, mappings2Data)));
          }
          default -> throw new UnsupportedOperationException(method.getName());
        }
    );
    final var fetcher = (systems.glam.services.rpc.AccountFetcher) java.lang.reflect.Proxy.newProxyInstance(
        systems.glam.services.rpc.AccountFetcher.class.getClassLoader(),
        new Class<?>[]{systems.glam.services.rpc.AccountFetcher.class},
        (proxy, method, args) -> method.getName().equals("listenToAll") ? null
            : Void.class.cast(new UnsupportedOperationException(method.getName()))
    );

    final var cache = KaminoCache.initService(
        tempDir,
        callerFor(client),
        fetcher,
        kaminoAccounts,
        Duration.ofSeconds(1)
    ).join();

    // both reserves landed: the feed-priced one wired to the fetched mappings,
    // the feedless one via the fast path
    final var priced = cache.reserveContext(RESERVE_A_KEY);
    assertNotNull(priced);
    assertNotNull(cache.reserveContext(feedlessKey));
    assertNotNull(cache.reserveContext(nilKey));
    final var feed = cache.indexes(priced.mint(), ORACLE, OracleType.SwitchboardOnDemand);
    assertNotNull(feed, "the fetched feed was not wired");
    assertArrayEquals(new short[]{11, -1, -1, -1}, feed.indexes());
    assertEquals(BigInteger.valueOf(1_000L), feed.liquidity());
    assertNotNull(cache.vaultForShareMint(
        PublicKey.readPubKey(vaultStateData, software.sava.idl.clients.kamino.vaults.gen.types.VaultState.SHARES_MINT_OFFSET)));

    // everything fetched was persisted for the next start
    assertTrue(Files.exists(configurationsPath.resolve(CONFIG2_KEY.toBase58() + ".dat.gz")),
        "the fetched configuration was not persisted");
    assertTrue(Files.exists(mappingsPath.resolve(MAPPINGS2_KEY.toBase58() + ".dat.gz")),
        "the fetched mappings were not persisted");
    assertTrue(Files.exists(reservesPath.resolve(priced.market().toBase58())
            .resolve(RESERVE_A_KEY.toBase58() + ".dat.gz")),
        "the fetched reserve was not persisted");
  }

  private static byte[] readVaultFixture() {
    try {
      return ResourceUtil.readResource("accounts/kamino/5YxwKgsvyTdT8q2CBgwA4L9BKbnKNrB66K9wUzij5wH.dat.gz");
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
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

  @Test
  void anInvalidVaultAccountFailsTheInit(@TempDir final Path tempDir) throws IOException {
    Files.createDirectories(tempDir.resolve("scope").resolve("configurations"));
    Files.createDirectories(tempDir.resolve("scope").resolve("mappings"));
    Files.createDirectories(tempDir.resolve("reserves"));
    systems.glam.services.io.FileUtils.writeCompressedAccountData(
        tempDir.resolve("scope").resolve("configurations"), CONFIG2_KEY, config2Data);
    systems.glam.services.io.FileUtils.writeCompressedAccountData(
        tempDir.resolve("scope").resolve("mappings"), MAPPINGS2_KEY, mappings2Data);

    final var fetcher = (systems.glam.services.rpc.AccountFetcher) java.lang.reflect.Proxy.newProxyInstance(
        systems.glam.services.rpc.AccountFetcher.class.getClassLoader(),
        new Class<?>[]{systems.glam.services.rpc.AccountFetcher.class},
        (proxy, method, args) -> null
    );
    final var future = KaminoCache.initService(
        tempDir,
        rpcCaller(java.util.List.of(accountInfo(key(93), 100L, new byte[16]))),
        fetcher,
        KaminoAccounts.MAIN_NET,
        Duration.ofSeconds(1)
    );
    final var failure = assertThrows(java.util.concurrent.CompletionException.class, future::join);
    assertTrue(failure.getCause().getMessage().contains("not a valid Kamino Vault"),
        failure.getCause().getMessage());
  }
}
