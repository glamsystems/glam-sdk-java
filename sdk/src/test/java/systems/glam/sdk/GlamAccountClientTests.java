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
