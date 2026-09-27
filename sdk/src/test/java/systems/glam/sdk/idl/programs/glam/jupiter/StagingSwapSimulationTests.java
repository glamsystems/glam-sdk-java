package systems.glam.sdk.idl.programs.glam.jupiter;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.jupiter.swap.rest.response.JupiterSwapInstructions;
import software.sava.idl.clients.kamino.lend.gen.types.Reserve;
import systems.comodal.jsoniter.JsonIterator;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.sdk.GlamAccounts;
import systems.glam.sdk.idl.programs.glam.config.gen.types.GlobalConfig;
import systems.glam.sdk.idl.programs.glam.config.gen.types.OracleSource;
import systems.glam.sdk.idl.programs.glam.staging.protocol.gen.GlamProtocolProgram;
import systems.glam.sdk.idl.programs.glam.staging.protocol.gen.types.StateAccount;
import systems.glam.sdk.mapping.RegisteredOracles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;
import static systems.glam.sdk.tests.MappingFixtures.*;

/// A swap the deployed staging program accepted in a mainnet simulation: a wrapped-SOL input, and
/// input and output roles priced by Kamino reserves. See the fixture's README for the run. The
/// accepted instruction list is the oracle; the recorded inputs must rebuild it.
final class StagingSwapSimulationTests {

  private static final String RUN = "jupiter-swap/staging/451027533/";

  private static final PublicKey STATE = fromBase58Encoded("HuRA6CuTcLaWB9adGJ9aLG67sC4fqp4SMXYvDWmEhd83");
  private static final PublicKey VAULT = fromBase58Encoded("HeL5m28kD42iRcmxFWCdYqkMbAzFRWyukidwNNZGBEt");
  private static final PublicKey SIGNER = fromBase58Encoded("gLJHKPrZLGBiBZ33hFgZh6YnsEhTVxuRT17UCqNp6ff");
  private static final PublicKey WSOL_RESERVE = fromBase58Encoded("d4A2prbA2whesmvHaL88BH6Ewn5N4bTSU2Ze8P6Bc4Q");
  private static final PublicKey USDC_RESERVE = fromBase58Encoded("D6q6wuQSrifJKZYpR1M8R4YawnLDtDsMmWM1NbBmgJ59");
  private static final long AMOUNT = 50_000_000L;

  private static RegisteredOracles recordedOracles() {
    return RegisteredOracles.of(GlobalConfig.readChecked(
        GLOBAL_CONFIG_KEY, readGzip(RUN + GLOBAL_CONFIG_KEY + ".dat.gz")
    ));
  }

  private static KaminoReserveRefresh recordedRefresh(final PublicKey reserveKey) {
    return KaminoReserveRefresh.of(Reserve.read(reserveKey, readGzip(RUN + reserveKey + ".dat.gz")));
  }

  /// What the harness passed to `swap`: the oracles as registered, the reserves as read, and the
  /// route Jupiter returned for the vault.
  private static List<Instruction> rebuild() {
    final var solana = SolanaAccounts.MAIN_NET;
    final var accountClient = GlamAccountClient.createClient(solana, GlamAccounts.MAIN_NET_STAGING, SIGNER, STATE);
    assertEquals(VAULT, accountClient.vaultAccounts().vaultPublicKey());

    final var oracles = recordedOracles();
    final var inputOracle = oracles.forMint(WSOL).oracle();
    final var route = JupiterSwapInstructions.parseInstructions(
        JsonIterator.parse(read(RUN + "swap-instructions.json"))
    ).swapInstruction();
    final var context = JupiterSwapContext.build()
        .inputMintKey(WSOL).inputTokenProgram(solana.tokenProgram()).inputTokenOracleKey(inputOracle)
        .outputMintKey(USDC).outputTokenProgram(solana.tokenProgram()).outputTokenOracleKey(oracles.forMint(USDC).oracle())
        .solUsdOracleKey(inputOracle)
        .skipQuotePriceCheck(false)
        .amount(AMOUNT)
        .swapInstruction(route)
        .wrapSOL(true).createATA(true)
        .kaminoReserves(List.of(recordedRefresh(WSOL_RESERVE), recordedRefresh(USDC_RESERVE)))
        .create();
    return GlamJupiterProgramClient.createClient(accountClient).swap(context);
  }

  @Test
  void theRecordedInputsRebuildTheAcceptedInstructions() {
    final var accepted = JupiterSwapInstructions.parseInstructionsList(
        JsonIterator.parse(read(RUN + "accepted-instructions.json"))
    );
    final var rebuilt = rebuild();

    assertEquals(accepted.size(), rebuilt.size());
    for (int i = 0; i < accepted.size(); ++i) {
      final var expected = accepted.get(i);
      final var actual = rebuilt.get(i);
      assertEquals(expected.programId().publicKey(), actual.programId().publicKey(), "program " + i);
      assertEquals(expected.accounts(), actual.accounts(), "accounts " + i);
      assertArrayEquals(expected.copyData(), actual.copyData(), "data " + i);
    }
  }

  /// wSOL and USDC each hold a PythPull and a KaminoReserve registration at the same priority, and
  /// the selection rule prefers the reserve: that is what made this a reserve-priced swap.
  @Test
  void theRecordedRegistrationsPriceBothSidesByKaminoReserves() {
    final var oracles = recordedOracles();
    final var input = oracles.forMint(WSOL);
    final var output = oracles.forMint(USDC);

    assertEquals(OracleSource.KaminoReserve, input.oracleSource());
    assertEquals(WSOL_RESERVE, input.oracle());
    assertEquals(OracleSource.KaminoReserve, output.oracleSource());
    assertEquals(USDC_RESERVE, output.oracle());
  }

  /// A simulation without signature checks cannot catch a wrong signer, so the list's signers are
  /// pinned here: the vault's owner, and never the vault. The recorded state names that owner.
  @Test
  void onlyTheOwnerSignsAndTheVaultNeverDoes() {
    final var state = StateAccount.read(STATE, readGzip(RUN + STATE + ".dat.gz"));
    assertEquals(SIGNER, state.owner());
    assertEquals(VAULT, state.vault());

    boolean vaultSeen = false;
    for (final var ix : rebuild()) {
      for (final var meta : ix.accounts()) {
        if (meta.signer()) {
          assertEquals(SIGNER, meta.publicKey(), () -> "signer in " + ix.programId().publicKey());
        }
        vaultSeen |= meta.publicKey().equals(VAULT);
      }
    }
    assertTrue(vaultSeen);
  }

  /// The swap leads with the reserve refresh, wraps through the staging program, and asks for the
  /// quote price check: with the skip flag an owner swap would never reach the oracles.
  @Test
  void theSwapRefreshesFirstWrapsAndChecksTheQuotePrice() {
    final var instructions = rebuild();
    final var staging = GlamAccounts.MAIN_NET_STAGING.protocolProgram();

    assertEquals(fromBase58Encoded("KLend2g3cP87fffoy8q1mQqGKjrxjC8boSyAYavgmjD"), instructions.getFirst().programId().publicKey());
    assertEquals(staging, instructions.get(2).programId().publicKey());

    final var swap = instructions.getLast();
    assertEquals(staging, swap.programId().publicKey());
    final var data = GlamProtocolProgram.JupiterSwapV2IxData.read(swap);
    assertEquals(GlamProtocolProgram.JUPITER_SWAP_V_2_DISCRIMINATOR, data.discriminator());
    assertFalse(data.skipQuotePriceCheck());
  }
}
