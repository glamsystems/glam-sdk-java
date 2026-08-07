package systems.glam.services.integrations.kamino;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.kamino.KaminoAccounts;
import software.sava.idl.clients.kamino.scope.entries.OracleEntry;
import software.sava.idl.clients.kamino.scope.gen.types.OracleType;
import software.sava.idl.clients.kamino.vaults.gen.types.VaultState;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.Context;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import software.sava.idl.clients.kamino.scope.gen.types.Configuration;
import software.sava.idl.clients.kamino.scope.gen.types.OraclePrices;
import systems.glam.services.oracles.scope.MappingsContext;
import systems.glam.services.oracles.scope.ScopeFeedContext;
import systems.glam.services.tests.LogCapture;
import systems.glam.services.tests.ResourceUtil;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;

/// Mainnet snapshots (2026-07-21) drive the full accept pipeline without RPC:
/// the klend scope feed's Configuration + OracleMappings, the main-market SOL
/// Reserve, and one KVault state.
final class KaminoCacheTests {

  private static final PublicKey CONFIGURATION_KEY = fromBase58Encoded("6cMwdbrJ95D7v5655Zsoe7oXmjQJMnagWK8EcdG6qmGM");
  private static final PublicKey ORACLE_MAPPINGS_KEY = fromBase58Encoded("4zh6bmb77qX2CL7t5AJYCqa6YqFafbz3QJNeFvZjLowg");
  private static final PublicKey PRICE_FEED_KEY = fromBase58Encoded("3t4JZcueEzTbVP6kLxXrL3VpWx45jDer4eqysweBchNH");
  private static final PublicKey SOL_RESERVE_KEY = fromBase58Encoded("d4A2prbA2whesmvHaL88BH6Ewn5N4bTSU2Ze8P6Bc4Q");
  private static final PublicKey MAIN_MARKET_KEY = fromBase58Encoded("7u3HeHxYDLhnCoErrtycNokbQYbWGzLs6JSDqGAv5PfF");
  private static final PublicKey VAULT_STATE_KEY = fromBase58Encoded("5YxwKgsvyTdT8q2CBgwA4L9BKbnKNrB66K9wUzij5wH");
  private static final PublicKey SOL_MINT = fromBase58Encoded("So11111111111111111111111111111111111111112");

  private static byte[] configurationData;
  private static byte[] mappingsData;
  private static byte[] reserveData;
  private static byte[] vaultStateData;

  @BeforeAll
  static void beforeAll() throws IOException {
    configurationData = ResourceUtil.readResource("accounts/kamino/" + CONFIGURATION_KEY + ".dat.gz");
    mappingsData = ResourceUtil.readResource("accounts/kamino/" + ORACLE_MAPPINGS_KEY + ".dat.gz");
    reserveData = ResourceUtil.readResource("accounts/kamino/" + SOL_RESERVE_KEY + ".dat.gz");
    vaultStateData = ResourceUtil.readResource("accounts/kamino/" + VAULT_STATE_KEY + ".dat.gz");
  }

  private static AccountInfo<byte[]> accountInfo(final PublicKey key, final long slot, final byte[] data) {
    return new AccountInfo<>(
        key, new Context(slot, null), false, 0, KaminoAccounts.MAIN_NET.kLendProgram(),
        BigInteger.ZERO, 0, data
    );
  }

  private static KaminoCacheImpl createCache(final Path tempDir) {
    // production initService creates the persistence directories; constructing
    // the impl directly must do the same, or every persist quietly fails into
    // a WARN and the persistence paths become untestable
    try {
      java.nio.file.Files.createDirectories(tempDir.resolve("configurations"));
      java.nio.file.Files.createDirectories(tempDir.resolve("mappings"));
      java.nio.file.Files.createDirectories(tempDir.resolve("reserves"));
    } catch (final java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
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

  /// Every entry point takes a read or write lock in a try/finally. A leaked
  /// lock blocks every other caller and no result assertion can see it.
  private static void assertUnlocked(final KaminoCacheImpl cache) {
    assertFalse(cache.lock.isWriteLocked());
    assertEquals(0, cache.lock.getReadLockCount());
  }

  private record RecordingListener(PublicKey key, List<String> events) implements KaminoListener {

    RecordingListener(final int id) {
      this(PublicKey.createPubKey(new byte[]{(byte) id, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
          0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) id}), new ArrayList<>());
    }

    @Override
    public void onNewScopeConfiguration(final PublicKey newAccount, final ScopeFeedContext scopeFeedContext) {
      events.add("onNewScopeConfiguration");
    }

    @Override
    public void onNewReserve(final ReserveContext reserveContext) {
      events.add("onNewReserve");
    }

    @Override
    public void onNewKaminoVault(final KaminoVaultContext vaultContext) {
      events.add("onNewKaminoVault");
    }

    @Override
    public void onScopeConfigurationChange(final ScopeFeedContext witness, final ScopeFeedContext latest) {
      events.add("onScopeConfigurationChange");
    }

    @Override
    public void onKaminoVaultChange(final KaminoVaultContext previous, final KaminoVaultContext vaultContext) {
      events.add("onKaminoVaultChange");
    }

    @Override
    public void onMappingChange(final ScopeFeedContext scopeFeedContext,
                                final MappingsContext previous,
                                final MappingsContext latest) {
      events.add("onMappingChange");
    }

    @Override
    public void onReserveChange(final ReserveContext previous,
                                final ReserveContext reserveContext,
                                final java.util.Set<ReserveChange> changes) {
      events.add("onReserveChange");
    }

    @Override
    public void onReserveUpdate(final AccountInfo<byte[]> accountInfo) {
      events.add("onReserveUpdate");
    }

    @Override
    public void onOraclePricesUpdate(final AccountInfo<byte[]> accountInfo) {
      events.add("onOraclePricesUpdate");
    }

    long updates() {
      return events.stream().filter("onReserveUpdate"::equals).count();
    }
  }

  /// `onReserveUpdate` reports raw account activity, so its contract is one
  /// call per *observed* Reserve write on every ingestion path, before any
  /// change filtering. `onReserveChange` only fires for the fields
  /// `ReserveChange` tracks, so a reserve republished with a fresh price but no
  /// tracked change never reaches it — closing that gap is why the raw hook
  /// exists, and a hook that inherited the filtering would be worthless for it.
  @Test
  void everyObservedReserveWriteIsReportedBeforeChangeFiltering(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);
    final var allReserves = new RecordingListener(4);
    final var thisReserve = new RecordingListener(5);
    cache.subscribeToReserves(allReserves);
    cache.subscribeToReserve(SOL_RESERVE_KEY, thisReserve);

    // no scope feed is indexed yet, so the cache drops this write without
    // storing it — the raw hook must still report that it was observed
    cache.accept(accountInfo(SOL_RESERVE_KEY, 100L, reserveData));
    assertNull(cache.reserveContext(SOL_RESERVE_KEY), "the feed is unknown, nothing may be cached yet");
    assertEquals(List.of("onReserveUpdate"), allReserves.events());
    assertEquals(List.of("onReserveUpdate"), thisReserve.events(), "a reserve-specific listener sees it too");
    assertUnlocked(cache);

    cache.accept(accountInfo(CONFIGURATION_KEY, 101L, configurationData));
    cache.accept(accountInfo(ORACLE_MAPPINGS_KEY, 102L, mappingsData));

    // now the feed exists: the same write both reports and lands
    cache.accept(accountInfo(SOL_RESERVE_KEY, 103L, reserveData));
    assertNotNull(cache.reserveContext(SOL_RESERVE_KEY));
    assertEquals(List.of("onReserveUpdate", "onReserveUpdate", "onNewReserve"), allReserves.events(),
        "the raw hook precedes the filtered event for one write");

    // a republish with no tracked change: invisible to onReserveChange, which
    // is exactly the activity onReserveUpdate exists to expose
    cache.accept(accountInfo(SOL_RESERVE_KEY, 104L, reserveData));
    assertEquals(3, allReserves.updates());
    assertFalse(allReserves.events().contains("onReserveChange"),
        "an otherwise-unchanged refresh must not be reported as a change");

    // the remaining ingestion paths report the same way
    cache.acceptReserve(accountInfo(SOL_RESERVE_KEY, 105L, reserveData));
    assertEquals(4, allReserves.updates(), "acceptReserve is an observed write");

    cache.accept(List.of(accountInfo(SOL_RESERVE_KEY, 106L, reserveData)), Map.of());
    assertEquals(5, allReserves.updates(), "the batch path is an observed write");

    assertEquals(5, thisReserve.updates(), "every path reaches the reserve-specific listener");
    assertUnlocked(cache);

    // an unsubscribed listener stops receiving raw activity
    cache.unSubscribeToReserve(SOL_RESERVE_KEY, thisReserve);
    cache.accept(accountInfo(SOL_RESERVE_KEY, 107L, reserveData));
    assertEquals(6, allReserves.updates());
    assertEquals(5, thisReserve.updates());
    assertUnlocked(cache);
  }

  @Test
  void acceptBuildsTheFeedReserveAndVaultState(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);
    final var scopeListener = new RecordingListener(1);
    final var reserveListener = new RecordingListener(2);
    final var vaultListener = new RecordingListener(3);
    cache.subscribeToScope(scopeListener);
    cache.subscribeToReserves(reserveListener);
    cache.subscribeToVaults(vaultListener);

    cache.accept(accountInfo(CONFIGURATION_KEY, 100L, configurationData));
    assertEquals(List.of("onNewScopeConfiguration"), scopeListener.events());

    cache.accept(accountInfo(ORACLE_MAPPINGS_KEY, 101L, mappingsData));
    cache.accept(accountInfo(SOL_RESERVE_KEY, 102L, reserveData));

    final var reserveContext = cache.reserveContext(SOL_RESERVE_KEY);
    assertNotNull(reserveContext);
    // the raw observed-write hook precedes the filtered new-reserve event
    assertEquals(List.of("onReserveUpdate", "onNewReserve"), reserveListener.events());
    assertEquals(1, cache.reserveContexts().size());
    assertEquals(SOL_MINT, reserveContext.mint());
    assertEquals(MAIN_MARKET_KEY, reserveContext.market());
    assertEquals(PRICE_FEED_KEY, reserveContext.priceFeed());
    assertEquals("SOL", reserveContext.tokenName());
    // the mappings resolved a price chain for the reserve
    final var priceChains = reserveContext.priceChains();
    assertNotNull(priceChains);
    assertTrue(priceChains.priceChain().length > 0);

    // the snapshot's SOL chain heads with a MostRecentOf COMPOSITE whose
    // sources are a Chainlink (index 1) and a PythLazer (index 2). indexes()
    // now recurses into the composite and serves each source through the
    // feed-indexed path with the reserve's real liquidity (previously this
    // fell through to the zero-liquidity mappings scan).
    assertFalse(priceChains.priceChain()[0] instanceof OracleEntry);
    final var chainlinkOracle = fromBase58Encoded("14HefJnxgiYQ6qiGL5b6GVZVUSAFpmehRdBJRaZ6GKx");
    final var pythLazerOracle = fromBase58Encoded("HFn8GnPADiny6XqUoWE8uRPPxb29ikn4yTuPa9MF2fWJ");
    final var collateral = BigInteger.valueOf(reserveContext.totalCollateral());

    final var chainlinkFeed = cache.indexes(SOL_MINT, chainlinkOracle, OracleType.Chainlink);
    assertNotNull(chainlinkFeed, "the composite's Chainlink source is not served");
    assertArrayEquals(new short[]{1, -1, -1, -1}, chainlinkFeed.indexes());
    assertEquals(collateral, chainlinkFeed.liquidity(), "served through the zero-liquidity fallback, not the feed path");

    final var pythFeed = cache.indexes(SOL_MINT, pythLazerOracle, OracleType.PythLazer);
    assertNotNull(pythFeed, "the composite's PythLazer source is not served");
    assertArrayEquals(new short[]{2, -1, -1, -1}, pythFeed.indexes());
    assertEquals(collateral, pythFeed.liquidity());

    // an oracle the composite does not read is still unserved
    assertNull(cache.indexes(SOL_MINT, PRICE_FEED_KEY, OracleType.Chainlink));
    // the read lock taken by the query is handed back
    assertUnlocked(cache);

    // the reserve snapshot was persisted under its market directory
    assertTrue(java.nio.file.Files.exists(
        tempDir.resolve("reserves").resolve(MAIN_MARKET_KEY.toBase58())
    ));

    cache.accept(accountInfo(VAULT_STATE_KEY, 103L, vaultStateData));
    assertUnlocked(cache);
    assertEquals(List.of("onNewKaminoVault"), vaultListener.events());
    assertEquals(1, cache.vaultContexts().size());
    final var sharesMint = PublicKey.readPubKey(vaultStateData, VaultState.SHARES_MINT_OFFSET);
    final var vaultContext = cache.vaultForShareMint(sharesMint);
    assertNotNull(vaultContext);
    assertEquals(VAULT_STATE_KEY, vaultContext.readVaultState().publicKey());
  }

  @Test
  void reservesRequireTheirFeedBeforeIndexing(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);

    // a reserve arriving before its scope configuration is dropped: the cache
    // converges on the poller's next pass, not within one batch
    cache.accept(List.of(accountInfo(SOL_RESERVE_KEY, 100L, reserveData)), Map.of());
    assertNull(cache.reserveContext(SOL_RESERVE_KEY));
    assertTrue(cache.reserveContexts().isEmpty());

    cache.accept(
        List.of(
            accountInfo(CONFIGURATION_KEY, 101L, configurationData),
            accountInfo(ORACLE_MAPPINGS_KEY, 101L, mappingsData),
            accountInfo(SOL_RESERVE_KEY, 102L, reserveData)
        ),
        Map.of()
    );
    assertNotNull(cache.reserveContext(SOL_RESERVE_KEY));
    assertUnlocked(cache);
  }

  @Test
  void staleAndUnchangedAccountsAreIgnored(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);
    final var scopeListener = new RecordingListener(1);
    cache.subscribeToScope(scopeListener);

    cache.accept(accountInfo(CONFIGURATION_KEY, 100L, configurationData));
    cache.accept(accountInfo(ORACLE_MAPPINGS_KEY, 100L, mappingsData));
    cache.accept(accountInfo(SOL_RESERVE_KEY, 100L, reserveData));
    final var reserveContext = cache.reserveContext(SOL_RESERVE_KEY);
    assertNotNull(reserveContext);

    // an unchanged configuration at a newer slot notifies no one
    cache.accept(accountInfo(CONFIGURATION_KEY, 200L, configurationData));
    assertEquals(List.of("onNewScopeConfiguration"), scopeListener.events());

    // an identical reserve at a newer slot keeps the existing context
    cache.accept(accountInfo(SOL_RESERVE_KEY, 200L, reserveData));
    assertSame(reserveContext, cache.reserveContext(SOL_RESERVE_KEY));

    // an identical vault state at an older slot is ignored
    cache.accept(accountInfo(VAULT_STATE_KEY, 100L, vaultStateData));
    final var vaultContext = cache.vaultContexts().iterator().next();
    cache.accept(accountInfo(VAULT_STATE_KEY, 50L, vaultStateData));
    assertSame(vaultContext, cache.vaultContexts().iterator().next());
  }

  @Test
  void acceptReserveValidatesShapeAndUnknownAccountsAreIgnored(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);
    cache.accept(accountInfo(CONFIGURATION_KEY, 100L, configurationData));
    cache.accept(accountInfo(ORACLE_MAPPINGS_KEY, 100L, mappingsData));

    // wrong shape: not a reserve
    assertNull(cache.acceptReserve(accountInfo(SOL_RESERVE_KEY, 101L, new byte[16])));
    // an unhandled account size logs and changes nothing
    try (final var log = LogCapture.attach(KaminoCache.class.getName())) {
      cache.accept(accountInfo(SOL_RESERVE_KEY, 101L, new byte[16]));
      log.assertLogged("Unhandled Kamino Account");
    }
    assertTrue(cache.reserveContexts().isEmpty());

    final var reserveContext = cache.acceptReserve(accountInfo(SOL_RESERVE_KEY, 101L, reserveData));
    assertNotNull(reserveContext);
    assertSame(cache.reserveContext(SOL_RESERVE_KEY), reserveContext);
  }

  @Test
  void unsubscribedListenersAreNotNotified(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);
    final var listener = new RecordingListener(1);
    cache.subscribeToScope(listener);
    cache.unsubscribeFromScope(listener);
    cache.subscribeToReserves(listener);
    cache.unsubscribeFromReserves(listener);
    cache.subscribeToVaults(listener);
    cache.unsubscribeFromVaults(listener);

    cache.accept(accountInfo(CONFIGURATION_KEY, 100L, configurationData));
    cache.accept(accountInfo(ORACLE_MAPPINGS_KEY, 100L, mappingsData));
    cache.accept(accountInfo(SOL_RESERVE_KEY, 100L, reserveData));
    cache.accept(accountInfo(VAULT_STATE_KEY, 100L, vaultStateData));

    assertEquals(List.of(), listener.events());
    assertNotNull(cache.reserveContext(SOL_RESERVE_KEY));
  }

  @Test
  void truncatedAccountsAreSkippedNotCrashed(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);
    // shorter than a discriminator: the length guards are what stand between
    // this data and an out-of-bounds discriminator read
    final var truncated = accountInfo(SOL_RESERVE_KEY, 100L, new byte[3]);

    try (final var log = LogCapture.attach(KaminoCache.class.getName())) {
      cache.accept(truncated);
      log.assertLogged("Unhandled Kamino Account");
      assertTrue(
          log.messages().stream().noneMatch(m -> m != null && m.contains("Failed to handle Scope account")),
          () -> log.messages().toString()
      );
    }

    // the list path has no catch of its own; a truncated or null entry must
    // be skipped by the guards, not crash the polling thread
    assertDoesNotThrow(() -> cache.accept(java.util.Arrays.asList(truncated, null), Map.of()));
    assertTrue(cache.reserveContexts().isEmpty());
    assertUnlocked(cache);
  }

  @Test
  void aChangedConfigurationNotifiesTheChange(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);
    final var scopeListener = new RecordingListener(1);
    cache.subscribeToScope(scopeListener);

    cache.accept(accountInfo(CONFIGURATION_KEY, 100L, configurationData));
    assertEquals(List.of("onNewScopeConfiguration"), scopeListener.events());

    // a real change inside the compared region (the admin key) at a newer slot
    final var changed = configurationData.clone();
    changed[Configuration.ADMIN_OFFSET] ^= 0x01;
    cache.accept(accountInfo(CONFIGURATION_KEY, 200L, changed));
    assertEquals(
        List.of("onNewScopeConfiguration", "onScopeConfigurationChange"),
        scopeListener.events()
    );

    // the change tore the old registration down without replacing it, so the
    // next arrival of this key is a NEW registration — a leftover stale entry
    // would swallow it as unchanged instead
    cache.accept(accountInfo(CONFIGURATION_KEY, 300L, changed));
    assertEquals(
        List.of("onNewScopeConfiguration", "onScopeConfigurationChange", "onNewScopeConfiguration"),
        scopeListener.events()
    );
    assertUnlocked(cache);
  }

  @Test
  void aSameSlotReserveChangeIsStale(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);
    cache.accept(accountInfo(CONFIGURATION_KEY, 100L, configurationData));
    cache.accept(accountInfo(ORACLE_MAPPINGS_KEY, 100L, mappingsData));
    cache.accept(accountInfo(SOL_RESERVE_KEY, 100L, reserveData));
    final var reserveContext = cache.reserveContext(SOL_RESERVE_KEY);
    assertNotNull(reserveContext);

    // changed collateral at the SAME slot: stale, the context must not move
    final var changed = reserveData.clone();
    changed[software.sava.idl.clients.kamino.lend.gen.types.Reserve.COLLATERAL_OFFSET
        + software.sava.idl.clients.kamino.lend.gen.types.ReserveCollateral.MINT_TOTAL_SUPPLY_OFFSET] ^= 0x01;
    cache.accept(accountInfo(SOL_RESERVE_KEY, 100L, changed));
    assertSame(reserveContext, cache.reserveContext(SOL_RESERVE_KEY));
    assertUnlocked(cache);
  }

  @Test
  void nullPersistencePathsDisablePersistenceQuietly(@TempDir final Path tempDir) {
    // production supports running without configuration/mappings persistence;
    // a "non-null" guard forced the wrong way turns that into an NPE per accept
    final var kaminoAccounts = KaminoAccounts.MAIN_NET;
    final var cache = new KaminoCacheImpl(
        null, null,
        kaminoAccounts.kLendProgram(),
        kaminoAccounts.scopePricesProgram(),
        kaminoAccounts.kVaultsProgram(),
        null, null,
        Duration.ofSeconds(1),
        null,
        null,
        tempDir.resolve("reserves"),
        Map.of(),
        new ConcurrentHashMap<>(),
        new ConcurrentHashMap<>(),
        new ConcurrentHashMap<>()
    );
    try {
      java.nio.file.Files.createDirectories(tempDir.resolve("reserves"));
    } catch (final IOException e) {
      throw new java.io.UncheckedIOException(e);
    }

    try (final var log = LogCapture.attach(KaminoCache.class.getName())) {
      cache.accept(accountInfo(CONFIGURATION_KEY, 100L, configurationData));
      cache.accept(accountInfo(ORACLE_MAPPINGS_KEY, 100L, mappingsData));
      cache.accept(accountInfo(SOL_RESERVE_KEY, 100L, reserveData));
      assertTrue(
          log.messages().stream().noneMatch(m -> m != null && m.contains("Failed to handle Scope account")),
          () -> log.messages().toString()
      );
    }
    assertNotNull(cache.reserveContext(SOL_RESERVE_KEY));
    assertUnlocked(cache);
  }

  @Test
  void aRekeyedDuplicateConfigurationIsIgnored(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);
    cache.accept(accountInfo(CONFIGURATION_KEY, 100L, configurationData));

    final var scopeListener = new RecordingListener(1);
    cache.subscribeToScope(scopeListener);
    // the same configuration bytes under a different account key: the price
    // feed is already registered, so this must be recognized and dropped, not
    // registered a second time under the new key
    final var rekeyed = PublicKey.createPubKey(new byte[]{9, 9, 9, 9, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 9, 9});
    cache.accept(accountInfo(rekeyed, 200L, configurationData));

    assertEquals(List.of(), scopeListener.events());
    assertUnlocked(cache);
  }

  @Test
  void vaultUpdateGatesHoldAtTheSlotAndReserveBoundaries(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);
    final var vaultListener = new RecordingListener(1);
    cache.subscribeToVaults(vaultListener);
    final var sharesMint = PublicKey.readPubKey(vaultStateData, VaultState.SHARES_MINT_OFFSET);

    cache.accept(accountInfo(VAULT_STATE_KEY, 103L, vaultStateData));
    final var initial = cache.vaultForShareMint(sharesMint);
    assertNotNull(initial);
    assertEquals(List.of("onNewKaminoVault"), vaultListener.events());

    // a changed vault at the SAME slot is stale: same context, no notification
    final var reservesChanged = vaultStateData.clone();
    reservesChanged[VaultState.VAULT_ALLOCATION_STRATEGY_OFFSET] ^= 0x01;
    cache.accept(accountInfo(VAULT_STATE_KEY, 103L, reservesChanged));
    assertSame(initial, cache.vaultForShareMint(sharesMint));
    assertEquals(List.of("onNewKaminoVault"), vaultListener.events());

    // a newer change OUTSIDE the reserves (a fee) updates the context
    // silently: only reserve-allocation changes are worth a notification
    final var feeChanged = vaultStateData.clone();
    feeChanged[VaultState.PERFORMANCE_FEE_BPS_OFFSET] ^= 0x01;
    cache.accept(accountInfo(VAULT_STATE_KEY, 104L, feeChanged));
    final var refeed = cache.vaultForShareMint(sharesMint);
    assertNotSame(initial, refeed);
    assertEquals(List.of("onNewKaminoVault"), vaultListener.events());

    // a newer reserve-allocation change is the notification-worthy one
    cache.accept(accountInfo(VAULT_STATE_KEY, 105L, reservesChanged));
    assertNotSame(refeed, cache.vaultForShareMint(sharesMint));
    assertEquals(List.of("onNewKaminoVault", "onKaminoVaultChange"), vaultListener.events());
    assertUnlocked(cache);
  }

  @Test
  void aChangedConfigurationUnderANewKeySupersedesTheOld(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);
    final var scopeListener = new RecordingListener(1);
    cache.subscribeToScope(scopeListener);
    cache.accept(accountInfo(CONFIGURATION_KEY, 100L, configurationData));

    // changed bytes under a new key: the old registration must be torn down
    final var changed = configurationData.clone();
    changed[Configuration.ADMIN_OFFSET] ^= 0x01;
    final var rekeyed = PublicKey.createPubKey(new byte[]{9, 9, 9, 9, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 9, 9});
    cache.accept(accountInfo(rekeyed, 200L, changed));

    // the original key must have been removed with its config: accepting it
    // again is a NEW registration, not a stale hit on a leftover entry
    cache.accept(accountInfo(CONFIGURATION_KEY, 300L, configurationData));
    assertEquals(
        List.of("onNewScopeConfiguration", "onNewScopeConfiguration", "onNewScopeConfiguration"),
        scopeListener.events()
    );
    assertUnlocked(cache);
  }

  @Test
  void aFailedMappingsPersistIsLoggedNotFatal(@TempDir final Path tempDir) throws IOException {
    final var cache = createCache(tempDir);
    // break the persistence target: a file where the directory should be
    final var mappingsDir = tempDir.resolve("mappings");
    java.nio.file.Files.delete(mappingsDir);
    java.nio.file.Files.createFile(mappingsDir);

    cache.accept(accountInfo(CONFIGURATION_KEY, 100L, configurationData));
    try (final var log = LogCapture.attach(KaminoCache.class.getName())) {
      cache.accept(accountInfo(ORACLE_MAPPINGS_KEY, 100L, mappingsData));
      // the failure must be reported, and must not take the cache down
      log.assertLogged("Failed to persist mappings.");
    }
    cache.accept(accountInfo(SOL_RESERVE_KEY, 100L, reserveData));
    assertNotNull(cache.reserveContext(SOL_RESERVE_KEY));
    assertUnlocked(cache);
  }

  @Test
  void acceptedAccountsArePersistedForRestart(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);
    cache.accept(accountInfo(CONFIGURATION_KEY, 100L, configurationData));
    cache.accept(accountInfo(ORACLE_MAPPINGS_KEY, 100L, mappingsData));
    cache.accept(accountInfo(SOL_RESERVE_KEY, 100L, reserveData));

    final var reserveContext = cache.reserveContext(SOL_RESERVE_KEY);
    assertNotNull(reserveContext);
    // mappings persist flat; reserves persist under their market directory
    assertTrue(
        java.nio.file.Files.exists(
            tempDir.resolve("mappings").resolve(ORACLE_MAPPINGS_KEY.toBase58() + ".dat.gz")),
        "the accepted mappings were not persisted"
    );
    assertTrue(
        java.nio.file.Files.exists(
            tempDir.resolve("reserves")
                .resolve(reserveContext.market().toBase58())
                .resolve(SOL_RESERVE_KEY.toBase58() + ".dat.gz")),
        "the accepted reserve was not persisted"
    );
  }

  /// An OraclePrices account is what a reserve actually prices through, and it
  /// is far larger and far busier than the mappings, so the cache does not
  /// subscribe to one. It does relay one that arrives by another route — an
  /// account-fetcher batch assembled for somebody else routinely carries it —
  /// because the bytes are already in hand and the size check is free.
  @Test
  void anOraclePricesAccountIsRelayedToTheScopeListeners(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);
    final var scopeListener = new RecordingListener(11);
    final var reserveListener = new RecordingListener(12);
    cache.subscribeToScope(scopeListener);
    cache.subscribeToReserves(reserveListener);

    final byte[] data = new byte[OraclePrices.BYTES];
    System.arraycopy(OraclePrices.DISCRIMINATOR.data(), 0, data, 0, OraclePrices.DISCRIMINATOR.length());
    final var priceFeed = accountInfo(PRICE_FEED_KEY, 100L, data);

    cache.accept(List.of(priceFeed), Map.of(PRICE_FEED_KEY, priceFeed));
    assertEquals(List.of("onOraclePricesUpdate"), scopeListener.events());
    assertTrue(reserveListener.events().isEmpty(), "a reserve listener has no business with a price feed");

    // the single-account path relays it too, so a websocket delivery would not
    // be silently logged as an unhandled account
    cache.accept(priceFeed);
    assertEquals(List.of("onOraclePricesUpdate", "onOraclePricesUpdate"), scopeListener.events());
    assertUnlocked(cache);
  }

  /// A raw-write hook fires orders of magnitude more often than the filtered
  /// ones, so it is the likeliest to be the listener that throws. The poll loop
  /// calls it inside a single catch that logs and returns, so an unguarded throw
  /// would stop polling for every consumer sharing the cache — and take the
  /// other listeners' delivery with it.
  @Test
  void aThrowingListenerNeitherStopsTheOthersNorEscapes(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);
    final var thrower = new KaminoListener() {
      @Override
      public PublicKey key() {
        return PublicKey.createPubKey(new byte[]{13, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 13});
      }

      @Override
      public void onReserveUpdate(final AccountInfo<byte[]> accountInfo) {
        throw new IllegalStateException("listener is broken");
      }
    };
    final var survivor = new RecordingListener(14);
    cache.subscribeToReserves(thrower);
    cache.subscribeToReserves(survivor);

    // The batch path deliberately, unlike accept(AccountInfo), has no catch of its own: it is
    // the account fetcher's polling thread, and AccountFetcherImpl only guards the consumer as a
    // whole. So an unguarded listener throw escapes here no matter which order the listeners are
    // visited in, which is what makes this a deterministic pin rather than a race on map order.
    final var reserve = accountInfo(SOL_RESERVE_KEY, 100L, reserveData);
    assertDoesNotThrow(() -> cache.accept(List.of(reserve), Map.of(SOL_RESERVE_KEY, reserve)));
    assertEquals(1, survivor.updates(), "the surviving listener must still be delivered to");
    assertUnlocked(cache);
  }

  /// The program sweep is the fallback for a websocket which stopped delivering, so what has to
  /// be pinned is the failure direction: silence must bring it back. Crucially that includes the
  /// case the fallback exists for — a socket still open but no longer delivering, which is
  /// indistinguishable from a dead one and has to be treated as one.
  @Test
  void theSweepIsDeferredOnlyWhileTheWebsocketIsAudible(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);
    final long pollingDelayMillis = Duration.ofSeconds(1).toMillis();
    final long now = 1_000_000L;

    // nothing heard yet: bootstrap sweeps
    assertFalse(cache.deferSweep(now), "a cache which has heard nothing may not defer");

    // heard just now, swept just now: defer
    cache.lastWebSocketMessage = now;
    cache.lastSweep = now;
    assertTrue(cache.deferSweep(now));

    // still audible, but the sweep is overdue: the deferral is a delay, not a cancellation, so
    // deletion detection and reconciliation still happen on a healthy connection
    cache.lastSweep = now - (pollingDelayMillis * KaminoCacheImpl.HEALTHY_WEBSOCKET_SWEEP_FACTOR);
    assertFalse(cache.deferSweep(now), "a healthy websocket may delay the sweep, not cancel it");

    // gone quiet for a polling delay: sweep, whether the socket is closed or merely mute
    cache.lastSweep = now;
    cache.lastWebSocketMessage = now - pollingDelayMillis;
    assertFalse(cache.deferSweep(now), "silence must bring the fallback back");
  }

  /// Only the websocket may stamp liveness. `accept(AccountInfo)` is also how the poll ingests
  /// the accounts it fetches itself, so registering the cache directly would let the poll
  /// satisfy its own liveness test and defer the sweep forever.
  @Test
  void onlyTheWebsocketStampsLiveness(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);
    final var consumers = new ArrayList<Consumer<AccountInfo<byte[]>>>();
    cache.subscribe(recordingWebsocket(consumers));
    assertEquals(0L, cache.lastWebSocketMessage, "subscribing is not hearing anything");
    assertEquals(4, consumers.size(), "every subscription must route through the stamping consumer");

    // the poll's own ingestion path
    cache.accept(accountInfo(ORACLE_MAPPINGS_KEY, 100L, mappingsData));
    assertEquals(0L, cache.lastWebSocketMessage,
        "the poll's ingestion path must not be able to satisfy the liveness test");

    // the websocket's
    consumers.getFirst().accept(accountInfo(ORACLE_MAPPINGS_KEY, 101L, mappingsData));
    assertTrue(cache.lastWebSocketMessage > 0L, "a websocket notification must stamp liveness");
  }

  private static SolanaRpcWebsocket recordingWebsocket(final List<Consumer<AccountInfo<byte[]>>> consumers) {
    return (SolanaRpcWebsocket) java.lang.reflect.Proxy.newProxyInstance(
        SolanaRpcWebsocket.class.getClassLoader(),
        new Class<?>[]{SolanaRpcWebsocket.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "equals" -> proxy == args[0];
          case "hashCode" -> System.identityHashCode(proxy);
          case "toString" -> "recordingWebsocket";
          // programSubscribe returns a primitive boolean; a null unboxes into an NPE
          case "programSubscribe" -> {
            @SuppressWarnings("unchecked") final var consumer = (Consumer<AccountInfo<byte[]>>) args[2];
            consumers.add(consumer);
            yield Boolean.TRUE;
          }
          default -> null;
        }
    );
  }
}
