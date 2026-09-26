package systems.glam.sdk.mapping;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import systems.glam.ix.proxy.SuppliedAccountsRequest.Role;
import systems.glam.sdk.idl.programs.glam.config.gen.types.AssetMeta;
import systems.glam.sdk.idl.programs.glam.config.gen.types.OracleSource;
import systems.glam.sdk.mapping.OrcaOracleResolver.ReserveOracle;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static systems.glam.sdk.mapping.OrcaOracleResolver.ASSET_ORACLE;
import static systems.glam.sdk.mapping.OrcaOracleResolver.SOL_USD_ORACLE;
import static systems.glam.sdk.tests.MappingFixtures.*;

final class OrcaOracleResolverTests {

  private static Role asset(final PublicKey mint) {
    return new Role(ASSET_ORACLE, List.of(mint), false);
  }

  private static final Role SOL_USD = new Role(SOL_USD_ORACLE, List.of(), true);

  private static List<Role> roles(final PublicKey mintA, final PublicKey mintB) {
    return List.of(asset(mintA), asset(mintB), SOL_USD);
  }

  private static OrcaOracleResolver recorded() {
    return new OrcaOracleResolver(RegisteredOracles.of(globalConfig()), WSOL);
  }

  private static OrcaOracleResolver over(final AssetMeta... metas) {
    return new OrcaOracleResolver(RegisteredOracles.of(metas), WSOL);
  }

  /// Two USD-priced mints, from the recorded configuration: their registered oracles, in the roles'
  /// order, and no SOL oracle, since the handler converts nothing.
  @Test
  void twoUsdPricedMintsGetTheirOraclesAndNoSolOracle() {
    final var resolved = recorded().resolve(roles(USDC, WSOL));
    assertEquals(List.of(USDC_ORACLE, WSOL_ORACLE), resolved.accounts());
    assertEquals(List.of(), resolved.kaminoReserves());
    assertEquals(List.of(WSOL_ORACLE, META_ORACLE), recorded().resolve(roles(WSOL, META)).accounts(), "a Switchboard feed prices in USD too");
    assertEquals(List.of(USDC_ORACLE, WSOL_ORACLE), recorded().resolve(List.of(asset(USDC), asset(WSOL))).accounts(), "with no SOL role the two oracles are the answer");
  }

  /// A SOL-priced mint beside a USD-priced one needs the wrapped SOL oracle, registered under the wrapped
  /// SOL mint and priced in USD, as the third account.
  @Test
  void aSolPricedMintBesideAUsdPricedOneAddsTheWrappedSolOracle() {
    final var jito = key(1);
    final var pool = key(11);
    final var resolver = over(
        meta(jito, pool, OracleSource.LstPoolState, 0),
        meta(USDC, USDC_ORACLE, OracleSource.PythLazerStableCoin, 0),
        meta(WSOL, WSOL_ORACLE, OracleSource.PythLazer, 0)
    );
    assertEquals(List.of(pool, USDC_ORACLE, WSOL_ORACLE), resolver.resolve(roles(jito, USDC)).accounts());
    assertEquals(List.of(USDC_ORACLE, pool, WSOL_ORACLE), resolver.resolve(roles(USDC, jito)).accounts(), "either side may be the SOL-priced one");

    // two SOL-priced mints agree, so no conversion and no SOL oracle
    final var marinade = key(12);
    final var bothSol = over(meta(jito, pool, OracleSource.LstPoolState, 0), meta(MSOL, marinade, OracleSource.MarinadeState, 0));
    assertEquals(List.of(pool, marinade), bothSol.resolve(roles(jito, MSOL)).accounts());
  }

  /// A mint with no selectable registration (none, all deprecated, a same-source tie), an oracle source
  /// the protocol does not price, one the Orca handler refuses, and a wrapped SOL oracle that is missing,
  /// SOL-priced or a Scope-read feed when a conversion needs it, each refuse with a message naming the
  /// mint or oracle; nothing is guessed.
  @Test
  void anOracleThatCannotBePlacedRefusesWithTheReason() {
    final var unknown = key(9);
    var refused = assertThrows(IllegalArgumentException.class, () -> recorded().resolve(roles(USDC, unknown)));
    assertEquals("The global configuration registers no oracle for mint " + unknown + '.', refused.getMessage());

    refused = assertThrows(IllegalArgumentException.class, () -> recorded().resolve(roles(XSTOCK, USDC)));
    assertEquals(
        "The oracle of mint " + XSTOCK + ", " + XSTOCK_ORACLE + " (source ChainlinkRWA), is one the Orca handler refuses (UnsupportedOracleSource).",
        refused.getMessage()
    );
    final var chainlinkX = over(meta(key(4), key(40), OracleSource.ChainlinkX, 0), meta(USDC, USDC_ORACLE, OracleSource.PythLazerStableCoin, 0));
    refused = assertThrows(IllegalArgumentException.class, () -> chainlinkX.resolve(roles(USDC, key(4))));
    assertTrue(refused.getMessage().contains(key(40) + " (source ChainlinkX), is one the Orca handler refuses"), refused.getMessage());

    final var unpriced = key(2);
    final var unpricedOracle = key(22);
    final var resolver = over(
        meta(unpriced, unpricedOracle, OracleSource.Pyth1K, 0),
        meta(USDC, USDC_ORACLE, OracleSource.PythLazerStableCoin, 0),
        meta(key(3), key(33), OracleSource.LstPoolState, 0),
        meta(WSOL, key(44), OracleSource.LstPoolState, 0)
    );
    refused = assertThrows(IllegalArgumentException.class, () -> resolver.resolve(roles(USDC, unpriced)));
    assertEquals(
        "The oracle of mint " + unpriced + ", " + unpricedOracle + " (source Pyth1K), has no denomination the protocol prices.",
        refused.getMessage()
    );
    final var legacyPush = over(meta(key(5), key(55), OracleSource.Pyth, 0), meta(USDC, USDC_ORACLE, OracleSource.PythLazerStableCoin, 0));
    refused = assertThrows(IllegalArgumentException.class, () -> legacyPush.resolve(roles(key(5), USDC)));
    assertTrue(refused.getMessage().contains("(source Pyth), has no denomination"), refused.getMessage());

    // the conversion needs the wrapped SOL oracle in USD: here it is SOL-priced
    refused = assertThrows(IllegalArgumentException.class, () -> resolver.resolve(roles(key(3), USDC)));
    assertEquals("The wrapped SOL oracle " + key(44) + " (source LstPoolState) prices in SOL, not the USD the handler converts through.", refused.getMessage());

    // here it is not registered at all
    final var noWsol = over(meta(USDC, USDC_ORACLE, OracleSource.PythLazerStableCoin, 0), meta(key(3), key(33), OracleSource.LstPoolState, 0));
    refused = assertThrows(IllegalArgumentException.class, () -> noWsol.resolve(roles(key(3), USDC)));
    assertEquals(
        "The global configuration registers no oracle for the wrapped SOL mint " + WSOL + ", which the SOL/USD oracle is registered under.",
        refused.getMessage()
    );

    // and here it is a Scope-read feed, which the SOL/USD path fails differently from a pool mint
    final var scopeWsol = over(meta(USDC, USDC_ORACLE, OracleSource.PythLazerStableCoin, 0), meta(key(3), key(33), OracleSource.LstPoolState, 0), meta(WSOL, key(45), OracleSource.ChainlinkRWA, 0));
    refused = assertThrows(IllegalArgumentException.class, () -> scopeWsol.resolve(roles(key(3), USDC)));
    assertTrue(refused.getMessage().endsWith("is a Scope-read feed the handler does not price as the SOL/USD oracle (InvalidPricingOracle: no Scope index)."), refused.getMessage());

    // the selection rule's own refusals: deprecated only, and a same-source tie
    final var deprecated = over(meta(key(6), key(66), OracleSource.PythLazer, -1), meta(USDC, USDC_ORACLE, OracleSource.PythLazerStableCoin, 0));
    refused = assertThrows(IllegalArgumentException.class, () -> deprecated.resolve(roles(key(6), USDC)));
    assertEquals("Every registration of mint " + key(6) + " is deprecated (1 of them).", refused.getMessage());
    final var tied = over(meta(key(7), key(71), OracleSource.PythLazer, 0), meta(key(7), key(72), OracleSource.PythLazer, 0), meta(USDC, USDC_ORACLE, OracleSource.PythLazerStableCoin, 0));
    refused = assertThrows(IllegalArgumentException.class, () -> tied.resolve(roles(USDC, key(7))));
    assertEquals(
        "The registrations of mint " + key(7) + " tie at priority 0 under PythLazer (" + key(71) + ", " + key(72) + "); nothing selects between them.",
        refused.getMessage()
    );
  }

  /// The roles must be the handlers' roles: a required SOL oracle where the handler reads none is refused
  /// rather than placed as a stray native account, a SOL role before any asset role, a second SOL role and
  /// an asset role after the SOL role are refused, an asset role naming other than one mint is refused,
  /// and a role this resolver does not serve is refused by name.
  @Test
  void theRolesMustBeTheHandlersRoles() {
    final var required = new Role(SOL_USD_ORACLE, List.of(), false);
    var refused = assertThrows(IllegalArgumentException.class, () -> recorded().resolve(List.of(asset(USDC), asset(WSOL), required)));
    assertEquals(
        "sol_usd_oracle is required, but the asset oracles all price in USD; the handler reads no SOL oracle then, and one supplied would reach it as a native remaining account.",
        refused.getMessage()
    );

    refused = assertThrows(IllegalArgumentException.class, () -> recorded().resolve(List.of(SOL_USD)));
    assertEquals("sol_usd_oracle depends on the asset_oracle roles before it, and none precedes it.", refused.getMessage());
    refused = assertThrows(IllegalArgumentException.class, () -> recorded().resolve(List.of(SOL_USD, asset(USDC))));
    assertEquals("asset_oracle after sol_usd_oracle; the handler reads the SOL oracle last.", refused.getMessage());

    final var jito = key(1);
    final var mixed = over(meta(jito, key(11), OracleSource.LstPoolState, 0), meta(USDC, USDC_ORACLE, OracleSource.PythLazerStableCoin, 0), meta(WSOL, WSOL_ORACLE, OracleSource.PythLazer, 0));
    refused = assertThrows(IllegalArgumentException.class, () -> mixed.resolve(List.of(asset(jito), asset(USDC), SOL_USD, SOL_USD)));
    assertEquals("A second sol_usd_oracle; the handler reads at most one.", refused.getMessage());
    refused = assertThrows(IllegalArgumentException.class, () -> mixed.resolve(List.of(asset(jito), SOL_USD, asset(USDC))));
    assertEquals("asset_oracle after sol_usd_oracle; the handler reads the SOL oracle last.", refused.getMessage());

    refused = assertThrows(IllegalArgumentException.class, () -> recorded().resolve(List.of(new Role(ASSET_ORACLE, List.of(USDC, WSOL), false))));
    assertTrue(refused.getMessage().contains("names 2 source accounts"), refused.getMessage());

    refused = assertThrows(IllegalArgumentException.class, () -> recorded().resolve(List.of(asset(USDC), new Role("loopscale_strategy_market", List.of(), false))));
    assertTrue(refused.getMessage().contains("Role loopscale_strategy_market is not one"), refused.getMessage());

    assertTrue(OrcaOracleResolver.serves(roles(USDC, WSOL)));
    assertTrue(OrcaOracleResolver.serves(List.of(asset(USDC))));
    assertFalse(OrcaOracleResolver.serves(List.of()));
    assertFalse(OrcaOracleResolver.serves(List.of(asset(USDC), new Role("loopscale_strategy_market", List.of(), false))));
  }

  /// A Kamino reserve chosen as an oracle is reported with the mint it prices, once per role that chose
  /// it, beside the accounts; the transaction refreshes it in front of the handler.
  @Test
  void aKaminoReserveOracleIsReportedWithItsMintForTheRefresh() {
    final var reserve = key(5);
    final var usdcReserve = key(6);
    final var resolver = over(
        meta(WSOL, reserve, OracleSource.KaminoReserve, 0),
        meta(USDC, usdcReserve, OracleSource.KaminoReserve, 0),
        meta(key(3), key(33), OracleSource.LstPoolState, 0)
    );
    var resolved = resolver.resolve(roles(WSOL, USDC));
    assertEquals(List.of(reserve, usdcReserve), resolved.accounts());
    assertEquals(List.of(new ReserveOracle(reserve, WSOL), new ReserveOracle(usdcReserve, USDC)), resolved.kaminoReserves());

    // the same reserve pricing both roles, and again as the SOL/USD oracle
    resolved = resolver.resolve(roles(WSOL, WSOL));
    assertEquals(List.of(reserve, reserve), resolved.accounts());
    assertEquals(List.of(new ReserveOracle(reserve, WSOL), new ReserveOracle(reserve, WSOL)), resolved.kaminoReserves());
    final var converted = resolver.resolve(roles(key(3), WSOL));
    assertEquals(List.of(key(33), reserve, reserve), converted.accounts(), "the SOL-priced side converts through the wrapped SOL reserve oracle");
    assertEquals(List.of(new ReserveOracle(reserve, WSOL), new ReserveOracle(reserve, WSOL)), converted.kaminoReserves());

    assertThrows(UnsupportedOperationException.class, () -> converted.accounts().add(reserve));
    assertThrows(UnsupportedOperationException.class, () -> converted.kaminoReserves().add(new ReserveOracle(reserve, WSOL)));
  }
}
