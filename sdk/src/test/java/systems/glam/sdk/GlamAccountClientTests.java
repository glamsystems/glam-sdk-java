package systems.glam.sdk;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import systems.glam.sdk.idl.programs.glam.mint.gen.GlamMintProgram;
import systems.glam.sdk.idl.programs.glam.protocol.gen.GlamProtocolProgram;
import systems.glam.sdk.idl.programs.glam.spl.gen.ExtSplProgram;

import java.util.List;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;
import static software.sava.core.accounts.meta.AccountMeta.*;

final class GlamAccountClientTests {

  private static final SolanaAccounts SOLANA_ACCOUNTS = SolanaAccounts.MAIN_NET;
  private static final PublicKey FEE_PAYER = fromBase58Encoded("F1oQY1jbdiJyxxeeuMBF2NsUckboyWo6TSXNqzJbrhxs");
  private static final PublicKey STATE_KEY = fromBase58Encoded("9fkan2jCsS7Xq3fLqgxgZT5pDCbj2MhQ5MAoEKSHrcAT");
  private static final PublicKey VAULT_KEY = fromBase58Encoded("ApgsxNeZbi9P2pCAjzYR8VauqnWZpNkbN1iRWH1QsSwH");

  // deterministic distinct keys, one byte set per role so no two collide
  private static PublicKey key(final int role) {
    final byte[] bytes = new byte[PublicKey.PUBLIC_KEY_LENGTH];
    bytes[0] = (byte) role;
    bytes[31] = (byte) role;
    return PublicKey.createPubKey(bytes);
  }

  private static GlamAccountClient createClient() {
    return GlamAccountClient.createClient(FEE_PAYER, STATE_KEY);
  }

  @Test
  void createClientRoutesByProtocolProgram() {
    final var production = createClient();
    assertSame(GlamEnv.PRODUCTION, production.glamEnv());
    assertSame(GlamAccounts.MAIN_NET, production.glamAccounts());

    final var staging = GlamAccountClient.createClient(
        SOLANA_ACCOUNTS, GlamAccounts.MAIN_NET_STAGING, FEE_PAYER, STATE_KEY
    );
    assertSame(GlamEnv.STAGING, staging.glamEnv());
    assertSame(GlamAccounts.MAIN_NET_STAGING, staging.glamAccounts());
    assertInstanceOf(GlamStagingAccountClient.class, staging);
  }

  @Test
  void clientAccountWiring() {
    final var client = createClient();
    assertSame(SOLANA_ACCOUNTS, client.solanaAccounts());
    // the vault, not the fee payer, owns token accounts
    assertEquals(VAULT_KEY, client.owner());
    assertEquals(AccountMeta.createFeePayer(FEE_PAYER), client.feePayer());
    assertEquals(FEE_PAYER, client.feePayerKey());
    assertEquals(STATE_KEY, client.vaultAccounts().glamStateKey());

    final var tokenProgram = SOLANA_ACCOUNTS.tokenProgram();
    final var mint = key(3);
    assertEquals(
        client.splClient().findATA(VAULT_KEY, tokenProgram, mint).publicKey(),
        client.vaultTokenAccount(tokenProgram, mint).publicKey()
    );
  }

  @Test
  void escrowMintTokenAccountAlwaysUsesToken2022() {
    final var client = createClient();
    final var mint = client.vaultAccounts().mintPDA().publicKey();
    final var escrow = GlamAccounts.MAIN_NET.escrowPDA(mint).publicKey();
    // known-good from GlamPDATests
    final var expected = fromBase58Encoded("8m1hHNSiatkEcggN4TxCkuejG7yioyCcSnJWtdwXq9BZ");
    assertEquals(expected, client.escrowMintTokenAccount(mint, escrow).publicKey());
    // the no-arg overload must derive the same mint and escrow, not others
    assertEquals(expected, client.escrowMintTokenAccount().publicKey());
    assertEquals(
        client.splClient().findATA(escrow, SOLANA_ACCOUNTS.token2022Program(), mint).publicKey(),
        client.escrowMintTokenAccount(mint, escrow).publicKey()
    );
  }

  @Test
  void transferSolLamports() {
    final var client = createClient();
    final var to = key(7);
    final var ix = client.transferSolLamports(to, 123_456_789L);

    assertEquals(GlamAccounts.MAIN_NET.protocolProgram(), ix.programId().publicKey());
    assertEquals(
        List.of(
            createRead(STATE_KEY),
            createWrite(VAULT_KEY),
            createWritableSigner(FEE_PAYER),
            createRead(SOLANA_ACCOUNTS.systemProgram()),
            createWrite(to)
        ),
        ix.accounts()
    );
    assertEquals(123_456_789L, GlamProtocolProgram.SystemTransferIxData.read(ix).lamports());
  }

  @Test
  void wrapSOL() {
    final var client = createClient();
    final var wrappedSolPDA = client.wrappedSolPDA().publicKey();
    final var ixs = client.wrapSOL(5_000_000L);
    assertEquals(2, ixs.size());

    final var transferIx = ixs.getFirst();
    assertEquals(GlamAccounts.MAIN_NET.protocolProgram(), transferIx.programId().publicKey());
    // system transfer to the wrapped SOL ata, with the token program appended
    // as the extra account the wSOL sync requires
    assertEquals(
        List.of(
            createRead(STATE_KEY),
            createWrite(VAULT_KEY),
            createWritableSigner(FEE_PAYER),
            createRead(SOLANA_ACCOUNTS.systemProgram()),
            createWrite(wrappedSolPDA),
            SOLANA_ACCOUNTS.readTokenProgram()
        ),
        transferIx.accounts()
    );
    assertEquals(5_000_000L, GlamProtocolProgram.SystemTransferIxData.read(transferIx).lamports());

    final var syncIx = ixs.getLast();
    assertEquals(SOLANA_ACCOUNTS.tokenProgram(), syncIx.programId().publicKey());
    assertEquals(List.of(createWrite(wrappedSolPDA)), syncIx.accounts());
  }

  private static void assertSplTokenAccounts(final Instruction ix,
                                             final PublicKey cpiTokenProgram,
                                             final List<AccountMeta> trailing) {
    assertEquals(GlamAccounts.MAIN_NET.splIntegrationProgram(), ix.programId().publicKey());
    final var expected = new java.util.ArrayList<>(List.of(
        createWrite(STATE_KEY),
        createWrite(VAULT_KEY),
        createWritableSigner(FEE_PAYER),
        createRead(GlamAccounts.MAIN_NET.readSplIntegrationAuthority().publicKey()),
        createRead(cpiTokenProgram),
        createRead(GlamAccounts.MAIN_NET.protocolProgram()),
        createRead(SOLANA_ACCOUNTS.systemProgram())
    ));
    expected.addAll(trailing);
    assertEquals(expected, ix.accounts());
  }

  @Test
  void transferTokenChecked() {
    final var client = createClient();
    final var from = key(11);
    final var to = key(12);
    final var mint = key(13);
    final var ix = client.transferTokenChecked(
        SOLANA_ACCOUNTS.invokedTokenProgram(), from, to, 42_000L, 6, mint
    );
    assertSplTokenAccounts(
        ix,
        SOLANA_ACCOUNTS.tokenProgram(),
        List.of(createWrite(from), createRead(mint), createWrite(to))
    );
    final var ixData = ExtSplProgram.TokenTransferCheckedIxData.read(ix);
    assertEquals(42_000L, ixData.amount());
    assertEquals(6, ixData.decimals());
  }

  @Test
  void closeTokenAccountAndUnwrapSOL() {
    final var client = createClient();
    final var tokenAccount = key(17);
    final var closeIx = client.closeTokenAccount(SOLANA_ACCOUNTS.invokedTokenProgram(), tokenAccount);
    assertSplTokenAccounts(
        closeIx, SOLANA_ACCOUNTS.tokenProgram(), List.of(createWrite(tokenAccount))
    );
    assertTrue(closeIx.beginsWith(ExtSplProgram.TOKEN_CLOSE_ACCOUNT_DISCRIMINATOR.data()));

    // unwrap is a close of the wrapped SOL ata
    final var unwrapIx = client.unwrapSOL();
    assertSplTokenAccounts(
        unwrapIx, SOLANA_ACCOUNTS.tokenProgram(), List.of(createWrite(client.wrappedSolPDA().publicKey()))
    );
    assertTrue(unwrapIx.beginsWith(ExtSplProgram.TOKEN_CLOSE_ACCOUNT_DISCRIMINATOR.data()));
  }

  @Test
  void validateAumEventAuthorityBranches() {
    final var client = createClient();
    final var mintProgram = GlamAccounts.MAIN_NET.mintProgram();

    final var cpiIx = client.validateAum(true);
    assertEquals(mintProgram, cpiIx.programId().publicKey());
    assertEquals(
        List.of(
            createRead(STATE_KEY),
            createWritableSigner(FEE_PAYER),
            createRead(GlamAccounts.MAIN_NET.mintEventAuthority()),
            createRead(mintProgram)
        ),
        cpiIx.accounts()
    );

    // without CPI events the event authority slot degrades to the program key
    final var noCpiIx = client.validateAum(false);
    assertEquals(
        List.of(
            createRead(STATE_KEY),
            createWritableSigner(FEE_PAYER),
            createRead(mintProgram),
            createRead(mintProgram)
        ),
        noCpiIx.accounts()
    );
  }

  @Test
  void priceVaultTokensEventAuthorityBranches() {
    final var client = createClient();
    final var solOracle = key(21);
    final var baseOracle = key(22);
    // each agg-index entry is a fixed-size short[4] in the IDL
    final short[][] aggIndexes = {{1, 2, 3, 4}, {5, 6, 7, 8}};
    final var mintProgram = GlamAccounts.MAIN_NET.mintProgram();

    final var ix = client.priceVaultTokens(solOracle, baseOracle, aggIndexes, true);
    assertEquals(mintProgram, ix.programId().publicKey());
    assertEquals(
        List.of(
            createWrite(STATE_KEY),
            createRead(VAULT_KEY),
            createWritableSigner(FEE_PAYER),
            createRead(solOracle),
            createRead(baseOracle),
            createRead(GlamAccounts.MAIN_NET.readMintIntegrationAuthority().publicKey()),
            createRead(GlamAccounts.MAIN_NET.globalConfigPDA().publicKey()),
            createRead(GlamAccounts.MAIN_NET.protocolProgram()),
            createRead(GlamAccounts.MAIN_NET.mintEventAuthority()),
            createRead(mintProgram)
        ),
        ix.accounts()
    );
    final var ixData = GlamMintProgram.PriceVaultTokensIxData.read(ix);
    assertArrayEquals(aggIndexes, ixData.aggIndexes());

    // no-CPI branch and the default overload both use the program key
    final var noCpiIx = client.priceVaultTokens(solOracle, baseOracle, aggIndexes, false);
    assertEquals(createRead(mintProgram), noCpiIx.accounts().get(8));
    assertEquals(noCpiIx.accounts(), client.priceVaultTokens(solOracle, baseOracle, aggIndexes).accounts());
  }

  /// Every remaining pricing method shares one contract: the convenience
  /// overload is exactly the no-CPI instruction (same accounts — this family
  /// already produced a real dropped-oracle-keys bug), and the CPI branch
  /// swaps the mint program slot for the mint event authority.
  @Test
  void pricingOverloadsMatchTheirNoCpiBranch() {
    final var client = createClient();
    final var sol = key(21);
    final var base = key(22);
    final var eventAuthority = createRead(GlamAccounts.MAIN_NET.mintEventAuthority());

    record Case(String name, Instruction cpi, Instruction noCpi, Instruction byDefault) {
    }
    final var kLendProgram = key(23);
    final var productionCases = List.of(
        new Case("priceDriftUsers",
            client.priceDriftUsers(sol, base, 3, true),
            client.priceDriftUsers(sol, base, 3, false),
            client.priceDriftUsers(sol, base, 3)),
        new Case("priceDriftVaultDepositors",
            client.priceDriftVaultDepositors(sol, base, 3, 2, 1, true),
            client.priceDriftVaultDepositors(sol, base, 3, 2, 1, false),
            client.priceDriftVaultDepositors(sol, base, 3, 2, 1)),
        new Case("priceKaminoObligations",
            client.priceKaminoObligations(kLendProgram, sol, base, true),
            client.priceKaminoObligations(kLendProgram, sol, base, false),
            client.priceKaminoObligations(kLendProgram, sol, base)),
        new Case("priceKaminoVaultShares",
            client.priceKaminoVaultShares(sol, base, 4, true),
            client.priceKaminoVaultShares(sol, base, 4, false),
            client.priceKaminoVaultShares(sol, base, 4))
    );
    // the rest of the pricing surface is staging-only; the interface defaults
    // route identically, just through the staging implementation
    final var staging = GlamAccountClient.createClient(
        SOLANA_ACCOUNTS, GlamAccounts.MAIN_NET_STAGING, FEE_PAYER, STATE_KEY);
    final var stagingCases = List.of(
        new Case("priceExternalPositions",
            staging.priceExternalPositions(sol, base, true),
            staging.priceExternalPositions(sol, base, false),
            staging.priceExternalPositions(sol, base)),
        new Case("priceLoopscaleLoans",
            staging.priceLoopscaleLoans(sol, base, true),
            staging.priceLoopscaleLoans(sol, base, false),
            staging.priceLoopscaleLoans(sol, base)),
        new Case("priceLoopscaleStrategies",
            staging.priceLoopscaleStrategies(sol, base, true),
            staging.priceLoopscaleStrategies(sol, base, false),
            staging.priceLoopscaleStrategies(sol, base)),
        new Case("priceLoopscaleVaultPositions",
            staging.priceLoopscaleVaultPositions(sol, base, 5, true),
            staging.priceLoopscaleVaultPositions(sol, base, 5, false),
            staging.priceLoopscaleVaultPositions(sol, base, 5)),
        new Case("priceOrcaWhirlpoolPositions",
            staging.priceOrcaWhirlpoolPositions(sol, base, 6, true),
            staging.priceOrcaWhirlpoolPositions(sol, base, 6, false),
            staging.priceOrcaWhirlpoolPositions(sol, base, 6)),
        new Case("priceStakeAccounts",
            staging.priceStakeAccounts(sol, base, true),
            staging.priceStakeAccounts(sol, base, false),
            staging.priceStakeAccounts(sol, base)),
        new Case("priceMarginfiAccounts",
            staging.priceMarginfiAccounts(sol, base, true),
            staging.priceMarginfiAccounts(sol, base, false),
            staging.priceMarginfiAccounts(sol, base)),
        new Case("pricePhoenixTraders",
            staging.pricePhoenixTraders(sol, base, true),
            staging.pricePhoenixTraders(sol, base, false),
            staging.pricePhoenixTraders(sol, base)),
        new Case("priceBridgeManagedTransfers",
            staging.priceBridgeManagedTransfers(sol, base, true),
            staging.priceBridgeManagedTransfers(sol, base, false),
            staging.priceBridgeManagedTransfers(sol, base))
    );
    for (final var priced : productionCases) {
      assertTrue(priced.cpi.accounts().contains(eventAuthority),
          () -> priced.name + ": the CPI branch does not carry the mint event authority");
      assertFalse(priced.noCpi.accounts().contains(eventAuthority),
          () -> priced.name + ": the no-CPI branch must not carry the event authority");
    }
    final var allCases = new java.util.ArrayList<>(productionCases);
    allCases.addAll(stagingCases);
    for (final var priced : allCases) {
      assertEquals(priced.noCpi.accounts(), priced.byDefault.accounts(),
          () -> priced.name + ": the convenience overload drifted from the no-CPI instruction");
      assertNotEquals(priced.noCpi.accounts(), priced.cpi.accounts(),
          () -> priced.name + ": the CPI branch changed nothing");
      // the oracle keys ride on every variant — the historical bug dropped them
      assertTrue(priced.byDefault.accounts().contains(createRead(sol)), priced.name);
      assertTrue(priced.byDefault.accounts().contains(createRead(base)), priced.name);
    }
  }

  @Test
  void accountCreationAndStateUpdateWiring() {
    final var client = createClient();
    final var systemProgram = SOLANA_ACCOUNTS.systemProgram();

    final var created = client.createAccount(key(41), 1_000_000L, 165L, key(42));
    assertEquals(systemProgram, created.programId().publicKey());

    final var withSeed = software.sava.core.accounts.AccountWithSeed.createAccount(
        FEE_PAYER, key(43), "glam-test".getBytes(java.nio.charset.StandardCharsets.US_ASCII), key(44));
    final var createdWithSeed = client.createAccountWithSeed(withSeed, 1_000_000L, 165L, key(44));
    assertEquals(systemProgram, createdWithSeed.programId().publicKey());

    final var escrowAta = client.createEscrowAssociatedTokenIdempotent(
        key(45), key(46), key(47), SOLANA_ACCOUNTS.tokenProgram());
    assertEquals(SOLANA_ACCOUNTS.associatedTokenAccountProgram(), escrowAta.programId().publicKey());
    // funded by the fee payer, created for the escrow
    assertEquals(createWritableSigner(FEE_PAYER), escrowAta.accounts().getFirst());

    final var updated = client.updateState(
        systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateModel.createRecord(
            null, null, null, Boolean.TRUE, null, null, null, null, null, OptionalLong.empty(), null, null));
    assertEquals(GlamAccounts.MAIN_NET.protocolProgram(), updated.programId().publicKey());
    assertEquals(
        List.of(createWrite(STATE_KEY), createWritableSigner(FEE_PAYER)),
        updated.accounts()
    );
  }

  @Test
  void fulfill() {
    final var client = createClient();
    final var baseAssetMint = key(31);
    final var tokenProgram = SOLANA_ACCOUNTS.tokenProgram();

    final var mint = client.vaultAccounts().mintPDA().publicKey();
    final var escrow = GlamAccounts.MAIN_NET.escrowPDA(mint).publicKey();
    final var requestQueue = GlamAccounts.MAIN_NET.requestQueuePDA(mint).publicKey();
    final var escrowMintAta = client.escrowMintTokenAccount(mint, escrow).publicKey();
    final var vaultAssetAta = client.splClient().findATA(VAULT_KEY, tokenProgram, baseAssetMint).publicKey();
    final var escrowAssetAta = client.splClient().findATA(escrow, tokenProgram, baseAssetMint).publicKey();

    final var ix = client.fulfill(0, baseAssetMint, tokenProgram, OptionalLong.of(77));
    assertEquals(GlamAccounts.MAIN_NET.mintProgram(), ix.programId().publicKey());
    assertEquals(
        List.of(
            createWrite(STATE_KEY),
            createWrite(VAULT_KEY),
            createWrite(mint),
            createRead(escrow),
            createWrite(requestQueue),
            createWritableSigner(FEE_PAYER),
            createWrite(escrowMintAta),
            createRead(baseAssetMint),
            createWrite(vaultAssetAta),
            createWrite(escrowAssetAta),
            createRead(SOLANA_ACCOUNTS.systemProgram()),
            createRead(tokenProgram),
            createRead(SOLANA_ACCOUNTS.token2022Program()),
            createRead(SOLANA_ACCOUNTS.associatedTokenAccountProgram()),
            createRead(GlamAccounts.MAIN_NET.protocolProgram())
        ),
        ix.accounts()
    );
    assertEquals(OptionalLong.of(77), GlamMintProgram.FulfillIxData.read(ix).limit());

    // default overload: share class 0, no limit
    final var defaultIx = client.fulfill(baseAssetMint, tokenProgram);
    assertEquals(ix.accounts(), defaultIx.accounts());
    assertEquals(OptionalLong.empty(), GlamMintProgram.FulfillIxData.read(defaultIx).limit());
  }

  @Test
  void priceExternalPositionsDefaultKeepsOracleKeys() {
    final var staging = GlamAccountClient.createClient(
        SOLANA_ACCOUNTS, GlamAccounts.MAIN_NET_STAGING, FEE_PAYER, STATE_KEY
    );
    final var solOracle = key(51);
    final var baseOracle = key(52);
    final var ix = staging.priceExternalPositions(solOracle, baseOracle, false);

    final var accounts = ix.accounts();
    // the overload derives the observation PDA but must not drop the oracles
    assertEquals(createRead(solOracle), accounts.get(3));
    assertEquals(createRead(baseOracle), accounts.get(4));
    final var observationPDA = systems.glam.sdk.idl.programs.glam.staging.registered_positions.gen.ExtRpiPDAs
        .observationStatePDA(
            GlamAccounts.MAIN_NET_STAGING.externalPositionProgram(),
            STATE_KEY
        );
    assertEquals(createRead(observationPDA.publicKey()), accounts.getLast());
    // without CPI events the event authority slot is the mint program itself
    assertEquals(createRead(GlamAccounts.MAIN_NET_STAGING.mintProgram()), accounts.get(8));

    final var cpiAccounts = staging.priceExternalPositions(solOracle, baseOracle, true).accounts();
    assertEquals(createRead(GlamAccounts.MAIN_NET_STAGING.mintEventAuthority()), cpiAccounts.get(8));
  }

  @Test
  void stagingOnlyMethodsThrowOnProduction() {
    final var client = createClient();
    final var a = key(41);
    final var b = key(42);
    assertThrows(IllegalStateException.class, () -> client.priceSingleAssetVault(a, false));
    assertThrows(IllegalStateException.class, () -> client.priceExternalPositions(a, b, b, false));
    assertThrows(IllegalStateException.class, () -> client.priceLoopscaleLoans(a, b, false));
    assertThrows(IllegalStateException.class, () -> client.priceLoopscaleStrategies(a, b, false));
    assertThrows(IllegalStateException.class, () -> client.priceLoopscaleVaultPositions(a, b, 2, false));
    assertThrows(IllegalStateException.class, () -> client.priceOrcaWhirlpoolPositions(a, b, 2, false));
    assertThrows(IllegalStateException.class, () -> client.priceStakeAccounts(a, b, false));
    assertThrows(IllegalStateException.class, () -> client.priceMarginfiAccounts(a, b, false));
    assertThrows(IllegalStateException.class, () -> client.pricePhoenixTraders(a, b, false));
    assertThrows(IllegalStateException.class, () -> client.priceBridgeManagedTransfers(a, b, false));
  }
}
