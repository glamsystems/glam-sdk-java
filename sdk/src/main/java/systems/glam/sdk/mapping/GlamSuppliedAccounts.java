package systems.glam.sdk.mapping;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.loopscale.LoopscaleAccounts;
import systems.glam.ix.proxy.SuppliedAccountsRequest;
import systems.glam.sdk.idl.programs.glam.config.gen.types.GlobalConfig;
import systems.glam.sdk.idl.programs.glam.jupiter.KaminoReserveRefresh;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;

/// The accounts a GLAM handler reads from its remaining accounts that a native instruction never carries,
/// resolved for the ix-mapper's supplied-account roles: the Orca liquidity handlers' oracle prefix from the
/// global configuration ([OrcaOracleResolver]) and the Loopscale `update_strategy` market from the update or
/// the strategy account ([LoopscaleStrategyMarketResolver]). Wire it through
/// [systems.glam.sdk.GlamVaultAccounts#mappingContext(Function)].
///
/// One instance serves one transaction build and is not thread-safe: it records the Kamino reserves it chose
/// as oracles, which the transaction must refresh in front of the mapped instructions
/// ([#kaminoReserves()], [#kaminoReserveRefresh]); [#reset()] forgets them between builds when one instance
/// serves several. A reserve is recorded when the supplier answers, so an instruction the mapper refuses
/// after that (its remaining-accounts rule) leaves its reserves recorded. A role it does not serve is
/// answered with null, which the mapper refuses with reason `context`; it invents no account.
public final class GlamSuppliedAccounts implements Function<SuppliedAccountsRequest, List<PublicKey>> {

  private final OrcaOracleResolver orca;
  private final LoopscaleStrategyMarketResolver loopscale;
  /// each reserve chosen, in the order first chosen, and the mint it was chosen for
  private final LinkedHashMap<PublicKey, PublicKey> kaminoReserves;

  public GlamSuppliedAccounts(final OrcaOracleResolver orca, final LoopscaleStrategyMarketResolver loopscale) {
    this.orca = orca;
    this.loopscale = loopscale;
    this.kaminoReserves = new LinkedHashMap<>();
  }

  /// Over the mainnet programs, which both deployments price and lend through.
  ///
  /// @param loopscaleStrategyMarkets the market each strategy stores, by strategy address, from accounts
  ///                                 fetched before mapping (see [LoopscaleStrategyMarketResolver#strategyMarkets(java.util.Map)]);
  ///                                 null serves only the updates that name their market
  public static GlamSuppliedAccounts create(final GlobalConfig globalConfig,
                                            final Function<PublicKey, PublicKey> loopscaleStrategyMarkets) {
    return new GlamSuppliedAccounts(
        new OrcaOracleResolver(RegisteredOracles.of(globalConfig), SolanaAccounts.MAIN_NET.wrappedSolTokenMint()),
        new LoopscaleStrategyMarketResolver(LoopscaleAccounts.MAIN_NET.loopscaleProgram(), loopscaleStrategyMarkets)
    );
  }

  /// [#create(GlobalConfig, Function)] reading no strategy account.
  public static GlamSuppliedAccounts create(final GlobalConfig globalConfig) {
    return create(globalConfig, null);
  }

  @Override
  public List<PublicKey> apply(final SuppliedAccountsRequest request) {
    final var roles = request.roles();
    if (OrcaOracleResolver.serves(roles)) {
      final var resolved = orca.resolve(roles);
      for (final var reserve : resolved.kaminoReserves()) {
        kaminoReserves.putIfAbsent(reserve.reserve(), reserve.mint());
      }
      return resolved.accounts();
    }
    if (LoopscaleStrategyMarketResolver.serves(roles)) {
      return loopscale.resolve(request);
    }
    return null;
  }

  /// The Kamino reserves chosen as oracles since construction or the last [#reset()], in the order first
  /// chosen, each once.
  public List<PublicKey> kaminoReserves() {
    return List.copyOf(kaminoReserves.keySet());
  }

  /// Forgets the reserves chosen so far: between transaction builds when one instance serves several.
  public void reset() {
    kaminoReserves.clear();
  }

  /// One `refresh_reserves_batch` over [#kaminoReserves()], from the reserve accounts the caller fetched and
  /// decoded, to send in front of the mapped instructions; null when no reserve was chosen. Each reserve's
  /// liquidity mint must be the mint it was chosen to price, as the handler's oracle check requires.
  ///
  /// @throws IllegalStateException if a chosen reserve was not decoded, or prices another mint
  public Instruction kaminoReserveRefresh(final AccountMeta invokedLendingProgram,
                                          final Function<PublicKey, KaminoReserveRefresh> decodedReserves) {
    if (kaminoReserves.isEmpty()) {
      return null;
    }
    final var refreshes = new ArrayList<KaminoReserveRefresh>(kaminoReserves.size());
    for (final var chosen : kaminoReserves.entrySet()) {
      final var reserve = chosen.getKey();
      final var refresh = decodedReserves.apply(reserve);
      if (refresh == null) {
        throw new IllegalStateException(
            "Reserve " + reserve + " was chosen as an oracle but not decoded; fetch it before building the transaction.");
      }
      final var mint = chosen.getValue();
      if (!mint.equals(refresh.liquidityMint())) {
        throw new IllegalStateException(
            "Reserve " + reserve + " was chosen to price " + mint + ", but its liquidity mint is " + refresh.liquidityMint()
                + "; the handler would refuse it (InvalidPricingOracle).");
      }
      refreshes.add(refresh);
    }
    return KaminoReserveRefresh.refreshInstruction(invokedLendingProgram, refreshes);
  }
}
