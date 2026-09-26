package systems.glam.sdk.mapping;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.loopscale.LoopscaleAccounts;
import software.sava.idl.clients.loopscale.gen.LoopscaleProgram;
import software.sava.idl.clients.loopscale.gen.types.CollateralTermsIndices;
import software.sava.idl.clients.loopscale.gen.types.MultiCollateralTermsUpdateParams;
import software.sava.idl.clients.loopscale.gen.types.Strategy;
import software.sava.idl.clients.loopscale.gen.types.UpdateStrategyParams;
import systems.glam.ix.proxy.SuppliedAccountsRequest;
import systems.glam.ix.proxy.SuppliedAccountsRequest.Role;
import systems.glam.sdk.GlamAccounts;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;
import static systems.glam.sdk.mapping.LoopscaleStrategyMarketResolver.*;
import static systems.glam.sdk.tests.MappingFixtures.*;

final class LoopscaleStrategyMarketResolverTests {

  private static final PublicKey LOOPSCALE = LoopscaleAccounts.MAIN_NET.loopscaleProgram();
  private static final PublicKey PROXY = GlamAccounts.MAIN_NET_STAGING.loopscaleIntegrationProgram();
  private static final Role MARKET_ROLE = new Role(LOOPSCALE_STRATEGY_MARKET, List.of(), false);
  /// Two collateral terms ahead of the params, so the market is read past variable-length data.
  private static final MultiCollateralTermsUpdateParams[] TERMS = {
      new MultiCollateralTermsUpdateParams(1_200L, new CollateralTermsIndices[]{new CollateralTermsIndices(1, 2), new CollateralTermsIndices(3, 4)}),
      new MultiCollateralTermsUpdateParams(900L, new CollateralTermsIndices[]{new CollateralTermsIndices(5, 6)})
  };

  static Instruction updateStrategy(final PublicKey strategy, final UpdateStrategyParams params) {
    return updateStrategy(key(42), key(43), key(41), strategy, params);
  }

  /// The native update: `admin` co-signs, `payer` pays, `lender` lends; the strategy's principal mint is
  /// the recorded strategy's, and two collateral terms precede the params.
  static Instruction updateStrategy(final PublicKey payer,
                                    final PublicKey lender,
                                    final PublicKey admin,
                                    final PublicKey strategy,
                                    final UpdateStrategyParams params) {
    final var solana = SolanaAccounts.MAIN_NET;
    return LoopscaleProgram.updateStrategy(
        LoopscaleAccounts.MAIN_NET.invokedLoopscaleProgram(),
        admin, payer, lender, strategy, STRATEGY_PRINCIPAL_MINT, key(46),
        solana.systemProgram(), solana.associatedTokenAccountProgram(), solana.tokenProgram(),
        key(49), key(50), LOOPSCALE,
        TERMS, params
    );
  }

  static UpdateStrategyParams params(final PublicKey marketInformation) {
    return new UpdateStrategyParams(
        Boolean.TRUE, OptionalLong.of(1L), OptionalLong.empty(), OptionalLong.empty(), OptionalLong.empty(),
        OptionalLong.of(5_000_000L), marketInformation, null
    );
  }

  private static SuppliedAccountsRequest request(final Instruction instruction) {
    return new SuppliedAccountsRequest(PROXY, LOOPSCALE, UPDATE_STRATEGY, UPDATE_STRATEGY, List.of(MARKET_ROLE), instruction);
  }

  private static LoopscaleStrategyMarketResolver recorded() {
    return new LoopscaleStrategyMarketResolver(LOOPSCALE, strategyMarkets(Map.of(STRATEGY_KEY, strategy())));
  }

  /// The market a strategy stores is read at the program's offset from the recorded account, from data
  /// carrying the strategy discriminator and reaching past the market, longer than today's strategy or not.
  @Test
  void theStoredMarketIsReadFromTheRecordedStrategy() {
    final var data = strategy();
    assertEquals(Strategy.BYTES, data.length);
    assertEquals(STRATEGY_MARKET, storedMarket(data));
    assertEquals(STRATEGY_PRINCIPAL_MINT, PublicKey.readPubKey(data, 42), "the fixture is the strategy the README describes");
    assertEquals(STRATEGY_MARKET, storedMarket(Arrays.copyOf(data, data.length + 64)), "a strategy grown by a later program version");
    assertEquals(STRATEGY_MARKET, storedMarket(Arrays.copyOf(data, MIN_STRATEGY_BYTES)), "the least the handler reads");
    assertEquals(300, MIN_STRATEGY_BYTES);

    final var lookup = strategyMarkets(Map.of(STRATEGY_KEY, data));
    assertEquals(STRATEGY_MARKET, lookup.apply(STRATEGY_KEY));
    assertNull(lookup.apply(key(1)), "a strategy that was not fetched");

    final var corrupted = data.clone();
    corrupted[0] ^= 1;
    var refused = assertThrows(IllegalArgumentException.class, () -> storedMarket(corrupted));
    assertEquals("The account does not carry the Loopscale strategy discriminator.", refused.getMessage());
    final var truncated = Arrays.copyOf(data, MIN_STRATEGY_BYTES - 1);
    refused = assertThrows(IllegalArgumentException.class, () -> storedMarket(truncated));
    assertEquals("A Loopscale strategy account stores its market at offset 268, so it holds at least 300 bytes, not 299.", refused.getMessage());
    refused = assertThrows(IllegalArgumentException.class, () -> storedMarket(null));
    assertTrue(refused.getMessage().endsWith("not null."), refused.getMessage());

    // a lookup names the strategy whose fetched account is not a strategy
    final var notAStrategy = strategyMarkets(Map.of(STRATEGY_KEY, corrupted));
    refused = assertThrows(IllegalArgumentException.class, () -> notAStrategy.apply(STRATEGY_KEY));
    assertEquals("The account fetched for strategy " + STRATEGY_KEY + " is not a strategy: The account does not carry the Loopscale strategy discriminator.", refused.getMessage());
  }

  /// An update that names a market in its params supplies that market: the strategy account is not
  /// consulted, and none need be fetched.
  @Test
  void anUpdateNamingAMarketSuppliesItWithoutTheStrategy() {
    final var named = key(7);
    final var resolver = new LoopscaleStrategyMarketResolver(LOOPSCALE, strategy -> {
      throw new AssertionError("the strategy account must not be consulted when the update names the market");
    });
    assertEquals(List.of(named), resolver.resolve(request(updateStrategy(STRATEGY_KEY, params(named)))));
    assertEquals(List.of(named), new LoopscaleStrategyMarketResolver(LOOPSCALE, null).resolve(request(updateStrategy(STRATEGY_KEY, params(named)))),
        "no lookup configured, and none needed");
  }

  /// An update naming no market, with params that leave it out or with no params at all, supplies the
  /// market the strategy account stores.
  @Test
  void anUpdateNamingNoMarketSuppliesTheStoredMarket() {
    final var resolver = recorded();
    assertEquals(List.of(STRATEGY_MARKET), resolver.resolve(request(updateStrategy(STRATEGY_KEY, params(null)))));
    assertEquals(List.of(STRATEGY_MARKET), resolver.resolve(request(updateStrategy(STRATEGY_KEY, null))));
  }

  /// A strategy whose account was not fetched before mapping refuses, naming the strategy and the fix.
  @Test
  void aStrategyNotFetchedRefusesNamingIt() {
    final var resolver = new LoopscaleStrategyMarketResolver(LOOPSCALE, strategyMarkets(Map.of()));
    final var refused = assertThrows(IllegalStateException.class,
        () -> resolver.resolve(request(updateStrategy(STRATEGY_KEY, params(null)))));
    assertEquals(
        "The market of strategy " + STRATEGY_KEY + " is not known: fetch the strategy account before mapping, or name market_information in the update.",
        refused.getMessage()
    );
  }

  /// Entries the resolver does not serve are answered null, never invented: another program, another
  /// instruction, and an update naming no market when no lookup is configured; the role set it serves is
  /// the one market role alone.
  @Test
  void entriesTheResolverDoesNotServeAreAnsweredNull() {
    final var instruction = updateStrategy(STRATEGY_KEY, params(null));
    assertNull(new LoopscaleStrategyMarketResolver(LOOPSCALE, null).resolve(request(instruction)));
    assertNull(recorded().resolve(new SuppliedAccountsRequest(PROXY, key(2), UPDATE_STRATEGY, UPDATE_STRATEGY, List.of(MARKET_ROLE), instruction)));
    assertNull(recorded().resolve(new SuppliedAccountsRequest(PROXY, LOOPSCALE, "create_strategy", "create_strategy", List.of(MARKET_ROLE), instruction)));

    assertTrue(serves(List.of(MARKET_ROLE)));
    assertFalse(serves(List.of()));
    assertFalse(serves(List.of(MARKET_ROLE, MARKET_ROLE)));
    assertFalse(serves(List.of(new Role("asset_oracle", List.of(key(1)), false))));
  }

  /// The strategy is read at position three of the native instruction, as the Loopscale program lays it
  /// out; an instruction too short to carry it, with no data, or with data that is not an update, is
  /// refused.
  @Test
  void theStrategySitsAtPositionThreeOfTheNativeInstruction() {
    final var instruction = updateStrategy(STRATEGY_KEY, params(null));
    assertEquals(STRATEGY_KEY, instruction.accounts().get(STRATEGY_POSITION).publicKey());
    assertEquals(3, STRATEGY_POSITION);

    final var resolver = recorded();
    final var truncated = Instruction.createInstruction(instruction.programId(), instruction.accounts().subList(0, 3), instruction.data());
    var refused = assertThrows(IllegalArgumentException.class, () -> resolver.resolve(request(truncated)));
    assertEquals("update_strategy carries 3 accounts; the strategy sits at position 3.", refused.getMessage());

    final var dataless = Instruction.createInstruction(instruction.programId(), instruction.accounts(), new byte[0]);
    refused = assertThrows(IllegalArgumentException.class, () -> resolver.resolve(request(dataless)));
    assertEquals("update_strategy carries no data to read the update from.", refused.getMessage());

    final var cut = Instruction.createInstruction(instruction.programId(), instruction.accounts(), Arrays.copyOf(instruction.data(), 10));
    refused = assertThrows(IllegalArgumentException.class, () -> resolver.resolve(request(cut)));
    assertTrue(refused.getMessage().startsWith("update_strategy's data cannot be read as an update: "), refused.getMessage());
  }
}
