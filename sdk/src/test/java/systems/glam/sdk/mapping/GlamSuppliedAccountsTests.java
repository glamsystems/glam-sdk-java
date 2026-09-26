package systems.glam.sdk.mapping;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.idl.clients.kamino.KaminoAccounts;
import software.sava.idl.clients.loopscale.LoopscaleAccounts;
import systems.glam.ix.proxy.SuppliedAccountsRequest;
import systems.glam.ix.proxy.SuppliedAccountsRequest.Role;
import systems.glam.sdk.GlamAccounts;
import systems.glam.sdk.GlamVaultAccounts;
import systems.glam.sdk.idl.programs.glam.config.gen.types.AssetMeta;
import systems.glam.sdk.idl.programs.glam.config.gen.types.OracleSource;
import systems.glam.sdk.idl.programs.glam.jupiter.KaminoReserveRefresh;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;
import static systems.glam.sdk.mapping.LoopscaleStrategyMarketResolverTests.params;
import static systems.glam.sdk.mapping.LoopscaleStrategyMarketResolverTests.updateStrategy;
import static systems.glam.sdk.tests.MappingFixtures.*;

final class GlamSuppliedAccountsTests {

  private static final PublicKey FEE_PAYER = fromBase58Encoded("F1oQY1jbdiJyxxeeuMBF2NsUckboyWo6TSXNqzJbrhxs");
  private static final PublicKey STATE_KEY = fromBase58Encoded("9fkan2jCsS7Xq3fLqgxgZT5pDCbj2MhQ5MAoEKSHrcAT");
  private static final PublicKey ORCA_PROXY = GlamAccounts.MAIN_NET_STAGING.orcaIntegrationProgram();
  private static final PublicKey WHIRLPOOL = fromBase58Encoded("whirLbMiicVdio4qvUfM5KAg6Ct8VwpYzGff3uctyCc");
  private static final PublicKey LOOPSCALE = LoopscaleAccounts.MAIN_NET.loopscaleProgram();
  private static final List<Role> MARKET_ROLE = List.of(new Role(LoopscaleStrategyMarketResolver.LOOPSCALE_STRATEGY_MARKET, List.of(), false));

  private static SuppliedAccountsRequest orcaRequest(final PublicKey mintA, final PublicKey mintB) {
    return new SuppliedAccountsRequest(
        ORCA_PROXY, WHIRLPOOL, "increase_liquidity_v2", "increase_liquidity_v2",
        List.of(
            new Role(OrcaOracleResolver.ASSET_ORACLE, List.of(mintA), false),
            new Role(OrcaOracleResolver.ASSET_ORACLE, List.of(mintB), false),
            new Role(OrcaOracleResolver.SOL_USD_ORACLE, List.of(), true)
        ),
        updateStrategy(STRATEGY_KEY, null)
    );
  }

  private static SuppliedAccountsRequest updateRequest(final PublicKey market) {
    return new SuppliedAccountsRequest(
        GlamAccounts.MAIN_NET_STAGING.loopscaleIntegrationProgram(), LOOPSCALE, "update_strategy", "update_strategy",
        MARKET_ROLE, updateStrategy(STRATEGY_KEY, params(market))
    );
  }

  private static GlamSuppliedAccounts over(final Function<PublicKey, PublicKey> strategyMarkets, final AssetMeta... metas) {
    return new GlamSuppliedAccounts(
        new OrcaOracleResolver(RegisteredOracles.of(metas), WSOL),
        new LoopscaleStrategyMarketResolver(LOOPSCALE, strategyMarkets)
    );
  }

  /// The supplier answers the oracle roles from the global configuration and the market role from the
  /// update or the strategy, and answers null for a role set it does not serve or cannot serve.
  @Test
  void rolesAreDispatchedToTheirResolver() {
    final var supplier = GlamSuppliedAccounts.create(globalConfig(), LoopscaleStrategyMarketResolver.strategyMarkets(Map.of(STRATEGY_KEY, strategy())));
    assertEquals(List.of(USDC_ORACLE, WSOL_ORACLE), supplier.apply(orcaRequest(USDC, WSOL)));
    assertEquals(List.of(STRATEGY_MARKET), supplier.apply(updateRequest(null)));
    assertEquals(List.of(STRATEGY_MARKET), supplier.apply(updateRequest(STRATEGY_MARKET)));
    final var change = assertThrows(IllegalStateException.class, () -> supplier.apply(updateRequest(key(8))));
    assertTrue(change.getMessage().contains("stores " + STRATEGY_MARKET), "a market change the deployed program cannot make is refused");

    final var unknown = new SuppliedAccountsRequest(ORCA_PROXY, WHIRLPOOL, "swap_v2", "swap_v2",
        List.of(new Role("pool_oracle", List.of(), false)), updateStrategy(STRATEGY_KEY, null));
    assertNull(supplier.apply(unknown), "a role the supplier does not serve is not invented");
    final var none = new SuppliedAccountsRequest(ORCA_PROXY, WHIRLPOOL, "swap_v2", "swap_v2", List.of(), updateStrategy(STRATEGY_KEY, null));
    assertNull(supplier.apply(none));

    // a Loopscale update_strategy asking for a role that is not the market role is not served either
    final var otherRole = new SuppliedAccountsRequest(
        GlamAccounts.MAIN_NET_STAGING.loopscaleIntegrationProgram(), LOOPSCALE, "update_strategy", "update_strategy",
        List.of(new Role("strategy_authority", List.of(), false)), updateStrategy(STRATEGY_KEY, params(null))
    );
    assertNull(supplier.apply(otherRole));

    // without a strategy lookup, an update naming its market is served and one naming none is not
    final var noLookup = GlamSuppliedAccounts.create(globalConfig());
    assertEquals(List.of(key(8)), noLookup.apply(updateRequest(key(8))));
    assertNull(noLookup.apply(updateRequest(null)));
  }

  /// The Kamino reserves chosen as oracles are carried to the transaction builder with the mint each
  /// prices: listed once each in the order first chosen across every instruction the supplier answered,
  /// none from an instruction it refused, forgotten by a reset, and turned into one refresh over the
  /// reserves the builder decoded, each checked to price its mint; none chosen, no refresh.
  @Test
  void chosenKaminoReservesAreCarriedToTheRefresh() {
    final var solReserve = key(5);
    final var usdcReserve = key(6);
    final var supplier = over(null,
        meta(WSOL, solReserve, OracleSource.KaminoReserve, 0),
        meta(USDC, usdcReserve, OracleSource.KaminoReserve, 0),
        meta(MSOL, MSOL_ORACLE, OracleSource.PythLazer, 0)
    );
    final var invokedLending = KaminoAccounts.MAIN_NET.invokedKLendProgram();
    final var lendingProgram = KaminoAccounts.MAIN_NET.kLendProgram();
    assertEquals(List.of(), supplier.kaminoReserves());
    assertNull(supplier.kaminoReserveRefresh(invokedLending, reserve -> null), "nothing chosen, nothing to refresh");

    // a refused instruction leaves no reserve behind: the wrapped SOL reserve is chosen before the
    // unregistered mint refuses the request
    assertThrows(IllegalArgumentException.class, () -> supplier.apply(orcaRequest(WSOL, key(99))));
    assertEquals(List.of(), supplier.kaminoReserves());

    assertEquals(List.of(MSOL_ORACLE, usdcReserve), supplier.apply(orcaRequest(MSOL, USDC)));
    assertEquals(List.of(solReserve, usdcReserve), supplier.apply(orcaRequest(WSOL, USDC)));
    assertEquals(List.of(usdcReserve, solReserve), supplier.kaminoReserves(), "first chosen first, each once");

    final var decoded = new ArrayList<PublicKey>();
    final Function<PublicKey, KaminoReserveRefresh> decodedReserves = reserve -> {
      decoded.add(reserve);
      return new KaminoReserveRefresh(reserve, key(70), reserve.equals(usdcReserve) ? USDC : WSOL, null, null, null, key(72));
    };
    final var refresh = supplier.kaminoReserveRefresh(invokedLending, decodedReserves);
    assertEquals(List.of(usdcReserve, solReserve), decoded);
    assertEquals(lendingProgram, refresh.programId().publicKey());
    final var expected = new ArrayList<>(new KaminoReserveRefresh(usdcReserve, key(70), USDC, null, null, null, key(72)).accounts(lendingProgram));
    expected.addAll(new KaminoReserveRefresh(solReserve, key(70), WSOL, null, null, null, key(72)).accounts(lendingProgram));
    assertEquals(expected, refresh.accounts());

    var refused = assertThrows(IllegalStateException.class, () -> supplier.kaminoReserveRefresh(invokedLending, reserve -> null));
    assertEquals("Reserve " + usdcReserve + " was chosen as an oracle but not decoded; fetch it before building the transaction.", refused.getMessage());
    refused = assertThrows(IllegalStateException.class, () -> supplier.kaminoReserveRefresh(invokedLending,
        reserve -> new KaminoReserveRefresh(reserve, key(70), MSOL, null, null, null, key(72))));
    assertEquals("Reserve " + usdcReserve + " was chosen to price " + USDC + ", but its liquidity mint is " + MSOL + "; the handler would refuse it (InvalidPricingOracle).", refused.getMessage());
    assertThrows(UnsupportedOperationException.class, () -> supplier.kaminoReserves().add(key(1)));

    supplier.reset();
    assertEquals(List.of(), supplier.kaminoReserves());
    assertNull(supplier.kaminoReserveRefresh(invokedLending, decodedReserves));
    assertEquals(List.of(solReserve, usdcReserve), supplier.apply(orcaRequest(WSOL, USDC)));
    assertEquals(List.of(solReserve, usdcReserve), supplier.kaminoReserves(), "chosen afresh after the reset");
  }

  /// A vault's mapping context carries the supplier beside the state, vault, signer and integration
  /// authorities; the plain context supplies none.
  @Test
  void theVaultContextCarriesTheSupplier() {
    final var vault = GlamVaultAccounts.createAccounts(GlamAccounts.MAIN_NET_STAGING, FEE_PAYER, STATE_KEY);
    final var supplier = GlamSuppliedAccounts.create(globalConfig());
    final var context = vault.mappingContext(supplier);
    assertSame(supplier, context.suppliedAccounts());
    final var plain = vault.mappingContext();
    assertNull(plain.suppliedAccounts());
    assertEquals(plain.glamState(), context.glamState());
    assertEquals(STATE_KEY, context.glamState());
    assertEquals(vault.vaultPublicKey(), context.glamVault());
    assertEquals(FEE_PAYER, context.glamSigner());
    assertEquals(
        GlamAccounts.MAIN_NET_STAGING.readOrcaIntegrationAuthority().publicKey(),
        context.integrationAuthority().apply(GlamAccounts.MAIN_NET_STAGING.orcaIntegrationProgram())
    );
    assertEquals(SolanaAccounts.MAIN_NET.wrappedSolTokenMint(), WSOL, "the resolver converts through the wrapped SOL mint's oracle");
  }
}
