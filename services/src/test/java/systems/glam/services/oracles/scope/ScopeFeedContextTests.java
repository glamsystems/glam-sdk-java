package systems.glam.services.oracles.scope;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.idl.clients.kamino.lend.gen.types.ScopeConfiguration;
import software.sava.idl.clients.kamino.lend.gen.types.TokenInfo;
import software.sava.idl.clients.kamino.scope.entries.PriceChainsRecord;
import software.sava.idl.clients.kamino.scope.entries.ScopeEntry;
import software.sava.idl.clients.kamino.scope.entries.SwitchboardOnDemand;
import software.sava.idl.clients.kamino.scope.gen.types.Configuration;
import software.sava.idl.clients.kamino.scope.gen.types.OracleType;
import systems.glam.services.integrations.kamino.ReserveContext;

import java.math.BigInteger;
import java.util.Set;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.*;

final class ScopeFeedContextTests {

  private static PublicKey key(final int id) {
    final byte[] bytes = new byte[PublicKey.PUBLIC_KEY_LENGTH];
    bytes[0] = (byte) (id >> 8);
    bytes[1] = (byte) id;
    return PublicKey.createPubKey(bytes);
  }

  private static final PublicKey CONFIG_KEY = key(1);
  private static final PublicKey ADMIN = key(2);
  private static final PublicKey ORACLE_MAPPINGS = key(3);
  private static final PublicKey PRICE_FEED = key(4);
  private static final PublicKey TOKENS_METADATA = key(5);
  private static final PublicKey ORACLE_TWAPS = key(6);
  private static final PublicKey ADMIN_CACHED = key(7);
  private static final PublicKey MINT = key(8);
  private static final PublicKey ORACLE = key(9);

  private static byte[] configurationData() {
    final byte[] data = new byte[Configuration.PADDING_OFFSET + 64];
    ADMIN.write(data, Configuration.ADMIN_OFFSET);
    ORACLE_MAPPINGS.write(data, Configuration.ORACLE_MAPPINGS_OFFSET);
    PRICE_FEED.write(data, Configuration.ORACLE_PRICES_OFFSET);
    TOKENS_METADATA.write(data, Configuration.TOKENS_METADATA_OFFSET);
    ORACLE_TWAPS.write(data, Configuration.ORACLE_TWAPS_OFFSET);
    ADMIN_CACHED.write(data, Configuration.ADMIN_CACHED_OFFSET);
    return data;
  }

  private static ScopeFeedContext context() {
    return ScopeFeedContext.createContext(21L, configurationData(), CONFIG_KEY);
  }

  private static ReserveContext reserve(final int id,
                                        final long collateral,
                                        final int chainIndex,
                                        final PublicKey oracle) {
    final var scopeConfiguration = new ScopeConfiguration(
        PRICE_FEED,
        // real chains pad unused slots with u16 max
        new int[]{chainIndex, 65_535, 65_535, 65_535},
        new int[]{65_535, 65_535, 65_535, 65_535}
    );
    final var name = java.util.Arrays.copyOf(("R" + id).getBytes(US_ASCII), TokenInfo.NAME_LEN);
    final var tokenInfo = new TokenInfo(
        name, null, 0L, 0L, 0L, scopeConfiguration, null, null, 0,
        new byte[TokenInfo.RESERVED_LEN], new long[TokenInfo.PADDING_LEN]
    );
    final var priceChains = new PriceChainsRecord(
        new ScopeEntry[]{new SwitchboardOnDemand(chainIndex, oracle, Set.of())},
        new ScopeEntry[0]
    );
    final var reserveKey = key(id);
    return new ReserveContext(
        1L, new byte[0], reserveKey, AccountMeta.createWrite(reserveKey),
        key(7000), "R" + id, MINT, collateral, priceChains, tokenInfo
    );
  }

  @Test
  void createContextReadsConfigurationOffsets() {
    final var context = context();
    assertEquals(21L, context.slot());
    assertEquals(CONFIG_KEY, context.configurationKey());
    assertEquals(ORACLE_MAPPINGS, context.oracleMappings());
    assertEquals(PRICE_FEED, context.priceFeed());
    assertEquals(ADMIN, context.admin());
    assertEquals(TOKENS_METADATA, context.tokensMetadata());
    assertEquals(ORACLE_TWAPS, context.oracleTwaps());
    assertEquals(ADMIN_CACHED, context.adminCached());
    assertEquals(AccountMeta.createRead(ORACLE_MAPPINGS), context.readOracleMappings());
    assertEquals(AccountMeta.createRead(PRICE_FEED), context.readPriceFeed());
    // the retained snapshot is trimmed to the meaningful prefix
    assertEquals(Configuration.PADDING_OFFSET, context.configurationData().length);
  }

  @Test
  void toJsonCarriesEveryConfiguredKey() {
    final var json = context().toJson();
    for (final var expected : new PublicKey[]{CONFIG_KEY, ADMIN, ORACLE_MAPPINGS, PRICE_FEED, TOKENS_METADATA, ORACLE_TWAPS, ADMIN_CACHED}) {
      assertTrue(json.contains(expected.toBase58()), json);
    }
    assertTrue(json.contains("\"slot\": 21"), json);
  }

  @Test
  void isStaleOrUnchangedComparesSlotsUnsignedThenData() {
    final var context = context();
    final var changed = configurationData();
    changed[Configuration.ADMIN_OFFSET] ^= 1;

    // older or same slot: stale regardless of data
    assertTrue(context.isStaleOrUnchanged(20L, changed));
    assertTrue(context.isStaleOrUnchanged(21L, changed));
    // newer slot with identical meaningful bytes: unchanged
    assertTrue(context.isStaleOrUnchanged(22L, configurationData()));
    // newer slot and changed bytes: an update
    assertFalse(context.isStaleOrUnchanged(22L, changed));
    // slots compare unsigned: -1 is the far future, not the past
    assertFalse(context.isStaleOrUnchanged(-1L, changed));
  }

  @Test
  void indexesCollectsEveryMatchingReserve() {
    final var context = context();
    context.indexReserveContext(reserve(101, 1_000L, 11, ORACLE));
    context.indexReserveContext(reserve(102, 3_000L, 12, ORACLE));
    context.indexReserveContext(reserve(103, 2_000L, 13, ORACLE));

    final var feedIndexes = context.indexes(MINT, ORACLE, OracleType.SwitchboardOnDemand);
    assertNotNull(feedIndexes);
    assertEquals(PRICE_FEED, feedIndexes.priceFeed());
    assertEquals(AccountMeta.createRead(ORACLE_MAPPINGS), feedIndexes.readOracleMappings());
    // ordered by collateral descending; NO reserve is skipped and the unused
    // slot keeps its -1 sentinel
    assertArrayEquals(new short[]{12, 13, 11, -1}, feedIndexes.indexes());
    // liquidity sums every collected reserve
    assertEquals(BigInteger.valueOf(6_000L), feedIndexes.liquidity());
  }

  @Test
  void indexesKeepsTheTopFourByCollateral() {
    final var context = context();
    context.indexReserveContext(reserve(101, 100L, 11, ORACLE));
    context.indexReserveContext(reserve(102, 500L, 12, ORACLE));
    context.indexReserveContext(reserve(103, 400L, 13, ORACLE));
    context.indexReserveContext(reserve(104, 300L, 14, ORACLE));
    context.indexReserveContext(reserve(105, 200L, 15, ORACLE));

    final var feedIndexes = context.indexes(MINT, ORACLE, OracleType.SwitchboardOnDemand);
    assertNotNull(feedIndexes);
    assertArrayEquals(new short[]{12, 13, 14, 15}, feedIndexes.indexes());
    assertEquals(BigInteger.valueOf(1_400L), feedIndexes.liquidity());
  }

  @Test
  void indexesFiltersByOracleAndType() {
    final var context = context();
    context.indexReserveContext(reserve(101, 1_000L, 11, ORACLE));

    assertNull(context.indexes(key(999), ORACLE, OracleType.SwitchboardOnDemand));
    assertNull(context.indexes(MINT, key(999), OracleType.SwitchboardOnDemand));
    assertNull(context.indexes(MINT, ORACLE, OracleType.PythPullEMA));
  }

  @Test
  void reservesAreIndexedByChainIndexAndReplacedInPlace() {
    final var context = context();
    final var original = reserve(101, 1_000L, 11, ORACLE);
    context.indexReserveContext(original);
    // ReserveContext holds byte[] components, so record equality is identity
    // anyway; the index must hold the exact instance handed to it
    assertSame(original, context.reservesForIndex(11).get(original.pubKey()));

    // same reserve key re-indexed with new collateral replaces, not appends
    final var updated = reserve(101, 9_000L, 11, ORACLE);
    context.resortReserves(updated);
    assertSame(updated, context.reservesForIndex(11).get(updated.pubKey()));
    final var feedIndexes = context.indexes(MINT, ORACLE, OracleType.SwitchboardOnDemand);
    assertEquals(BigInteger.valueOf(9_000L), feedIndexes.liquidity());
  }

  @Test
  void removePreviousEntryForgetsTheReserve() {
    final var context = context();
    final var reserveA = reserve(101, 1_000L, 11, ORACLE);
    final var reserveB = reserve(102, 2_000L, 12, ORACLE);
    context.indexReserveContext(reserveA);
    context.indexReserveContext(reserveB);

    context.removePreviousEntry(reserveA);
    assertNull(context.reservesForIndex(11));
    assertNotNull(context.reservesForIndex(12));

    final var feedIndexes = context.indexes(MINT, ORACLE, OracleType.SwitchboardOnDemand);
    assertArrayEquals(new short[]{12, -1, -1, -1}, feedIndexes.indexes());
    assertEquals(BigInteger.valueOf(2_000L), feedIndexes.liquidity());

    context.removePreviousEntry(reserveB);
    assertNull(context.indexes(MINT, ORACLE, OracleType.SwitchboardOnDemand));
  }
}
