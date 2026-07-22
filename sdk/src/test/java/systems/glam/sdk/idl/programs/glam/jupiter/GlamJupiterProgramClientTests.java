package systems.glam.sdk.idl.programs.glam.jupiter;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.jupiter.JupiterAccounts;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.sdk.GlamAccounts;
import systems.glam.sdk.idl.programs.glam.protocol.gen.GlamProtocolProgram;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;
import static software.sava.core.accounts.meta.AccountMeta.*;

final class GlamJupiterProgramClientTests {

  private static final SolanaAccounts SOLANA_ACCOUNTS = SolanaAccounts.MAIN_NET;
  private static final PublicKey FEE_PAYER = fromBase58Encoded("F1oQY1jbdiJyxxeeuMBF2NsUckboyWo6TSXNqzJbrhxs");
  private static final PublicKey STATE_KEY = fromBase58Encoded("9fkan2jCsS7Xq3fLqgxgZT5pDCbj2MhQ5MAoEKSHrcAT");
  private static final PublicKey VAULT_KEY = fromBase58Encoded("ApgsxNeZbi9P2pCAjzYR8VauqnWZpNkbN1iRWH1QsSwH");

  private static PublicKey key(final int id) {
    final byte[] bytes = new byte[PublicKey.PUBLIC_KEY_LENGTH];
    bytes[0] = (byte) id;
    bytes[31] = 3;
    return PublicKey.createPubKey(bytes);
  }

  private static GlamJupiterProgramClient createClient() {
    return GlamJupiterProgramClient.createClient(GlamAccountClient.createClient(FEE_PAYER, STATE_KEY));
  }

  private static Instruction routeIx() {
    // shaped like a jupiter route: some reads, the vault as a writable signer,
    // and a later signer that must keep its rights
    return Instruction.createInstruction(
        AccountMeta.createInvoked(JupiterAccounts.MAIN_NET.swapProgram()),
        List.of(
            createRead(key(11)),
            createWritableSigner(VAULT_KEY),
            createWrite(key(12)),
            createReadOnlySigner(key(13))
        ),
        new byte[]{9, 8, 7, 6}
    );
  }

  @Test
  void fixCPICallerRightsStripsOnlyTheFirstSigner() {
    final var fixed = GlamJupiterProgramClient.fixCPICallerRights(routeIx().accounts());
    assertEquals(createRead(key(11)), fixed.get(0));
    // the vault keeps write access but loses its signer requirement
    assertEquals(createWrite(VAULT_KEY), fixed.get(1));
    assertFalse(fixed.get(1).signer());
    assertEquals(createWrite(key(12)), fixed.get(2));
    // only the FIRST signer is stripped; later signers keep their rights
    assertEquals(createReadOnlySigner(key(13)), fixed.get(3));
    assertTrue(fixed.get(3).signer());

    final var readOnlySignerFirst = GlamJupiterProgramClient.fixCPICallerRights(
        List.of(createReadOnlySigner(key(14)), createWrite(key(15)))
    );
    assertEquals(createRead(key(14)), readOnlySignerFirst.getFirst());

    final var fixedIx = GlamJupiterProgramClient.fixCPICallerRights(routeIx());
    assertEquals(JupiterAccounts.MAIN_NET.swapProgram(), fixedIx.programId().publicKey());
    assertArrayEquals(new byte[]{9, 8, 7, 6}, fixedIx.data());
    assertEquals(fixed, fixedIx.accounts());

    // no signer at all: the scan must run off the end gracefully, not index
    // past it
    final var noSigners = List.of(createRead(key(16)), createWrite(key(17)));
    assertEquals(noSigners, GlamJupiterProgramClient.fixCPICallerRights(noSigners));
  }

  @Test
  void clientAccessors() {
    final var client = createClient();
    assertSame(SOLANA_ACCOUNTS, client.solanaAccounts());
    assertSame(JupiterAccounts.MAIN_NET, client.jupiterAccounts());
    assertEquals(STATE_KEY, client.glamVaultAccounts().glamStateKey());
  }

  private static JupiterSwapContext.Builder contextBuilder(final PublicKey inputMint) {
    return JupiterSwapContext.build()
        .inputMintKey(inputMint)
        .inputTokenProgram(SOLANA_ACCOUNTS.tokenProgram())
        .outputMintKey(key(22))
        .outputTokenProgram(SOLANA_ACCOUNTS.tokenProgram())
        .amount(1_000_000L)
        .swapInstruction(routeIx());
  }

  @Test
  void uncheckedSwapWrapsTheRouteInAGlamCpi() {
    final var client = createClient();
    final var context = contextBuilder(key(21))
        .skipQuotePriceCheck(true)
        .create();

    final var instructions = client.swap(context);
    assertEquals(1, instructions.size());
    final var swapIx = instructions.getFirst();
    final var protocolProgram = GlamAccounts.MAIN_NET.protocolProgram();
    assertEquals(protocolProgram, swapIx.programId().publicKey());

    final var accounts = swapIx.accounts();
    assertEquals(createWrite(STATE_KEY), accounts.get(0));
    assertEquals(createWrite(VAULT_KEY), accounts.get(1));
    assertEquals(createWritableSigner(FEE_PAYER), accounts.get(2));
    assertEquals(createRead(JupiterAccounts.MAIN_NET.swapProgram()), accounts.get(3));
    // no program state, config or oracles: every optional slot degrades to the
    // protocol program key
    for (int i = 4; i <= 9; ++i) {
      assertEquals(createRead(protocolProgram), accounts.get(i), "slot " + i);
    }
    // the route's accounts follow, with the vault's signer bit stripped
    final var fixed = GlamJupiterProgramClient.fixCPICallerRights(routeIx().accounts());
    assertEquals(fixed, accounts.subList(10, accounts.size()));

    final var ixData = GlamProtocolProgram.JupiterSwapV2IxData.read(swapIx);
    assertTrue(ixData.skipQuotePriceCheck());
    assertArrayEquals(new byte[]{9, 8, 7, 6}, ixData.data());
  }

  @Test
  void priceCheckedSwapCarriesConfigAndOracles() {
    final var client = createClient();
    final var solOracle = key(31);
    final var inputOracle = key(32);
    final var outputOracle = key(33);
    final var context = contextBuilder(key(21))
        .solUsdOracleKey(solOracle)
        .inputTokenOracleKey(inputOracle)
        .outputTokenOracleKey(outputOracle)
        .create();

    final var swapIx = client.swap(context).getFirst();
    final var accounts = swapIx.accounts();
    assertEquals(createRead(GlamAccounts.MAIN_NET.globalConfigPDA().publicKey()), accounts.get(6));
    assertEquals(createRead(solOracle), accounts.get(7));
    assertEquals(createRead(inputOracle), accounts.get(8));
    assertEquals(createRead(outputOracle), accounts.get(9));
    assertFalse(GlamProtocolProgram.JupiterSwapV2IxData.read(swapIx).skipQuotePriceCheck());
  }

  @Test
  void wrappingSwapPrependsTransferAndSync() {
    final var client = createClient();
    final var wSolMint = SOLANA_ACCOUNTS.wrappedSolTokenMint();
    final var context = contextBuilder(wSolMint)
        .skipQuotePriceCheck(true)
        .wrapSOL(true)
        .create();

    final var instructions = client.swap(context);
    assertEquals(3, instructions.size());

    final var wrappedSolPDA = GlamAccountClient.createClient(FEE_PAYER, STATE_KEY).wrappedSolPDA().publicKey();
    final var transferIx = instructions.getFirst();
    assertEquals(createWrite(wrappedSolPDA), transferIx.accounts().get(4));
    assertEquals(1_000_000L, GlamProtocolProgram.SystemTransferIxData.read(transferIx).lamports());
    final var syncIx = instructions.get(1);
    assertEquals(SOLANA_ACCOUNTS.tokenProgram(), syncIx.programId().publicKey());
    assertEquals(GlamAccounts.MAIN_NET.protocolProgram(), instructions.get(2).programId().publicKey());

    // wrapSOL without a wSOL input mint must not wrap
    final var nonSolContext = contextBuilder(key(21))
        .skipQuotePriceCheck(true)
        .wrapSOL(true)
        .create();
    assertEquals(1, client.swap(nonSolContext).size());

    // and a wSOL input without wrapSOL must not wrap either — both operands
    // of the wrap condition matter, in both the checked and unchecked paths
    final var noWrapContext = contextBuilder(wSolMint)
        .skipQuotePriceCheck(true)
        .create();
    assertEquals(1, client.swap(noWrapContext).size());
    final var noWrapChecked = contextBuilder(wSolMint)
        .skipQuotePriceCheck(true)
        .createATA(true)
        .create();
    assertEquals(2, client.swap(noWrapChecked).size());
  }

  @Test
  void checkedSwapCreatesTheOutputTokenAccount() {
    final var client = createClient();
    final var accountClient = GlamAccountClient.createClient(FEE_PAYER, STATE_KEY);
    final var outputATA = accountClient.findATA(SOLANA_ACCOUNTS.tokenProgram(), key(22)).publicKey();

    final var context = contextBuilder(key(21))
        .skipQuotePriceCheck(true)
        .createATA(true)
        .create();
    final var instructions = client.swap(context);
    assertEquals(2, instructions.size());
    final var createAtaIx = instructions.getFirst();
    assertEquals(SOLANA_ACCOUNTS.associatedTokenAccountProgram(), createAtaIx.programId().publicKey());
    assertTrue(createAtaIx.accounts().stream().anyMatch(meta -> meta.publicKey().equals(outputATA)));
    assertEquals(GlamAccounts.MAIN_NET.protocolProgram(), instructions.getLast().programId().publicKey());

    // a wSOL input adds the input ata creation, funding transfer and sync
    final var wrappingContext = contextBuilder(SOLANA_ACCOUNTS.wrappedSolTokenMint())
        .skipQuotePriceCheck(true)
        .createATA(true)
        .wrapSOL(true)
        .create();
    assertEquals(5, client.swap(wrappingContext).size());
  }

  @Test
  void createSwapTokenAccountsIdempotent() {
    final var client = createClient();
    final var accountClient = GlamAccountClient.createClient(FEE_PAYER, STATE_KEY);
    final var tokenProgram = SOLANA_ACCOUNTS.tokenProgram();

    final var context = contextBuilder(key(21)).create();
    final var byAta = client.createSwapTokenAccountsIdempotent(context);
    final var outputATA = accountClient.findATA(tokenProgram, key(22)).publicKey();
    assertEquals(1, byAta.size());
    assertNotNull(byAta.get(outputATA));

    // a wSOL input also needs its own vault ata
    final var wSolMint = SOLANA_ACCOUNTS.wrappedSolTokenMint();
    final var wSolContext = contextBuilder(wSolMint).create();
    final var bothAtas = client.createSwapTokenAccountsIdempotent(wSolContext);
    assertEquals(2, bothAtas.size());
    assertNotNull(bothAtas.get(accountClient.findATA(tokenProgram, wSolMint).publicKey()));
    assertNotNull(bothAtas.get(outputATA));

    // the explicit-key overload matches the context-driven one
    final var explicit = client.createSwapTokenAccountsIdempotent(
        tokenProgram, wSolMint, tokenProgram, key(22)
    );
    assertEquals(bothAtas.keySet(), explicit.keySet());
    // and for a non-wSOL input it must create only the output ata
    final var explicitSingle = client.createSwapTokenAccountsIdempotent(
        tokenProgram, key(21), tokenProgram, key(22)
    );
    assertEquals(1, explicitSingle.size());
    assertNotNull(explicitSingle.get(outputATA));
  }
}
