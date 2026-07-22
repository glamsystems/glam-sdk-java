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
import software.sava.idl.clients.kamino.scope.entries.OracleEntry;
import software.sava.idl.clients.kamino.scope.gen.types.Configuration;
import software.sava.idl.clients.kamino.scope.gen.types.OracleMappings;
import software.sava.idl.clients.kamino.vaults.gen.types.VaultState;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.Context;
import systems.glam.services.oracles.scope.MappingsContext;
import systems.glam.services.oracles.scope.ScopeFeedContext;
import systems.glam.services.tests.LogCapture;
import systems.glam.services.tests.ResourceUtil;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;

/// Drives the cache through *sequences* — the same account arriving changed,
/// stale, or malformed — where the single-shot tests in KaminoCacheTests only
/// establish the initial state.
final class KaminoCacheSequenceTests {

  private static final PublicKey CONFIGURATION_KEY = fromBase58Encoded("6cMwdbrJ95D7v5655Zsoe7oXmjQJMnagWK8EcdG6qmGM");
  private static final PublicKey ORACLE_MAPPINGS_KEY = fromBase58Encoded("4zh6bmb77qX2CL7t5AJYCqa6YqFafbz3QJNeFvZjLowg");
  private static final PublicKey SOL_RESERVE_KEY = fromBase58Encoded("d4A2prbA2whesmvHaL88BH6Ewn5N4bTSU2Ze8P6Bc4Q");
  private static final PublicKey VAULT_STATE_KEY = fromBase58Encoded("5YxwKgsvyTdT8q2CBgwA4L9BKbnKNrB66K9wUzij5wH");

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
    // initService creates these; constructing directly must do the same or
    // every persist quietly fails into the warning log
    for (final var dir : List.of("configurations", "mappings", "reserves")) {
      try {
        Files.createDirectories(tempDir.resolve(dir));
      } catch (final IOException e) {
        throw new java.io.UncheckedIOException(e);
      }
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

  private static KaminoCacheImpl primedCache(final Path tempDir) {
    final var cache = createCache(tempDir);
    cache.accept(accountInfo(CONFIGURATION_KEY, 100L, configurationData));
    cache.accept(accountInfo(ORACLE_MAPPINGS_KEY, 100L, mappingsData));
    cache.accept(accountInfo(SOL_RESERVE_KEY, 100L, reserveData));
    return cache;
  }

  private static final class RecordingListener implements KaminoListener {

    final List<String> events = new ArrayList<>();
    private final PublicKey key;

    RecordingListener(final int id) {
      final byte[] bytes = new byte[PublicKey.PUBLIC_KEY_LENGTH];
      bytes[0] = (byte) id;
      this.key = PublicKey.createPubKey(bytes);
    }

    @Override
    public PublicKey key() {
      return key;
    }

    @Override
    public void onMappingChange(final ScopeFeedContext scopeFeedContext,
                                final MappingsContext witness,
                                final MappingsContext mappingContext) {
      events.add("onMappingChange");
    }

    @Override
    public void onReserveChange(final ReserveContext previous,
                                final ReserveContext reserveContext,
                                final java.util.Set<ReserveChange> changes) {
      events.add("onReserveChange:" + changes);
    }

    @Override
    public void onNewKaminoVault(final KaminoVaultContext vaultContext) {
      events.add("onNewKaminoVault");
    }

    @Override
    public void onKaminoVaultChange(final KaminoVaultContext previous, final KaminoVaultContext vaultContext) {
      events.add("onKaminoVaultChange");
    }
  }

  @Test
  void dispatchChecksTheDiscriminatorNotJustTheLength(@TempDir final Path tempDir) {
    final var cache = primedCache(tempDir);
    // right discriminator, wrong length: must fall through to "unhandled", not
    // be parsed — a parse attempt on 16 bytes dies loudly and is logged as a
    // failure instead
    final var discriminators = List.of(
        Reserve.DISCRIMINATOR, VaultState.DISCRIMINATOR,
        OracleMappings.DISCRIMINATOR, Configuration.DISCRIMINATOR
    );
    for (final var discriminator : discriminators) {
      final byte[] truncated = Arrays.copyOf(discriminator.data(), 16);
      try (final var log = LogCapture.attach(KaminoCache.class.getName())) {
        cache.accept(accountInfo(SOL_RESERVE_KEY, 200L, truncated));
        log.assertLogged("Unhandled Kamino Account");
        assertFalse(
            log.messages().stream().anyMatch(m -> m != null && m.contains("Failed to handle")),
            () -> "attempted to parse a truncated account: " + log.messages()
        );
      }
      // and the list path, which has no catch, must not throw either
      assertDoesNotThrow(() -> cache.accept(List.of(accountInfo(SOL_RESERVE_KEY, 200L, truncated)), Map.of()));
    }
    assertNotNull(cache.reserveContext(SOL_RESERVE_KEY));
  }

  @Test
  void theListPathDispatchesEveryAccountShape(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);
    cache.accept(
        List.of(
            accountInfo(CONFIGURATION_KEY, 100L, configurationData),
            accountInfo(ORACLE_MAPPINGS_KEY, 100L, mappingsData),
            accountInfo(SOL_RESERVE_KEY, 101L, reserveData),
            accountInfo(VAULT_STATE_KEY, 101L, vaultStateData)
        ),
        Map.of()
    );
    final var reserveContext = cache.reserveContext(SOL_RESERVE_KEY);
    assertNotNull(reserveContext);
    // the mappings were dispatched too: the reserve resolved its price chains
    assertNotNull(reserveContext.priceChains());
    assertEquals(1, cache.vaultContexts().size());
  }

  @Test
  void mappingChangesAreGatedPersistedAndNotified(@TempDir final Path tempDir) throws IOException {
    final var cache = primedCache(tempDir);
    final var listener = new RecordingListener(1);
    cache.subscribeToScope(listener);

    final var persisted = tempDir.resolve("mappings").resolve(ORACLE_MAPPINGS_KEY + ".dat.gz");
    assertTrue(Files.exists(persisted), "priming should persist the mappings");
    Files.delete(persisted);

    // identical data at a newer slot: gated out, nothing re-persisted
    cache.accept(accountInfo(ORACLE_MAPPINGS_KEY, 200L, mappingsData));
    assertFalse(Files.exists(persisted), "unchanged mappings must not re-persist");
    assertEquals(List.of(), listener.events);

    // a mappings account the cache was never configured for: ignored entirely
    final var unknownKey = PublicKey.createPubKey(new byte[PublicKey.PUBLIC_KEY_LENGTH]);
    cache.accept(accountInfo(unknownKey, 200L, mappingsData));
    assertFalse(Files.exists(tempDir.resolve("mappings").resolve(unknownKey + ".dat.gz")));

    // changed bytes at the SAME slot are not newer, and older slots never win
    final byte[] changed = Arrays.copyOf(mappingsData, mappingsData.length);
    changed[changed.length - 1] ^= 1;
    cache.accept(accountInfo(ORACLE_MAPPINGS_KEY, 100L, changed));
    cache.accept(accountInfo(ORACLE_MAPPINGS_KEY, 50L, changed));
    assertFalse(Files.exists(persisted), "same or older slots must not re-persist");
    assertEquals(List.of(), listener.events);

    // genuinely changed bytes at a newer slot: re-persisted, witness notified
    cache.accept(accountInfo(ORACLE_MAPPINGS_KEY, 300L, changed));
    assertTrue(Files.exists(persisted), "changed mappings must persist");
    assertEquals(List.of("onMappingChange"), listener.events);
  }

  @Test
  void reserveUpdatesFollowSlotOrderAndChangeKind(@TempDir final Path tempDir) throws IOException {
    final var cache = primedCache(tempDir);
    final var listener = new RecordingListener(1);
    final var specificListener = new RecordingListener(2);
    cache.subscribeToReserves(listener);
    cache.subscribeToReserve(SOL_RESERVE_KEY, specificListener);
    final var before = cache.reserveContext(SOL_RESERVE_KEY);

    final int collateralOffset = Reserve.COLLATERAL_OFFSET + ReserveCollateral.MINT_TOTAL_SUPPLY_OFFSET;
    final byte[] moreCollateral = Arrays.copyOf(reserveData, reserveData.length);
    ByteUtil.putInt64LE(moreCollateral, collateralOffset, before.totalCollateral() + 1_000L);

    // an older slot never wins, however different the data
    cache.accept(accountInfo(SOL_RESERVE_KEY, 50L, moreCollateral));
    assertSame(before, cache.reserveContext(SOL_RESERVE_KEY));

    // newer slot, collateral-only change: the context is replaced but only
    // re-sorted — no reserve-change notification
    cache.accept(accountInfo(SOL_RESERVE_KEY, 200L, moreCollateral));
    final var resorted = cache.reserveContext(SOL_RESERVE_KEY);
    assertNotSame(before, resorted);
    assertEquals(before.totalCollateral() + 1_000L, resorted.totalCollateral());
    assertEquals(List.of(), listener.events);

    // newer slot, a non-collateral change (token name): fully re-indexed,
    // notified, and re-persisted
    final var marketDir = tempDir.resolve("reserves").resolve(before.market().toBase58());
    final var persisted = marketDir.resolve(SOL_RESERVE_KEY + ".dat.gz");
    assertTrue(Files.exists(persisted));
    Files.delete(persisted);
    final byte[] renamed = Arrays.copyOf(moreCollateral, moreCollateral.length);
    final int nameOffset = Reserve.CONFIG_OFFSET + ReserveConfig.TOKEN_INFO_OFFSET;
    renamed[nameOffset] = 'X';
    try (final var log = LogCapture.attach(KaminoCache.class.getName())) {
      cache.accept(accountInfo(SOL_RESERVE_KEY, 300L, renamed));
      // the update succeeded outright — it did not limp through the
      // catch-and-log path with a broken lock behind it
      assertEquals(List.of(), log.messages());
    }
    assertFalse(cache.lock.isWriteLocked());
    final var renamedContext = cache.reserveContext(SOL_RESERVE_KEY);
    // the vault-wide and the reserve-specific listeners both hear it
    assertEquals(listener.events, specificListener.events);
    assertEquals(1, listener.events.size());
    assertTrue(listener.events.getFirst().startsWith("onReserveChange"), listener.events.toString());
    assertTrue(listener.events.getFirst().contains("TOKEN_NAME"), listener.events.toString());
    assertTrue(renamedContext.tokenName().startsWith("X"), renamedContext.tokenName());
    assertTrue(Files.exists(persisted), "a re-indexed reserve is re-persisted");
  }

  @Test
  void vaultUpdatesFollowSlotOrderAndReserveChanges(@TempDir final Path tempDir) {
    final var cache = createCache(tempDir);
    // a vault-specific subscription registered before the vault first appears
    // hears the new-vault event too
    final var sharesMint = PublicKey.readPubKey(vaultStateData, VaultState.SHARES_MINT_OFFSET);
    final var earlyListener = new RecordingListener(3);
    cache.subscribeToVault(sharesMint, earlyListener);
    cache.accept(accountInfo(VAULT_STATE_KEY, 100L, vaultStateData));
    assertEquals(List.of("onNewKaminoVault"), earlyListener.events);
    cache.unSubscribeFromVault(sharesMint, earlyListener);
    // unsubscribing a mint nothing ever subscribed to is a no-op, not a crash
    assertDoesNotThrow(() -> cache.unSubscribeFromVault(PublicKey.NONE, earlyListener));
    final var before = cache.vaultContexts().iterator().next();
    assertTrue(before.numReserves() > 0, "the fixture vault allocates to reserves");

    final var vaultListener = new RecordingListener(1);
    final var specificListener = new RecordingListener(2);
    cache.subscribeToVaults(vaultListener);
    cache.subscribeToVault(sharesMint, specificListener);

    // identical data at a newer slot is not a change: the same context stays
    cache.accept(accountInfo(VAULT_STATE_KEY, 150L, vaultStateData));
    assertSame(before, cache.vaultForShareMint(sharesMint));

    // a changed vault key that is NOT a reserve (the lookup table) replaces
    // the context without a reserve-change notification
    final byte[] changedLut = Arrays.copyOf(vaultStateData, vaultStateData.length);
    changedLut[VaultState.VAULT_LOOKUP_TABLE_OFFSET] ^= 1;
    cache.accept(accountInfo(VAULT_STATE_KEY, 160L, changedLut));
    final var lutChanged = cache.vaultForShareMint(sharesMint);
    assertNotNull(lutChanged);
    assertNotSame(before, lutChanged);
    assertEquals(List.of(), vaultListener.events);
    assertEquals(List.of(), specificListener.events);

    // each non-reserve vault key is compared independently: a change to any
    // one of them replaces the context, silently
    var previousContext = lutChanged;
    long slot = 170L;
    for (final int keyOffset : new int[]{
        VaultState.VAULT_ADMIN_AUTHORITY_OFFSET,
        VaultState.BASE_VAULT_AUTHORITY_OFFSET,
        VaultState.VAULT_FARM_OFFSET
    }) {
      final byte[] changedKey = Arrays.copyOf(vaultStateData, vaultStateData.length);
      changedKey[keyOffset] ^= 1;
      cache.accept(accountInfo(VAULT_STATE_KEY, ++slot, changedKey));
      final var replaced = cache.vaultForShareMint(sharesMint);
      assertNotNull(replaced);
      assertNotSame(previousContext, replaced, "offset " + keyOffset);
      previousContext = replaced;
    }
    assertEquals(List.of(), vaultListener.events);

    final byte[] changedReserves = Arrays.copyOf(vaultStateData, vaultStateData.length);
    changedReserves[VaultState.VAULT_ALLOCATION_STRATEGY_OFFSET] ^= 1;

    // an older slot never wins
    cache.accept(accountInfo(VAULT_STATE_KEY, 50L, changedReserves));
    assertSame(previousContext, cache.vaultForShareMint(sharesMint));

    // a newer slot with a changed reserve set replaces and notifies both the
    // general and the vault-specific listeners
    cache.accept(accountInfo(VAULT_STATE_KEY, 999L, changedReserves));
    final var reserveChanged = cache.vaultForShareMint(sharesMint);
    assertNotNull(reserveChanged);
    assertNotSame(previousContext, reserveChanged);
    // the flipped byte changed a reserve KEY, not the count
    assertEquals(previousContext.numReserves(), reserveChanged.numReserves());
    assertEquals(List.of("onKaminoVaultChange"), vaultListener.events);
    assertEquals(List.of("onKaminoVaultChange"), specificListener.events);
    // and the earlier unsubscribes held: the early listener heard nothing more
    assertEquals(List.of("onNewKaminoVault"), earlyListener.events);
  }

  @Test
  void indexesFallsBackToScanningMappingsEntriesDirectly(@TempDir final Path tempDir) {
    final var cache = primedCache(tempDir);

    // derive a direct oracle entry from the same mappings the cache holds; the
    // expected indexes are every entry sharing its oracle and type, in entry
    // order, capped at four
    final var mappingsContext = MappingsContext.createContext(accountInfo(ORACLE_MAPPINGS_KEY, 100L, mappingsData));
    assertEquals(100L, mappingsContext.slot());
    final var scopeEntries = mappingsContext.scopeEntries();
    // prefer an oracle several entries share, so the cap and accumulation in
    // the scan are exercised, not just the first hit
    final var matches = new java.util.HashMap<String, Integer>();
    OracleEntry firstDirect = null;
    for (int i = 0; i < scopeEntries.numEntries(); ++i) {
      if (scopeEntries.scopeEntry(i) instanceof OracleEntry oracleEntry) {
        final var matchKey = oracleEntry.oracleType() + ":" + oracleEntry.oracle();
        final int count = matches.merge(matchKey, 1, Integer::sum);
        if (firstDirect == null || (count > 1 && matches.getOrDefault(firstDirect.oracleType() + ":" + firstDirect.oracle(), 1) == 1)) {
          firstDirect = oracleEntry;
        }
      }
    }
    assertNotNull(firstDirect, "the mainnet mappings should hold at least one direct oracle entry");

    final var expected = new short[]{-1, -1, -1, -1};
    int n = 0;
    for (int i = 0; i < scopeEntries.numEntries() && n < 4; ++i) {
      if (scopeEntries.scopeEntry(i) instanceof OracleEntry oracleEntry
          && oracleEntry.oracleType() == firstDirect.oracleType()
          && oracleEntry.oracle().equals(firstDirect.oracle())) {
        expected[n++] = (short) oracleEntry.index();
      }
    }

    // a mint with no reserves cannot resolve through the feed contexts, so the
    // query falls back to scanning the mappings entries
    final var unknownMint = PublicKey.createPubKey(new byte[PublicKey.PUBLIC_KEY_LENGTH]);
    final var feedIndexes = cache.indexes(unknownMint, firstDirect.oracle(), firstDirect.oracleType());
    assertNotNull(feedIndexes);
    assertArrayEquals(expected, feedIndexes.indexes());
    assertEquals(BigInteger.ZERO, feedIndexes.liquidity());
    assertEquals(fromBase58Encoded("3t4JZcueEzTbVP6kLxXrL3VpWx45jDer4eqysweBchNH"), feedIndexes.priceFeed());

    // an oracle nothing references resolves through neither path
    assertNull(cache.indexes(unknownMint, unknownMint, firstDirect.oracleType()));
  }

  @Test
  void aReserveChangeWithNoSpecificSubscribersDoesNotTrip(@TempDir final Path tempDir) {
    // notifyReserveChange guards a per-reserve listener map that is null until
    // someone subscribes to that reserve; the change must apply cleanly
    final var cache = primedCache(tempDir);
    final byte[] renamed = Arrays.copyOf(reserveData, reserveData.length);
    renamed[Reserve.CONFIG_OFFSET + ReserveConfig.TOKEN_INFO_OFFSET] = 'Z';
    try (final var log = LogCapture.attach(KaminoCache.class.getName())) {
      cache.accept(accountInfo(SOL_RESERVE_KEY, 200L, renamed));
      assertEquals(List.of(), log.messages());
    }
    assertTrue(cache.reserveContext(SOL_RESERVE_KEY).tokenName().startsWith("Z"));
  }
}
