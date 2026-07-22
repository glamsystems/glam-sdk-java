package systems.glam.sdk;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.tx.Instruction;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.Context;

import java.math.BigInteger;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;
import static software.sava.core.accounts.meta.AccountMeta.createRead;

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
    assertMintProgramPricing((c, cpi) -> c.priceExternalPositions(solOracle, baseOracle, key(14), cpi));
    assertMintProgramPricing((c, cpi) -> c.priceLoopscaleLoans(solOracle, baseOracle, cpi));
    assertMintProgramPricing((c, cpi) -> c.priceLoopscaleStrategies(solOracle, baseOracle, cpi));
    assertMintProgramPricing((c, cpi) -> c.priceLoopscaleVaultPositions(solOracle, baseOracle, 3, cpi));
    assertMintProgramPricing((c, cpi) -> c.priceOrcaWhirlpoolPositions(solOracle, baseOracle, 3, cpi));
    assertMintProgramPricing((c, cpi) -> c.priceStakeAccounts(solOracle, baseOracle, cpi));
    assertMintProgramPricing((c, cpi) -> c.priceMarginfiAccounts(solOracle, baseOracle, cpi));
    assertMintProgramPricing((c, cpi) -> c.pricePhoenixTraders(solOracle, baseOracle, cpi));
    assertMintProgramPricing((c, cpi) -> c.priceBridgeManagedTransfers(solOracle, baseOracle, cpi));
    assertMintProgramPricing((c, cpi) -> c.priceVaultTokens(solOracle, baseOracle, new short[][]{{1, 2, 3, 4}}, cpi));
    assertMintProgramPricing((c, cpi) -> c.priceDriftUsers(solOracle, baseOracle, 2, cpi));
    assertMintProgramPricing((c, cpi) -> c.priceDriftVaultDepositors(solOracle, baseOracle, 1, 2, 3, cpi));
    assertMintProgramPricing((c, cpi) -> c.priceKaminoObligations(key(15), solOracle, baseOracle, cpi));
    assertMintProgramPricing((c, cpi) -> c.priceKaminoVaultShares(solOracle, baseOracle, 2, cpi));
    assertMintProgramPricing((c, cpi) -> c.validateAum(cpi));

    // the oracle keys must actually land in the instruction
    final var ix = createClient().priceLoopscaleLoans(solOracle, baseOracle, false);
    assertTrue(ix.accounts().contains(createRead(solOracle)));
    assertTrue(ix.accounts().contains(createRead(baseOracle)));
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
