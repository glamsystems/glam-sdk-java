package systems.glam.services.integrations.kamino;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.idl.clients.kamino.lend.gen.types.ScopeConfiguration;
import software.sava.idl.clients.kamino.lend.gen.types.TokenInfo;
import software.sava.idl.clients.kamino.scope.entries.PriceChainsRecord;
import software.sava.idl.clients.kamino.scope.entries.ScopeEntry;
import software.sava.idl.clients.kamino.scope.entries.SwitchboardOnDemand;

import java.util.Arrays;
import java.util.Set;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.*;

/// `changed` decides what the cache propagates to listeners and whether a
/// reserve is merely re-sorted or fully re-indexed, so every field it compares
/// needs a case that differs in exactly that field: a dropped comparison
/// silently reports "unchanged" and downstream state goes stale.
final class ReserveContextChangeTests {

  private static PublicKey key(final int id) {
    final byte[] bytes = new byte[PublicKey.PUBLIC_KEY_LENGTH];
    bytes[0] = (byte) (id >> 8);
    bytes[1] = (byte) id;
    return PublicKey.createPubKey(bytes);
  }

  private static final PublicKey RESERVE = key(1);
  private static final PublicKey MARKET = key(2);
  private static final PublicKey MINT = key(3);
  private static final PublicKey PRICE_FEED = key(4);
  private static final PublicKey ORACLE = key(5);

  private static TokenInfo tokenInfo(final String name,
                                     final PublicKey priceFeed,
                                     final long maxAgePrice,
                                     final long maxAgeTwap,
                                     final long maxTwapDivergenceBps) {
    return new TokenInfo(
        Arrays.copyOf(name.getBytes(US_ASCII), TokenInfo.NAME_LEN),
        null, maxTwapDivergenceBps, maxAgePrice, maxAgeTwap,
        new ScopeConfiguration(priceFeed, new int[]{11, 65_535, 65_535, 65_535}, new int[4]),
        null, null, 0, new byte[TokenInfo.RESERVED_LEN], new long[TokenInfo.PADDING_LEN]
    );
  }

  private static PriceChainsRecord chains(final int priceIndex, final int twapIndex) {
    return new PriceChainsRecord(
        new ScopeEntry[]{new SwitchboardOnDemand(priceIndex, ORACLE, Set.of())},
        new ScopeEntry[]{new SwitchboardOnDemand(twapIndex, ORACLE, Set.of())}
    );
  }

  private static ReserveContext context(final PublicKey market,
                                        final PublicKey mint,
                                        final String tokenName,
                                        final long totalCollateral,
                                        final TokenInfo tokenInfo,
                                        final PriceChainsRecord priceChains) {
    return new ReserveContext(
        1L, new byte[0], RESERVE, AccountMeta.createWrite(RESERVE),
        market, tokenName, mint, totalCollateral, priceChains, tokenInfo
    );
  }

  private static ReserveContext base() {
    return context(MARKET, MINT, "SOL", 1_000L, tokenInfo("SOL", PRICE_FEED, 60, 120, 300), chains(11, 21));
  }

  @Test
  void anIdenticalReserveReportsNoChanges() {
    assertEquals(Set.of(), base().changed(base()));
  }

  @Test
  void everyComparedFieldReportsItsOwnChange() {
    assertEquals(
        Set.of(ReserveChange.MARKET),
        base().changed(context(key(99), MINT, "SOL", 1_000L, tokenInfo("SOL", PRICE_FEED, 60, 120, 300), chains(11, 21)))
    );
    assertEquals(
        Set.of(ReserveChange.MINT),
        base().changed(context(MARKET, key(98), "SOL", 1_000L, tokenInfo("SOL", PRICE_FEED, 60, 120, 300), chains(11, 21)))
    );
    assertEquals(
        Set.of(ReserveChange.TOKEN_NAME),
        base().changed(context(MARKET, MINT, "JITOSOL", 1_000L, tokenInfo("SOL", PRICE_FEED, 60, 120, 300), chains(11, 21)))
    );
    assertEquals(
        Set.of(ReserveChange.TOTAL_COLLATERAL),
        base().changed(context(MARKET, MINT, "SOL", 2_000L, tokenInfo("SOL", PRICE_FEED, 60, 120, 300), chains(11, 21)))
    );
    assertEquals(
        Set.of(ReserveChange.PRICE_FEED),
        base().changed(context(MARKET, MINT, "SOL", 1_000L, tokenInfo("SOL", key(97), 60, 120, 300), chains(11, 21)))
    );
    assertEquals(
        Set.of(ReserveChange.MAX_AGE_PRICE_SECONDS),
        base().changed(context(MARKET, MINT, "SOL", 1_000L, tokenInfo("SOL", PRICE_FEED, 61, 120, 300), chains(11, 21)))
    );
    assertEquals(
        Set.of(ReserveChange.MAX_AGE_TWAP_SECONDS),
        base().changed(context(MARKET, MINT, "SOL", 1_000L, tokenInfo("SOL", PRICE_FEED, 60, 121, 300), chains(11, 21)))
    );
    assertEquals(
        Set.of(ReserveChange.MAX_TWAP_DIVERGENCE_BPS),
        base().changed(context(MARKET, MINT, "SOL", 1_000L, tokenInfo("SOL", PRICE_FEED, 60, 120, 301), chains(11, 21)))
    );
    assertEquals(
        Set.of(ReserveChange.PRICE_CHAIN),
        base().changed(context(MARKET, MINT, "SOL", 1_000L, tokenInfo("SOL", PRICE_FEED, 60, 120, 300), chains(12, 21)))
    );
    assertEquals(
        Set.of(ReserveChange.TWAP_CHAIN),
        base().changed(context(MARKET, MINT, "SOL", 1_000L, tokenInfo("SOL", PRICE_FEED, 60, 120, 300), chains(11, 22)))
    );
  }

  @Test
  void changesAccumulateRatherThanReplaceEachOther() {
    // the first change seeds the set and every later one is added to it, so a
    // reserve that moved market AND mint must report both
    final var many = base().changed(
        context(key(99), key(98), "JITOSOL", 2_000L, tokenInfo("SOL", key(97), 61, 121, 301), chains(12, 22))
    );
    assertEquals(
        Set.of(
            ReserveChange.MARKET, ReserveChange.MINT, ReserveChange.TOKEN_NAME,
            ReserveChange.TOTAL_COLLATERAL, ReserveChange.PRICE_FEED,
            ReserveChange.MAX_AGE_PRICE_SECONDS, ReserveChange.MAX_AGE_TWAP_SECONDS,
            ReserveChange.MAX_TWAP_DIVERGENCE_BPS, ReserveChange.PRICE_CHAIN, ReserveChange.TWAP_CHAIN
        ),
        many
    );
  }

  @Test
  void anAppearingOrDisappearingPriceChainChangesBothChains() {
    final var withChains = base();
    final var withoutChains = context(
        MARKET, MINT, "SOL", 1_000L, tokenInfo("SOL", PRICE_FEED, 60, 120, 300), null
    );
    assertEquals(Set.of(ReserveChange.PRICE_CHAIN, ReserveChange.TWAP_CHAIN), withChains.changed(withoutChains));
    assertEquals(Set.of(ReserveChange.PRICE_CHAIN, ReserveChange.TWAP_CHAIN), withoutChains.changed(withChains));
    // both absent is not a change, and neither chain is dereferenced
    assertEquals(Set.of(), withoutChains.changed(withoutChains));
  }

  @Test
  void comparingADifferentReserveIsRejected() {
    final var other = new ReserveContext(
        1L, new byte[0], key(42), AccountMeta.createWrite(key(42)),
        MARKET, "SOL", MINT, 1_000L, chains(11, 21), tokenInfo("SOL", PRICE_FEED, 60, 120, 300)
    );
    assertThrows(IllegalStateException.class, () -> base().changed(other));
  }

  @Test
  void onlyCollateralChangedGatesTheResortFastPath() {
    // the cache re-sorts in place for a collateral-only change and fully
    // re-indexes for anything else
    assertTrue(ReserveContext.onlyCollateralChanged(Set.of(ReserveChange.TOTAL_COLLATERAL)));
    assertFalse(ReserveContext.onlyCollateralChanged(Set.of()));
    assertFalse(ReserveContext.onlyCollateralChanged(Set.of(ReserveChange.PRICE_FEED)));
    assertFalse(ReserveContext.onlyCollateralChanged(
        Set.of(ReserveChange.TOTAL_COLLATERAL, ReserveChange.PRICE_FEED)
    ));
  }
}
