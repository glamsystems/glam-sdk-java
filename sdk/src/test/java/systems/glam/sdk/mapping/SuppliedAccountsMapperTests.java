package systems.glam.sdk.mapping;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.loopscale.LoopscaleAccounts;
import software.sava.idl.clients.orca.OrcaAccounts;
import software.sava.idl.clients.orca.whirlpools.gen.WhirlpoolProgram;
import software.sava.idl.clients.orca.whirlpools.gen.types.IncreaseLiquidityMethod;
import software.sava.idl.clients.orca.whirlpools.gen.types.RepositionLiquidityMethod;
import systems.glam.ix.proxy.InstructionMapper;
import systems.glam.ix.proxy.MapResult;
import systems.glam.ix.proxy.MappingDocumentParser;
import systems.glam.ix.proxy.UnsupportedReason;
import systems.glam.sdk.GlamAccounts;
import systems.glam.sdk.GlamVaultAccounts;
import systems.glam.sdk.idl.programs.glam.config.gen.types.AssetMeta;
import systems.glam.sdk.idl.programs.glam.config.gen.types.OracleSource;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;
import static software.sava.core.accounts.meta.AccountMeta.*;
import static systems.glam.sdk.tests.MappingFixtures.*;
import static systems.glam.sdk.mapping.LoopscaleStrategyMarketResolver.strategyMarkets;
import static systems.glam.sdk.mapping.LoopscaleStrategyMarketResolverTests.params;
import static systems.glam.sdk.mapping.LoopscaleStrategyMarketResolverTests.updateStrategy;

/// The five entries mapped through the mapper with the supplier, over hand-written staging documents
/// (`supplied-accounts/staging`) that list the entries as the generated set will once the monorepo flips
/// them, with the source positions of the managed IDL the generator reads: the seats, then the appended
/// global configuration, then the supplied accounts read-only, then the native extras in place. The
/// bundled whirlpool builders append a `whirlpool_program` account the managed IDL does not list, so it
/// rides as the first native extra.
final class SuppliedAccountsMapperTests {

  private static final PublicKey FEE_PAYER = fromBase58Encoded("F1oQY1jbdiJyxxeeuMBF2NsUckboyWo6TSXNqzJbrhxs");
  private static final PublicKey STATE_KEY = fromBase58Encoded("9fkan2jCsS7Xq3fLqgxgZT5pDCbj2MhQ5MAoEKSHrcAT");
  private static final GlamAccounts STAGING = GlamAccounts.MAIN_NET_STAGING;
  private static final SolanaAccounts SOLANA = SolanaAccounts.MAIN_NET;
  private static final PublicKey WHIRLPOOL_PROGRAM = OrcaAccounts.MAIN_NET.invokedWhirlpoolProgram().publicKey();
  private static final PublicKey LOOPSCALE_PROGRAM = LoopscaleAccounts.MAIN_NET.loopscaleProgram();
  private static final PublicKey WHIRLPOOL = key(30);
  private static final List<AccountMeta> EXTRAS = List.of(createWrite(key(60)), createRead(key(61)));

  private static InstructionMapper mapper() {
    return STAGING.createMapper(List.of(
        MappingDocumentParser.parse(read("supplied-accounts/staging/" + WHIRLPOOL_PROGRAM + ".json"), "whirlpool"),
        MappingDocumentParser.parse(read("supplied-accounts/staging/" + LOOPSCALE_PROGRAM + ".json"), "loopscale")
    ));
  }

  private static GlamVaultAccounts vault() {
    return GlamVaultAccounts.createAccounts(STAGING, FEE_PAYER, STATE_KEY);
  }

  private static GlamSuppliedAccounts recorded() {
    return GlamSuppliedAccounts.create(globalConfig(), strategyMarkets(Map.of(STRATEGY_KEY, strategy())));
  }

  private static Instruction increaseLiquidity(final PublicKey vault, final PublicKey mintA, final PublicKey mintB) {
    return WhirlpoolProgram.increaseLiquidityV2(
        OrcaAccounts.MAIN_NET.invokedWhirlpoolProgram(), SOLANA,
        WHIRLPOOL, SOLANA.tokenProgram(), SOLANA.tokenProgram(), vault, key(31), key(32), mintA, mintB,
        key(33), key(34), key(35), key(36), key(37), key(38), WHIRLPOOL_PROGRAM,
        BigInteger.valueOf(1_000L), 10L, 20L, null
    );
  }

  private static List<AccountMeta> liquiditySeats(final GlamVaultAccounts vault, final PublicKey mintA, final PublicKey mintB) {
    return List.of(
        createWrite(STATE_KEY),
        createWrite(vault.vaultPublicKey()),
        createWritableSigner(FEE_PAYER),
        createRead(STAGING.readOrcaIntegrationAuthority().publicKey()),
        createRead(WHIRLPOOL_PROGRAM),
        createRead(STAGING.protocolProgram()),
        createRead(SOLANA.systemProgram()),
        createWrite(WHIRLPOOL),
        createRead(SOLANA.tokenProgram()),
        createRead(SOLANA.tokenProgram()),
        createRead(SOLANA.memoProgramV2()),
        createWrite(key(31)),
        createRead(key(32)),
        createRead(mintA),
        createRead(mintB),
        createWrite(key(33)),
        createWrite(key(34)),
        createWrite(key(35)),
        createWrite(key(36)),
        createWrite(key(37)),
        createWrite(key(38)),
        createRead(GLOBAL_CONFIG_KEY)
    );
  }

  private static MapResult.Mapped mapped(final InstructionMapper mapper, final Instruction instruction, final GlamVaultAccounts vault, final GlamSuppliedAccounts supplier) {
    return assertInstanceOf(MapResult.Mapped.class, mapper.map(instruction, vault.mappingContext(supplier)));
  }

  /// increase_liquidity_v2 over two USD-priced mints: the twenty-one seats, the appended global
  /// configuration, the two oracles read-only in mint order, no SOL oracle, and the native extras after
  /// them with their flags, the builder's whirlpool program account first among them.
  @Test
  void increaseLiquidityV2MapsWithTheOraclePrefixBeforeTheNativeExtras() {
    final var vault = vault();
    final var source = increaseLiquidity(vault.vaultPublicKey(), USDC, WSOL).extraAccounts(EXTRAS);
    final var result = mapped(mapper(), source, vault, recorded());
    assertEquals("increase_liquidity_v2", result.source());
    assertEquals("increase_liquidity_v2", result.handler());
    final var instruction = result.instruction();
    assertEquals(STAGING.orcaIntegrationProgram(), instruction.programId().publicKey());
    assertEquals(GLOBAL_CONFIG_KEY, STAGING.globalConfigPDA().publicKey(), "the appended seat is the deployment's global configuration");

    final var expected = new ArrayList<>(liquiditySeats(vault, USDC, WSOL));
    expected.add(createRead(USDC_ORACLE));
    expected.add(createRead(WSOL_ORACLE));
    expected.add(createRead(WHIRLPOOL_PROGRAM));
    expected.addAll(EXTRAS);
    assertEquals(expected, instruction.accounts());
    assertArrayEquals(source.data(), instruction.data(), "the payload rides along unchanged");
  }

  /// increase_liquidity_by_token_amounts_v2 over a SOL-priced and a USD-priced mint: the wrapped SOL
  /// oracle follows the two, so the handler can convert.
  @Test
  void increaseLiquidityByTokenAmountsV2AddsTheSolOracleWhenTheDenominationsDiffer() {
    final var vault = vault();
    final var jito = key(1);
    final var pool = key(11);
    final var supplier = new GlamSuppliedAccounts(
        new OrcaOracleResolver(RegisteredOracles.of(new AssetMeta[]{
            meta(jito, pool, OracleSource.LstPoolState, 0),
            meta(USDC, USDC_ORACLE, OracleSource.PythLazerStableCoin, 0),
            meta(WSOL, WSOL_ORACLE, OracleSource.PythLazer, 0)
        }), WSOL),
        new LoopscaleStrategyMarketResolver(LOOPSCALE_PROGRAM, null)
    );
    final var keys = WhirlpoolProgram.increaseLiquidityV2Keys(
        SOLANA, WHIRLPOOL, SOLANA.tokenProgram(), SOLANA.tokenProgram(), vault.vaultPublicKey(), key(31), key(32), jito, USDC,
        key(33), key(34), key(35), key(36), key(37), key(38), WHIRLPOOL_PROGRAM
    );
    final var source = WhirlpoolProgram.increaseLiquidityByTokenAmountsV2(
        OrcaAccounts.MAIN_NET.invokedWhirlpoolProgram(), keys,
        new IncreaseLiquidityMethod.ByTokenAmounts(10L, 20L, BigInteger.ONE, BigInteger.TWO), null
    );
    final var result = mapped(mapper(), source, vault, supplier);
    assertEquals("increase_liquidity_by_token_amounts_v2", result.handler());
    final var expected = new ArrayList<>(liquiditySeats(vault, jito, USDC));
    expected.add(createRead(pool));
    expected.add(createRead(USDC_ORACLE));
    expected.add(createRead(WSOL_ORACLE));
    expected.add(createRead(WHIRLPOOL_PROGRAM));
    assertEquals(expected, result.instruction().accounts());
    assertEquals(List.of(), supplier.kaminoReserves());
  }

  /// decrease_liquidity_v2 reads the same prefix from the same positions.
  @Test
  void decreaseLiquidityV2MapsWithTheOraclePrefix() {
    final var vault = vault();
    final var keys = WhirlpoolProgram.increaseLiquidityV2Keys(
        SOLANA, WHIRLPOOL, SOLANA.tokenProgram(), SOLANA.tokenProgram(), vault.vaultPublicKey(), key(31), key(32), WSOL, MSOL,
        key(33), key(34), key(35), key(36), key(37), key(38), WHIRLPOOL_PROGRAM
    );
    final var source = WhirlpoolProgram.decreaseLiquidityV2(
        OrcaAccounts.MAIN_NET.invokedWhirlpoolProgram(), keys, BigInteger.valueOf(500L), 1L, 2L, null
    );
    final var result = mapped(mapper(), source, vault, recorded());
    assertEquals("decrease_liquidity_v2", result.handler());
    final var expected = new ArrayList<>(liquiditySeats(vault, WSOL, MSOL));
    expected.add(createRead(WSOL_ORACLE));
    expected.add(createRead(MSOL_ORACLE));
    expected.add(createRead(WHIRLPOOL_PROGRAM));
    assertEquals(expected, result.instruction().accounts());
  }

  /// reposition_liquidity_v2 seats the funder as the signer and reads the mints one position later; the
  /// prefix follows its twenty-three seats.
  @Test
  void repositionLiquidityV2MapsWithTheOraclePrefixFromItsMintPositions() {
    final var vault = vault();
    final var source = WhirlpoolProgram.repositionLiquidityV2(
        OrcaAccounts.MAIN_NET.invokedWhirlpoolProgram(), SOLANA,
        WHIRLPOOL, SOLANA.tokenProgram(), SOLANA.tokenProgram(), vault.vaultPublicKey(), FEE_PAYER, key(31), key(32), USDC, WSOL,
        key(33), key(34), key(35), key(36), key(37), key(38), key(39), key(40), WHIRLPOOL_PROGRAM,
        -100, 100, new RepositionLiquidityMethod.ByLiquidity(BigInteger.TEN, 1L, 2L, 3L, 4L), null
    ).extraAccounts(EXTRAS);
    final var result = mapped(mapper(), source, vault, recorded());
    assertEquals("reposition_liquidity_v2", result.handler());
    final var expected = new ArrayList<>(List.of(
        createWrite(STATE_KEY),
        createWrite(vault.vaultPublicKey()),
        createWritableSigner(FEE_PAYER),
        createRead(STAGING.readOrcaIntegrationAuthority().publicKey()),
        createRead(WHIRLPOOL_PROGRAM),
        createRead(STAGING.protocolProgram()),
        createRead(SOLANA.systemProgram()),
        createWrite(WHIRLPOOL),
        createRead(SOLANA.tokenProgram()),
        createRead(SOLANA.tokenProgram()),
        createRead(SOLANA.memoProgramV2()),
        createWrite(key(31)),
        createRead(key(32)),
        createRead(USDC),
        createRead(WSOL),
        createWrite(key(33)),
        createWrite(key(34)),
        createWrite(key(35)),
        createWrite(key(36)),
        createWrite(key(37)),
        createWrite(key(38)),
        createWrite(key(39)),
        createWrite(key(40)),
        createRead(GLOBAL_CONFIG_KEY),
        createRead(USDC_ORACLE),
        createRead(WSOL_ORACLE),
        createRead(WHIRLPOOL_PROGRAM)
    ));
    expected.addAll(EXTRAS);
    assertEquals(expected, result.instruction().accounts());
  }

  /// update_strategy takes the market as its first remaining account, from the update when it names one
  /// and from the strategy account otherwise; the native extras follow. The handler pays from the GLAM
  /// signer and lends from the vault, so the native update must seat them there.
  @Test
  void updateStrategyMapsWithTheMarketAsItsFirstRemainingAccount() {
    final var vault = vault();
    final var mapper = mapper();
    final var admin = key(41);
    final var solana = SOLANA;
    final var fromStrategy = mapped(mapper, updateStrategy(FEE_PAYER, vault.vaultPublicKey(), admin, STRATEGY_KEY, params(null)).extraAccounts(EXTRAS), vault, recorded());
    assertEquals("update_strategy", fromStrategy.handler());
    assertEquals(STAGING.loopscaleIntegrationProgram(), fromStrategy.instruction().programId().publicKey());
    final var expected = new ArrayList<>(List.of(
        createWrite(STATE_KEY),
        createWrite(vault.vaultPublicKey()),
        createWritableSigner(FEE_PAYER),
        createRead(STAGING.readLoopscaleIntegrationAuthority().publicKey()),
        createRead(LOOPSCALE_PROGRAM),
        createRead(STAGING.protocolProgram()),
        createRead(solana.systemProgram()),
        createReadOnlySigner(admin),
        createWrite(STRATEGY_KEY),
        createRead(STRATEGY_PRINCIPAL_MINT),
        createWrite(key(46)),
        createRead(solana.associatedTokenAccountProgram()),
        createRead(solana.tokenProgram()),
        createRead(key(49)),
        createRead(key(50)),
        createRead(STRATEGY_MARKET)
    ));
    expected.addAll(EXTRAS);
    assertEquals(expected, fromStrategy.instruction().accounts());

    final var named = key(8);
    final var fromParams = mapped(mapper, updateStrategy(FEE_PAYER, vault.vaultPublicKey(), admin, STRATEGY_KEY, params(named)), vault, GlamSuppliedAccounts.create(globalConfig(), strategy -> null));
    assertEquals(createRead(named), fromParams.instruction().accounts().get(15), "the update's market, without the strategy account");
    assertEquals(16, fromParams.instruction().accounts().size());

    // a change away from the stored market is refused when the strategy is known: the deployed program
    // would keep the stored market and the handler refuse the update after the CPI
    final var change = assertInstanceOf(MapResult.Unsupported.class,
        mapper.map(updateStrategy(FEE_PAYER, vault.vaultPublicKey(), admin, STRATEGY_KEY, params(named)), vault.mappingContext(recorded())));
    assertEquals(UnsupportedReason.CONTEXT, change.reason());
    assertTrue(change.message().startsWith("the context's supplied accounts failed for update_strategy: update_strategy names market " + named + ", but strategy " + STRATEGY_KEY + " stores " + STRATEGY_MARKET), change.message());

    final var vaultAsPayer = updateStrategy(vault.vaultPublicKey(), vault.vaultPublicKey(), admin, STRATEGY_KEY, params(null));
    final var refused = assertInstanceOf(MapResult.Unsupported.class, mapper.map(vaultAsPayer, vault.mappingContext(recorded())));
    assertEquals(UnsupportedReason.ACCOUNT_EXPECTATION, refused.reason());
    assertEquals("update_strategy account 1 (payer) must be glam_signer", refused.message());
  }

  /// Without a supplier, with a supplier that serves no such entry, and with one that refuses, the
  /// instruction is refused with reason `context`, the refusal's message carried; nothing is guessed.
  @Test
  void anEntryTheContextCannotSupplyIsRefused() {
    final var vault = vault();
    final var mapper = mapper();
    final var source = increaseLiquidity(vault.vaultPublicKey(), USDC, WSOL);

    var refused = assertInstanceOf(MapResult.Unsupported.class, mapper.map(source, vault.mappingContext()));
    assertEquals(UnsupportedReason.CONTEXT, refused.reason());
    assertEquals("the context supplies no accounts for increase_liquidity_v2", refused.message());

    final var unregistered = key(99);
    refused = assertInstanceOf(MapResult.Unsupported.class, mapper.map(increaseLiquidity(vault.vaultPublicKey(), USDC, unregistered), vault.mappingContext(recorded())));
    assertEquals(UnsupportedReason.CONTEXT, refused.reason());
    assertEquals("the context's supplied accounts failed for increase_liquidity_v2: The global configuration registers no oracle for mint " + unregistered + '.', refused.message());

    final var update = updateStrategy(FEE_PAYER, vault.vaultPublicKey(), key(41), STRATEGY_KEY, params(null));
    refused = assertInstanceOf(MapResult.Unsupported.class, mapper.map(update, vault.mappingContext(GlamSuppliedAccounts.create(globalConfig()))));
    assertEquals(UnsupportedReason.CONTEXT, refused.reason());
    assertEquals("the context supplies no accounts for update_strategy", refused.message());

    refused = assertInstanceOf(MapResult.Unsupported.class, mapper.map(update, vault.mappingContext(GlamSuppliedAccounts.create(globalConfig(), strategyMarkets(Map.of())))));
    assertEquals(UnsupportedReason.CONTEXT, refused.reason());
    assertTrue(refused.message().startsWith("the context's supplied accounts failed for update_strategy: The market of strategy " + STRATEGY_KEY), refused.message());
  }
}
