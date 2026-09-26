package systems.glam.sdk.mapping;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.loopscale.gen.LoopscaleProgram;
import software.sava.idl.clients.loopscale.gen.types.Strategy;
import systems.glam.ix.proxy.SuppliedAccountsRequest;

import software.sava.core.tx.Instruction;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/// Resolves the market the Loopscale `update_strategy` handler checks its first remaining account against
/// before it co-signs: the `market_information` the update names in its params when it names one, else the
/// market the strategy account stores. The strategy account is read through a lookup the caller builds from
/// accounts fetched before mapping, since the mapper's supplier is synchronous; [#strategyMarkets(Map)]
/// builds one from fetched account data, and [#storedMarket(byte[])] reads the market a strategy stores.
///
/// The deployed Loopscale program keeps a strategy's stored market whatever the update names, and the
/// handler's check after the CPI then refuses the update (ProtocolPolicyViolation), so only the stored
/// market ever works: when the lookup knows the stored market and the update names another, the resolver
/// refuses up front rather than supply a prefix the transaction cannot use.
public final class LoopscaleStrategyMarketResolver {

  public static final String LOOPSCALE_STRATEGY_MARKET = "loopscale_strategy_market";
  public static final String UPDATE_STRATEGY = "update_strategy";
  /// The strategy's position in Loopscale's `update_strategy`: the protocol admin, the payer, the lender,
  /// then the strategy. The generated document names it as the role's one `of` position; the position
  /// is the fallback for a document that names none.
  public static final int STRATEGY_POSITION = 3;
  /// The least a strategy account holds to store a market at the program's offset.
  public static final int MIN_STRATEGY_BYTES = Strategy.MARKET_INFORMATION_OFFSET + PublicKey.PUBLIC_KEY_LENGTH;

  private final PublicKey loopscaleProgram;
  private final Function<PublicKey, PublicKey> strategyMarkets;

  /// @param strategyMarkets the market each strategy stores, by strategy address, null for one not fetched;
  ///                        null when the caller reads no strategy account, which leaves an update that
  ///                        names no market unserved
  public LoopscaleStrategyMarketResolver(final PublicKey loopscaleProgram,
                                         final Function<PublicKey, PublicKey> strategyMarkets) {
    this.loopscaleProgram = loopscaleProgram;
    this.strategyMarkets = strategyMarkets;
  }

  /// True when `roles` is the one market role.
  public static boolean serves(final List<SuppliedAccountsRequest.Role> roles) {
    return roles.size() == 1 && LOOPSCALE_STRATEGY_MARKET.equals(roles.getFirst().role());
  }

  /// The market for the request: the one the update names, else the one the strategy stores; null when
  /// this resolver serves no such entry, because the request is not Loopscale's `update_strategy` or the
  /// update names no market and no strategy lookup was configured. A named market is served without a
  /// lookup, or when the lookup does not know the strategy; a lookup that knows a different stored market
  /// refuses it.
  ///
  /// @throws IllegalArgumentException when the instruction's data cannot be read as an update or it
  ///                                  carries no strategy
  /// @throws IllegalStateException    when the strategy's account was not fetched before mapping, or the
  ///                                  update names a market other than the one the strategy stores
  public List<PublicKey> resolve(final SuppliedAccountsRequest request) {
    if (!loopscaleProgram.equals(request.program()) || !UPDATE_STRATEGY.equals(request.source())) {
      return null;
    }
    final var instruction = request.instruction();
    final LoopscaleProgram.UpdateStrategyIxData data;
    try {
      data = LoopscaleProgram.UpdateStrategyIxData.read(instruction);
    } catch (final RuntimeException e) {
      throw new IllegalArgumentException("update_strategy's data cannot be read as an update: " + e, e);
    }
    if (data == null) {
      throw new IllegalArgumentException("update_strategy carries no data to read the update from.");
    }
    final var params = data.params();
    final var named = params == null ? null : params.marketInformation();
    if (strategyMarkets == null) {
      return named == null ? null : List.of(named);
    }
    final var strategy = strategy(request.roles().getFirst(), instruction);
    final var stored = strategyMarkets.apply(strategy);
    if (named != null) {
      if (stored != null && !stored.equals(named)) {
        throw new IllegalStateException(
            "update_strategy names market " + named + ", but strategy " + strategy + " stores " + stored
                + "; the deployed Loopscale program keeps the stored market and the handler then refuses the update (ProtocolPolicyViolation). Name the stored market, or none.");
      }
      return List.of(named);
    }
    if (stored == null) {
      throw new IllegalStateException(
          "The market of strategy " + strategy + " is not known: fetch the strategy account before mapping, or name market_information in the update.");
    }
    return List.of(stored);
  }

  /// The strategy the role names at its `of` position, else the one at the instruction's strategy position.
  private static PublicKey strategy(final SuppliedAccountsRequest.Role role, final Instruction instruction) {
    final var of = role.of();
    if (of.size() == 1) {
      return of.getFirst();
    }
    final var accounts = instruction.accounts();
    if (accounts.size() <= STRATEGY_POSITION) {
      throw new IllegalArgumentException(
          "update_strategy carries " + accounts.size() + " accounts; the strategy sits at position " + STRATEGY_POSITION + '.');
    }
    return accounts.get(STRATEGY_POSITION).publicKey();
  }

  /// The market a strategy account stores, at the offset the Loopscale program reads it from; the data must
  /// carry the strategy discriminator and reach past the market, as the GLAM handler requires, and may be
  /// longer than today's strategy.
  ///
  /// @throws IllegalArgumentException if the data is not a strategy account's, by length or discriminator
  public static PublicKey storedMarket(final byte[] strategyData) {
    if (strategyData == null || strategyData.length < MIN_STRATEGY_BYTES) {
      throw new IllegalArgumentException(
          "A Loopscale strategy account stores its market at offset " + Strategy.MARKET_INFORMATION_OFFSET
              + ", so it holds at least " + MIN_STRATEGY_BYTES + " bytes, not "
              + (strategyData == null ? "null" : String.valueOf(strategyData.length)) + '.');
    }
    if (!Strategy.DISCRIMINATOR.equals(strategyData, 0)) {
      throw new IllegalArgumentException("The account does not carry the Loopscale strategy discriminator.");
    }
    return PublicKey.readPubKey(strategyData, Strategy.MARKET_INFORMATION_OFFSET);
  }

  /// A lookup over strategy accounts fetched before mapping, keyed by address; a strategy absent from the map
  /// resolves to null, and one whose data is not a strategy's is refused naming the address.
  public static Function<PublicKey, PublicKey> strategyMarkets(final Map<PublicKey, byte[]> strategyAccounts) {
    return strategy -> {
      final var data = strategyAccounts.get(strategy);
      if (data == null) {
        return null;
      }
      try {
        return storedMarket(data);
      } catch (final IllegalArgumentException e) {
        throw new IllegalArgumentException("The account fetched for strategy " + strategy + " is not a strategy: " + e.getMessage(), e);
      }
    };
  }
}
