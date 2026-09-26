package systems.glam.sdk.mapping;

import org.junit.jupiter.api.Test;
import systems.glam.sdk.idl.programs.glam.config.gen.types.AssetMeta;
import systems.glam.sdk.idl.programs.glam.config.gen.types.OracleSource;
import systems.glam.sdk.mapping.RegisteredOracles.Failure;
import systems.glam.sdk.mapping.RegisteredOracles.None;
import systems.glam.sdk.mapping.RegisteredOracles.Reason;
import systems.glam.sdk.mapping.RegisteredOracles.Selected;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static systems.glam.sdk.tests.MappingFixtures.*;

/// The selection rule is the monorepo's `selectGlamOracleRegistration`, so both consumers pick the same
/// registration: deprecated never, lowest priority, then source preference with the Kamino reserve first,
/// a same-source tie selects nothing, and the stored position decides nothing.
final class RegisteredOraclesTests {

  private static Selected selected(final RegisteredOracles oracles, final software.sava.core.accounts.PublicKey mint) {
    return assertInstanceOf(Selected.class, oracles.select(mint));
  }

  /// The recorded configuration registers each mint once, so every selection is the sole active one; a
  /// mint it does not list is unregistered.
  @Test
  void theRecordedConfigurationRegistersOneOraclePerMint() {
    final var oracles = RegisteredOracles.of(globalConfig());
    assertEquals(52, oracles.size());
    final var usdc = selected(oracles, USDC);
    assertEquals(USDC_ORACLE, usdc.meta().oracle());
    assertEquals(OracleSource.PythLazerStableCoin, usdc.meta().oracleSource());
    assertEquals(Reason.SOLE_ACTIVE, usdc.reason());
    assertEquals(WSOL_ORACLE, oracles.forMint(WSOL).oracle());
    assertEquals(OracleSource.PythLazer, oracles.forMint(WSOL).oracleSource());
    assertEquals(META_ORACLE, oracles.forMint(META).oracle());
    assertEquals(OracleSource.SwitchboardOnDemand, oracles.forMint(META).oracleSource());
    assertEquals(XSTOCK_ORACLE, oracles.forMint(XSTOCK).oracle());
    assertEquals(OracleSource.ChainlinkRWA, oracles.forMint(XSTOCK).oracleSource());

    final var none = assertInstanceOf(None.class, oracles.select(key(99)));
    assertEquals(Failure.UNREGISTERED, none.failure());
    assertEquals(0, none.registrations());
    assertEquals(List.of(), none.tied());
    assertNull(oracles.forMint(key(99)));
  }

  /// The lowest priority number wins wherever the registration sits in the vector.
  @Test
  void theLowestPriorityNumberWinsWhereverItIsStored() {
    final var mint = key(1);
    final var later = meta(mint, key(11), OracleSource.PythPull, 1);
    final var top = meta(mint, key(12), OracleSource.PythLazer, 0);
    final var other = meta(key(2), key(21), OracleSource.PythLazer, 5);
    for (final var order : List.of(new AssetMeta[]{later, top, other}, new AssetMeta[]{other, top, later})) {
      final var oracles = RegisteredOracles.of(order);
      assertEquals(2, oracles.size(), "two mints");
      final var chosen = selected(oracles, mint);
      assertSame(top, chosen.meta());
      assertEquals(Reason.LOWEST_PRIORITY, chosen.reason());
      assertSame(other, oracles.forMint(key(2)));
    }
  }

  /// Among equal priorities the source preference decides: the Kamino reserve first, then the sources in
  /// the order the program declares them; the stored position never does.
  @Test
  void amongEqualPrioritiesTheSourcePreferenceDecides() {
    final var mint = key(1);
    final var lazer = meta(mint, key(12), OracleSource.PythLazer, 0);
    final var switchboard = meta(mint, key(13), OracleSource.SwitchboardOnDemand, 0);
    final var reserve = meta(mint, key(14), OracleSource.KaminoReserve, 0);
    final var lowerRank = RegisteredOracles.of(new AssetMeta[]{lazer, switchboard});
    var chosen = selected(lowerRank, mint);
    assertSame(switchboard, chosen.meta(), "SwitchboardOnDemand is declared before PythLazer");
    assertEquals(Reason.SOURCE_PREFERENCE, chosen.reason());
    assertSame(switchboard, RegisteredOracles.of(new AssetMeta[]{switchboard, lazer}).forMint(mint), "whichever is stored first");

    for (final var order : List.of(new AssetMeta[]{lazer, switchboard, reserve}, new AssetMeta[]{reserve, lazer, switchboard})) {
      chosen = selected(RegisteredOracles.of(order), mint);
      assertSame(reserve, chosen.meta(), "the Kamino reserve comes first");
      assertEquals(Reason.SOURCE_PREFERENCE, chosen.reason());
    }
    // a lower priority number still beats the preferred source
    final var lowerNumber = meta(mint, key(15), OracleSource.PythPull, 0);
    final var reserveAtOne = meta(mint, key(16), OracleSource.KaminoReserve, 1);
    assertSame(lowerNumber, RegisteredOracles.of(new AssetMeta[]{reserveAtOne, lowerNumber}).forMint(mint));
    assertEquals(-1, RegisteredOracles.sourceRank(OracleSource.KaminoReserve));
    assertEquals(OracleSource.Pyth.ordinal(), RegisteredOracles.sourceRank(OracleSource.Pyth));
  }

  /// Equal priority and equal source is a tie nobody can break from here: nothing is selected and the tied
  /// registrations are named in stored order.
  @Test
  void aSameSourceTieSelectsNothingAndNamesTheTied() {
    final var mint = key(1);
    final var first = meta(mint, key(11), OracleSource.PythLazer, 0);
    final var second = meta(mint, key(12), OracleSource.PythLazer, 0);
    final var behind = meta(mint, key(13), OracleSource.KaminoReserve, 1);
    final var oracles = RegisteredOracles.of(new AssetMeta[]{first, behind, second});
    final var none = assertInstanceOf(None.class, oracles.select(mint));
    assertEquals(Failure.TIED, none.failure());
    assertEquals(List.of(first, second), none.tied());
    assertEquals(3, none.registrations());
    assertNull(oracles.forMint(mint));
    assertThrows(UnsupportedOperationException.class, () -> none.tied().add(first));
  }

  /// A deprecated registration, a negative priority under a signed reader, is never selected: an active
  /// one at any priority wins over it, and a mint with only deprecated registrations selects nothing.
  @Test
  void aDeprecatedRegistrationIsNeverSelected() {
    final var mint = key(1);
    final var deprecated = meta(mint, key(11), OracleSource.KaminoReserve, -1);
    final var active = meta(mint, key(12), OracleSource.PythLazer, 3);
    assertTrue(RegisteredOracles.deprecated(-1));
    assertFalse(RegisteredOracles.deprecated(0));
    assertFalse(RegisteredOracles.deprecated(255), "an unsigned reader has no deprecated state");

    final var chosen = selected(RegisteredOracles.of(new AssetMeta[]{deprecated, active}), mint);
    assertSame(active, chosen.meta());
    assertEquals(Reason.SOLE_ACTIVE, chosen.reason());

    final var onlyDeprecated = RegisteredOracles.of(new AssetMeta[]{deprecated, meta(mint, key(13), OracleSource.PythLazer, -2)});
    final var none = assertInstanceOf(None.class, onlyDeprecated.select(mint));
    assertEquals(Failure.ALL_DEPRECATED, none.failure());
    assertEquals(2, none.registrations());
    assertEquals(1, onlyDeprecated.size(), "the mint is registered, if only by deprecated rows");
  }
}
