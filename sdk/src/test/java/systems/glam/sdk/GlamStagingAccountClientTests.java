package systems.glam.sdk;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.tx.Instruction;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.Context;
import systems.glam.sdk.idl.programs.glam.config.gen.GlamConfigPDAs;
import systems.glam.sdk.idl.programs.glam.staging.bridge.gen.ExtBridgePDAs;
import systems.glam.sdk.idl.programs.glam.staging.jupiter.gen.ExtJupiterPDAs;
import systems.glam.sdk.idl.programs.glam.staging.loopscale.gen.ExtLoopscalePDAs;
import systems.glam.sdk.idl.programs.glam.staging.marginfi.gen.ExtMarginfiPDAs;
import systems.glam.sdk.idl.programs.glam.staging.nt.gen.ExtNeutralPDAs;
import systems.glam.sdk.idl.programs.glam.staging.orca.gen.ExtOrcaPDAs;
import systems.glam.sdk.idl.programs.glam.staging.phoenix.gen.ExtPhoenixPDAs;
import systems.glam.sdk.idl.programs.glam.staging.registered_positions.gen.ExtRpiPDAs;

import java.math.BigInteger;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;
import static software.sava.core.accounts.meta.AccountMeta.createRead;
import static software.sava.core.accounts.meta.AccountMeta.createWrite;

final class GlamStagingAccountClientTests {

  private static final SolanaAccounts SOLANA_ACCOUNTS = SolanaAccounts.MAIN_NET;
  private static final PublicKey FEE_PAYER = fromBase58Encoded("F1oQY1jbdiJyxxeeuMBF2NsUckboyWo6TSXNqzJbrhxs");
  private static final PublicKey STATE_KEY = fromBase58Encoded("3H7XbyVaYusyzQCncfRSBx3zgvfmjGG7wrr3ARtXF1o7");
  private static final GlamAccounts STAGING = GlamAccounts.MAIN_NET_STAGING;

  private static PublicKey key(final int id) {
    final byte[] bytes = new byte[PublicKey.PUBLIC_KEY_LENGTH];
    bytes[0] = (byte) id;
    bytes[31] = 5;
    return PublicKey.createPubKey(bytes);
  }

  private static GlamAccountClient createClient() {
    return GlamAccountClient.createClient(SOLANA_ACCOUNTS, STAGING, FEE_PAYER, STATE_KEY);
  }

  /// Every staging pricing method routes through the staging mint program and
  /// swaps its event-authority slot on the cpiEmitEvents flag.
  private static void assertMintProgramPricing(final BiFunction<GlamAccountClient, Boolean, Instruction> price) {
    final var client = createClient();
    final var mintProgram = STAGING.mintProgram();
    final var eventAuthority = createRead(STAGING.mintEventAuthority());

    final var cpiIx = price.apply(client, true);
    assertEquals(mintProgram, cpiIx.programId().publicKey());
    assertTrue(cpiIx.accounts().contains(eventAuthority));

    final var noCpiIx = price.apply(client, false);
    assertEquals(mintProgram, noCpiIx.programId().publicKey());
    assertFalse(noCpiIx.accounts().contains(eventAuthority));
    assertTrue(noCpiIx.accounts().contains(createRead(mintProgram)));
  }

  @Test
  void stagingPricingMethodsAreImplemented() {
    final var solOracle = key(11);
    final var baseOracle = key(12);
    assertMintProgramPricing((c, cpi) -> c.priceSingleAssetVault(key(13), cpi));
    assertMintProgramPricing((c, cpi) -> c.priceStakeAccounts(solOracle, baseOracle, cpi));
    assertMintProgramPricing((c, cpi) -> c.priceVaultTokens(solOracle, baseOracle, new short[][]{{1, 2, 3, 4}}, cpi));
    assertMintProgramPricing((c, cpi) -> c.priceDriftUsers(solOracle, baseOracle, 2, cpi));
    assertMintProgramPricing((c, cpi) -> c.priceDriftVaultDepositors(solOracle, baseOracle, 1, 2, 3, cpi));
    assertMintProgramPricing((c, cpi) -> c.priceKaminoObligations(key(15), solOracle, baseOracle, cpi));
    assertMintProgramPricing((c, cpi) -> c.priceKaminoVaultShares(solOracle, baseOracle, 2, cpi));
    assertMintProgramPricing((c, cpi) -> c.validateAum(cpi));

    // the oracle keys must actually land in the instruction
    final var ix = createClient().priceStakeAccounts(solOracle, baseOracle, false);
    assertTrue(ix.accounts().contains(createRead(solOracle)));
    assertTrue(ix.accounts().contains(createRead(baseOracle)));
  }

  /// The integration pricers moved out of glam_mint (GLAM-1305) are built against the ext program
  /// that owns the positions. Each takes glam_mint's `PriceVaultCommon` list without its signer and
  /// its two event accounts, with the ext program's own integration authority:
  ///
  ///   [glam_state W, glam_vault, sol_usd_oracle, base_asset_oracle, integration_authority
  ///    (PDA "integration-authority" under the ext program), glam_config, glam_protocol_program]
  ///
  /// and keeps the glam_mint instruction's name, so its discriminator: sha256("global:<name>")[..8]
  /// of `price_loopscale_loans` is 6ab48ac15a03182a, and so on for each, followed by the u8
  /// argument where there is one.
  @Test
  void theIntegrationPricersRouteThroughTheirExtPrograms() {
    final var client = createClient();
    final var solOracle = key(31);
    final var baseOracle = key(32);
    final var vault = client.vaultAccounts().vaultPublicKey();
    final var globalConfig = GlamConfigPDAs.globalConfigPDA(STAGING.configProgram()).publicKey();

    record Case(String name, Instruction ix, PublicKey extProgram, PublicKey authority, byte[] mintData) {
    }
    final var loopscale = STAGING.loopscaleIntegrationProgram();
    final var loopscaleAuthority = ExtLoopscalePDAs.integrationAuthorityPDA(loopscale).publicKey();
    final var orca = STAGING.orcaIntegrationProgram();
    final var marginfi = STAGING.marginFiIntegrationProgram();
    final var phoenix = STAGING.phoenixIntegrationProgram();
    final var neutral = STAGING.neutralTradeIntegrationProgram();
    final var jupiter = STAGING.jupiterIntegrationProgram();
    final var jupiterAuthority = ExtJupiterPDAs.integrationAuthorityPDA(jupiter).publicKey();
    final var cases = List.of(
        new Case("price_loopscale_loans", client.priceLoopscaleLoans(solOracle, baseOracle),
            loopscale, loopscaleAuthority, hex("6ab48ac15a03182a")),
        new Case("price_loopscale_strategies", client.priceLoopscaleStrategies(solOracle, baseOracle),
            loopscale, loopscaleAuthority, hex("a934190b608a0aae")),
        new Case("price_loopscale_vault_positions", client.priceLoopscaleVaultPositions(solOracle, baseOracle, 3),
            loopscale, loopscaleAuthority, hex("62e5639a5e8b7cdc03")),
        new Case("price_orca_whirlpool_positions", client.priceOrcaWhirlpoolPositions(solOracle, baseOracle, 7),
            orca, ExtOrcaPDAs.integrationAuthorityPDA(orca).publicKey(),
            hex("0351752205ee9ee807")),
        new Case("price_marginfi_accounts", client.priceMarginfiAccounts(solOracle, baseOracle),
            marginfi, ExtMarginfiPDAs.integrationAuthorityPDA(marginfi).publicKey(),
            hex("92d7b4e7bfbc2aeb")),
        new Case("price_phoenix_traders", client.pricePhoenixTraders(solOracle, baseOracle),
            phoenix, ExtPhoenixPDAs.integrationAuthorityPDA(phoenix).publicKey(),
            hex("705ab12e91bfdbd5")),
        new Case("price_neutral_bundle_depositors", client.priceNeutralBundleDepositors(solOracle, baseOracle),
            neutral, ExtNeutralPDAs.integrationAuthorityPDA(neutral).publicKey(),
            hex("ca5dcd1d25b47f66")),
        new Case("price_jupiter_earn_positions", client.priceJupiterEarnPositions(solOracle, baseOracle),
            jupiter, jupiterAuthority, hex("780a0a89913ca410")),
        new Case("price_jupiter_borrow_positions", client.priceJupiterBorrowPositions(solOracle, baseOracle),
            jupiter, jupiterAuthority, hex("37fb21375011129a"))
    );
    for (final var priced : cases) {
      assertEquals(priced.extProgram, priced.ix.programId().publicKey(), priced.name);
      assertEquals(
          List.of(
              createWrite(STATE_KEY),
              createRead(vault),
              createRead(solOracle),
              createRead(baseOracle),
              createRead(priced.authority),
              createRead(globalConfig),
              createRead(STAGING.protocolProgram())
          ),
          priced.ix.accounts(),
          priced.name
      );
      assertArrayEquals(priced.mintData, priced.ix.copyData(), priced.name);
    }
  }

  /// ext_rpi's pricer takes the observation state in place of the vault, oracles and config.
  @Test
  void registeredPositionsPriceThroughExtRpiOverTheDerivedObservationState() {
    final var client = createClient();
    final var extRpi = STAGING.externalPositionProgram();
    final var observationState = ExtRpiPDAs.observationStatePDA(extRpi, STATE_KEY).publicKey();

    final var ix = client.priceRegisteredPositions();
    assertEquals(extRpi, ix.programId().publicKey());
    assertEquals(
        List.of(
            createWrite(STATE_KEY),
            createRead(observationState),
            createRead(ExtRpiPDAs.integrationAuthorityPDA(extRpi).publicKey()),
            createRead(STAGING.protocolProgram())
        ),
        ix.accounts()
    );
    assertArrayEquals(hex("5a9da232ec10bc03"), ix.copyData());

    final var explicit = key(33);
    assertEquals(createRead(explicit), client.priceRegisteredPositions(explicit).accounts().get(1));
  }

  /// ext_bridge's pricer is not glam_mint's renamed: `price_managed_transfers` over the vault's
  /// bridge registry, with protocol before config and only the base-asset oracle.
  @Test
  void managedTransfersPriceThroughExtBridgeOverTheVaultsRegistry() {
    final var client = createClient();
    final var extBridge = STAGING.bridgeIntegrationProgram();
    final var baseOracle = key(34);

    final var ix = client.priceBridgeManagedTransfers(baseOracle);
    assertEquals(extBridge, ix.programId().publicKey());
    assertEquals(
        List.of(
            createWrite(STATE_KEY),
            createRead(ExtBridgePDAs.bridgeRegistryPDA(extBridge, STATE_KEY).publicKey()),
            createRead(ExtBridgePDAs.integrationAuthorityPDA(extBridge).publicKey()),
            createRead(STAGING.protocolProgram()),
            createRead(GlamConfigPDAs.globalConfigPDA(STAGING.configProgram()).publicKey()),
            createRead(baseOracle)
        ),
        ix.accounts()
    );
    assertArrayEquals(hex("4d4c143029a8cd51"), ix.copyData());
  }

  /// Instruction data written out byte by byte. An Anchor discriminator is
  /// `sha256("global:<instruction name>")[..8]`, computed here independently of the generated
  /// constants (which glam_mint's copies of these pricers will lose, GLAM-1305 step 3); a u8
  /// argument follows as one byte.
  private static byte[] hex(final String hex) {
    return java.util.HexFormat.of().parseHex(hex);
  }

  @Test
  void stagingTokenInstructionsRouteThroughStagingPrograms() {
    final var client = createClient();
    final var from = key(21);
    final var to = key(22);
    final var mint = key(23);

    final var transferIx = client.transferTokenChecked(
        SOLANA_ACCOUNTS.invokedTokenProgram(), from, to, 55_000L, 6, mint
    );
    assertEquals(STAGING.splIntegrationProgram(), transferIx.programId().publicKey());
    final var transferData = systems.glam.sdk.idl.programs.glam.staging.spl.gen.ExtSplProgram
        .TokenTransferCheckedIxData.read(transferIx);
    assertEquals(55_000L, transferData.amount());
    assertEquals(6, transferData.decimals());

    final var closeIx = client.closeTokenAccount(SOLANA_ACCOUNTS.invokedTokenProgram(), key(24));
    assertEquals(STAGING.splIntegrationProgram(), closeIx.programId().publicKey());

    final var fulfillIx = client.fulfill(0, mint, SOLANA_ACCOUNTS.tokenProgram(), OptionalLong.of(9));
    assertEquals(STAGING.mintProgram(), fulfillIx.programId().publicKey());
    assertEquals(
        OptionalLong.of(9),
        systems.glam.sdk.idl.programs.glam.staging.mint.gen.GlamMintProgram.FulfillIxData.read(fulfillIx).limit()
    );
  }

  @Test
  void createStateAccountClientFromRealStagingAccount() {
    final var client = createClient();
    final byte[] stateData = Base64.getDecoder().decode(StateAccountFixture.BASE64.stripTrailing());
    final var accountInfo = new AccountInfo<>(
        STATE_KEY, new Context(123L, null), false, 0,
        STAGING.protocolProgram(), BigInteger.ZERO, 0, stateData
    );

    final var stateClient = client.createStateAccountClient(accountInfo);
    assertNotNull(stateClient);
    assertNull(client.createStateAccountClient(null));

    assertEquals("LST Yield", stateClient.name());
    assertEquals(fromBase58Encoded("FNL47CVnjoso6eZPkVi2RSAdCM49HgGbh3P4UrgY3DpR"), stateClient.mint());
    assertEquals(fromBase58Encoded("So11111111111111111111111111111111111111112"), stateClient.baseAssetMint());
    assertEquals(9, stateClient.baseAssetDecimals());
    assertEquals(SOLANA_ACCOUNTS.tokenProgram(), stateClient.baseAssetTokenProgram());
    assertEquals(5, stateClient.assets().length);
    assertEquals(2, stateClient.externalPositions().length);
    assertSame(client, stateClient.accountClient());

    // integration ACLs: kamino bitmask 3 grants lending and vaults
    assertTrue(stateClient.kaminoLendEnabled());
    assertTrue(stateClient.kaminoVaultsEnabled());

    // the fixture carries a legacy drift ACL on delegate HVDx…: it must be
    // skipped, while grants for supported programs on the SAME delegate hold
    final var delegate = fromBase58Encoded("HVDx4ijqYDMZF8dM4yFQrQG8cqwkC6LZZ4WgwYa3eLge");
    final var driftProgram = fromBase58Encoded("gstgdpMFXKobURsFtStdaMLRSuwdmDUsrndov7kyu9h");
    assertTrue(stateClient.delegateHasPermissions(
        delegate, Map.of(STAGING.splIntegrationProgram(), Protocol.TOKEN.permissions(1L))
    ));
    assertFalse(stateClient.delegateHasPermissions(
        delegate, Map.of(driftProgram, Protocol.TOKEN.permissions(1L))
    ));

    // the other delegate holds a single mint-program grant
    final var mintDelegate = fromBase58Encoded("ema1yjj7rwZ64kx6G4z4MVMi8ARqzQgKTN894QjzzsB");
    assertTrue(stateClient.delegateHasPermissions(
        mintDelegate, Map.of(STAGING.mintProgram(), Protocol.MINT.permissions(32L))
    ));
    assertFalse(stateClient.delegateHasPermissions(
        mintDelegate, Map.of(STAGING.mintProgram(), Protocol.MINT.permissions(64L))
    ));

    assertTrue(GlamAccountClient.isDelegated(
        systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateAccount.read(STATE_KEY, stateData),
        mintDelegate
    ));
  }

  @Test
  void wrapAndUnwrapUseInheritedProductionWiring() {
    final var client = createClient();
    final var ixs = client.wrapSOL(1_000L);
    assertEquals(2, ixs.size());
    // transfers route through the STAGING protocol program
    assertEquals(STAGING.protocolProgram(), ixs.getFirst().programId().publicKey());
    assertEquals(List.of(SOLANA_ACCOUNTS.readTokenProgram()), ixs.getFirst().accounts().subList(5, 6));
  }
}
