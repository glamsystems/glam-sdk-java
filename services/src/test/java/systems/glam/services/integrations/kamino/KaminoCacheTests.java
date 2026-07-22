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
    assertEquals(List.of("onNewReserve"), reserveListener.events());
    assertEquals(1, cache.reserveContexts().size());
    assertEquals(SOL_MINT, reserveContext.mint());
    assertEquals(MAIN_MARKET_KEY, reserveContext.market());
    assertEquals(PRICE_FEED_KEY, reserveContext.priceFeed());
    assertEquals("SOL", reserveContext.tokenName());
    // the mappings resolved a price chain for the reserve
    final var priceChains = reserveContext.priceChains();
    assertNotNull(priceChains);
    assertTrue(priceChains.priceChain().length > 0);

    // the snapshot's SOL chain heads with a MostRecentOf COMPOSITE, and
    // indexes() only matches direct OracleEntry members — nested oracle types
    // are a documented gap in ScopeFeedContext.indexes, so the agg-index query
    // cannot serve this reserve. If this assertion flips after re-snapshotting,
    // the chain became direct and the branch below should assert real indexes.
    assertFalse(priceChains.priceChain()[0] instanceof OracleEntry);
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
}
