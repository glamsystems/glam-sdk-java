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

  private static void assertSameInstruction(final String name, final Instruction expected, final Instruction actual) {
    assertEquals(expected.programId().publicKey(), actual.programId().publicKey(), name);
    assertEquals(expected.accounts(), actual.accounts(), name);
    assertArrayEquals(expected.data(), actual.data(), name);
  }

  private static void assertSameInstructions(final String name,
                                             final java.util.List<Instruction> expected,
                                             final java.util.List<Instruction> actual) {
    assertEquals(expected.size(), actual.size(), name);
    for (int i = 0; i < expected.size(); ++i) {
      assertSameInstruction(name + "[" + i + "]", expected.get(i), actual.get(i));
    }
  }

  /// Every convenience overload must produce exactly its fully-explicit
  /// form — this delegation family already produced a real dropped-argument
  /// bug (priceExternalPositions), so each hop is pinned.
  @Test
  void convenienceOverloadsMatchTheirExplicitForms() {
    final var client = createClient();
    final var tokenProgram = SOLANA_ACCOUNTS.tokenProgram();
    final var in = key(21);
    final var out = key(22);
    final long amount = 1_234L;
    final var route = routeIx();
    final var inState = key(23);
    final var outState = key(24);

    final var checked = client.swapWithProgramStateChecked(
        null, in, tokenProgram, null, out, tokenProgram, amount, route, false);
    final var checkedWrap = client.swapWithProgramStateChecked(
        null, in, tokenProgram, null, out, tokenProgram, amount, route, true);
    final var unchecked = client.swapWithProgramStateUnchecked(
        null, in, tokenProgram, null, out, tokenProgram, amount, route, false);
    final var uncheckedWrap = client.swapWithProgramStateUnchecked(
        null, in, tokenProgram, null, out, tokenProgram, amount, route, true);
    final var noWrap = client.swapWithProgramStateUncheckedAndNoWrap(
        null, in, tokenProgram, null, out, tokenProgram, route);

    assertSameInstructions("swapChecked/7",
        client.swapChecked(in, tokenProgram, out, tokenProgram, amount, route, false), checked);
    assertSameInstructions("swapChecked/5",
        client.swapChecked(in, out, amount, route, false), checked);
    assertSameInstructions("swapChecked/4 defaults to wrap",
        client.swapChecked(in, out, amount, route), checkedWrap);
    assertSameInstruction("swapUncheckedAndNoWrap/5",
        client.swapUncheckedAndNoWrap(in, tokenProgram, out, tokenProgram, route), noWrap);
    assertSameInstruction("swapUncheckedAndNoWrap/3",
        client.swapUncheckedAndNoWrap(in, out, route), noWrap);
    assertSameInstructions("swapUnchecked/7",
        client.swapUnchecked(in, tokenProgram, out, tokenProgram, amount, route, false), unchecked);
    assertSameInstructions("swapUnchecked/5",
        client.swapUnchecked(in, out, amount, route, false), unchecked);
    assertSameInstructions("swapUnchecked/4 defaults to wrap",
        client.swapUnchecked(in, out, amount, route), uncheckedWrap);

    // the program-state keys must survive the token-program-defaulting hops
    final var stateChecked = client.swapWithProgramStateChecked(
        inState, in, tokenProgram, outState, out, tokenProgram, amount, route, false);
    assertSameInstructions("swapWithProgramStateChecked/7",
        client.swapWithProgramStateChecked(inState, in, outState, out, amount, route, false), stateChecked);
    assertSameInstructions("swapWithProgramStateChecked/6 defaults to wrap",
        client.swapWithProgramStateChecked(inState, in, outState, out, amount, route),
        client.swapWithProgramStateChecked(inState, in, tokenProgram, outState, out, tokenProgram, amount, route, true));
    final var stateUnchecked = client.swapWithProgramStateUnchecked(
        inState, in, tokenProgram, outState, out, tokenProgram, amount, route, false);
    assertSameInstructions("swapWithProgramStateUnchecked/7",
        client.swapWithProgramStateUnchecked(inState, in, outState, out, amount, route, false), stateUnchecked);
    assertSameInstructions("swapWithProgramStateUnchecked/6 defaults to wrap",
        client.swapWithProgramStateUnchecked(inState, in, outState, out, amount, route),
        client.swapWithProgramStateUnchecked(inState, in, tokenProgram, outState, out, tokenProgram, amount, route, true));
    assertSameInstruction("swapWithProgramStateUncheckedAndNoWrap/5",
        client.swapWithProgramStateUncheckedAndNoWrap(inState, in, outState, out, route),
        client.swapWithProgramStateUncheckedAndNoWrap(inState, in, tokenProgram, outState, out, tokenProgram, route));

    // and they actually land in the CPI: with vs without must differ
    assertNotEquals(checked.getLast().accounts(), stateChecked.getLast().accounts(),
        "the program-state keys never reached the checked swap CPI");
    assertNotEquals(unchecked.getLast().accounts(), stateUnchecked.getLast().accounts(),
        "the program-state keys never reached the unchecked swap CPI");

    // the route's accounts ride the CPI as extra accounts — Instruction is
    // immutable, so a dropped extraAccounts() result loses the whole route
    // (the wrapSOL variant of this was a real bug)
    assertTrue(noWrap.accounts().contains(createRead(key(11))),
        "the route accounts never reached the swap CPI");

    // a non-wSOL input must not trigger the wrap prelude even when allowed
    assertEquals(2, checkedWrap.size(), "wrap prelude added for a non-wSOL input");
    assertEquals(1, uncheckedWrap.size(), "wrap prelude added for a non-wSOL input");
  }

  /// The program-state swap variants share the wrap gate: a wSOL input plus
  /// wrapSOL prepends the fund-and-sync prelude; either alone must not.
  @Test
  void programStateSwapsWrapOnlyWrappedSolInputs() {
    final var client = createClient();
    final var tokenProgram = SOLANA_ACCOUNTS.tokenProgram();
    final var wSol = SOLANA_ACCOUNTS.wrappedSolTokenMint();
    final var out = key(22);
    final long amount = 1_234L;
    final var route = routeIx();
    final var inState = key(23);
    final var outState = key(24);

    final var checkedWrapped = client.swapWithProgramStateChecked(
        inState, wSol, tokenProgram, outState, out, tokenProgram, amount, route, true);
    // create input ATA, fund it, sync, create output ATA, swap
    assertEquals(5, checkedWrapped.size());
    final var checkedUnwrapped = client.swapWithProgramStateChecked(
        inState, wSol, tokenProgram, outState, out, tokenProgram, amount, route, false);
    assertEquals(2, checkedUnwrapped.size(), "wrapSOL=false must skip the prelude");

    final var uncheckedWrapped = client.swapWithProgramStateUnchecked(
        inState, wSol, tokenProgram, outState, out, tokenProgram, amount, route, true);
    // fund the wSOL PDA, sync, swap
    assertEquals(3, uncheckedWrapped.size());
    assertEquals(1, client.swapWithProgramStateUnchecked(
            inState, wSol, tokenProgram, outState, out, tokenProgram, amount, route, false).size(),
        "wrapSOL=false must skip the prelude");

    // the prelude funds the vault's wSOL account with exactly the swap amount
    final var transfer = checkedWrapped.get(1);
    assertEquals(GlamAccounts.MAIN_NET.protocolProgram(), transfer.programId().publicKey());
  }

  @Test
  void contextBuilderProgramStateSettersReturnTheBuilder() {
    final var builder = contextBuilder(key(21));
    assertSame(builder, builder.inputProgramStateKey(key(23)));
    assertSame(builder, builder.outputProgramStateKey(key(24)));
  }
}
