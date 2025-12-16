package systems.glam.sdk.idl.programs.glam.jupiter;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.jupiter.JupiterAccounts;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.sdk.GlamVaultAccounts;
import systems.glam.sdk.idl.programs.glam.protocol.gen.GlamProtocolProgram;

import java.util.List;
import java.util.Map;

final class GlamJupiterProgramClientImpl implements GlamJupiterProgramClient {

  private final GlamAccountClient glamAccountClient;
  private final SolanaAccounts solanaAccounts;
  private final GlamVaultAccounts glamVaultAccounts;
  private final AccountMeta invokedProgram;
  private final AccountMeta feePayer;
  private final JupiterAccounts jupiterAccounts;
  private final PublicKey swapProgram;

  GlamJupiterProgramClientImpl(final GlamAccountClient glamAccountClient,
                               final JupiterAccounts jupiterAccounts) {
    this.glamAccountClient = glamAccountClient;
    this.solanaAccounts = glamAccountClient.solanaAccounts();
    this.glamVaultAccounts = glamAccountClient.vaultAccounts();
    this.feePayer = glamAccountClient.feePayer();
    this.invokedProgram = glamVaultAccounts.glamAccounts().invokedProtocolProgram();
    this.jupiterAccounts = jupiterAccounts;
    this.swapProgram = jupiterAccounts.swapProgram();
  }

  @Override
  public SolanaAccounts solanaAccounts() {
    return solanaAccounts;
  }

  @Override
  public GlamVaultAccounts glamVaultAccounts() {
    return glamVaultAccounts;
  }

  @Override
  public JupiterAccounts jupiterAccounts() {
    return jupiterAccounts;
  }

  private Instruction jupiterSwap(final PublicKey inputProgramStateKey,
                                  final PublicKey outputProgramStateKey,
                                  final Instruction swapInstruction) {
    final var fixedAccounts = GlamJupiterProgramClient.fixCPICallerRights(swapInstruction.accounts());
    return GlamProtocolProgram.jupiterSwap(
        invokedProgram,
        glamVaultAccounts.glamStateKey(),
        glamVaultAccounts.vaultPublicKey(),
        feePayer.publicKey(),
        swapProgram,
        inputProgramStateKey, outputProgramStateKey,
        swapInstruction.data()
    ).extraAccounts(fixedAccounts);
  }

  @Override
  public Map<PublicKey, Instruction> createSwapTokenAccountsIdempotent(final PublicKey inputTokenProgram,
                                                                       final PublicKey inputMintKey,
                                                                       final PublicKey outputTokenProgram,
                                                                       final PublicKey outputMintKey) {
    final var outputVaultATA = glamAccountClient.findATA(outputTokenProgram, outputMintKey).publicKey();
    final var createVaultOutputATA = glamAccountClient.createATAForOwnerFundedByFeePayer(
        true, outputVaultATA, outputMintKey, outputTokenProgram
    );

    if (inputMintKey.equals(solanaAccounts.wrappedSolTokenMint())) {
      final var inputVaultATA = glamAccountClient.findATA(inputTokenProgram, inputMintKey).publicKey();
      final var createVaultInputATA = glamAccountClient.createATAForOwnerFundedByFeePayer(
          true, inputVaultATA, inputMintKey, inputTokenProgram
      );
      return Map.of(
          inputVaultATA, createVaultInputATA,
          outputVaultATA, createVaultOutputATA
      );
    } else {
      return Map.of(outputVaultATA, createVaultOutputATA);
    }
  }

  @Override
  public List<Instruction> swapWithProgramStateChecked(final PublicKey inputProgramStateKey,
                                                       final PublicKey inputMintKey,
                                                       final PublicKey inputTokenProgram,
                                                       final PublicKey outputProgramStateKey,
                                                       final PublicKey outputMintKey,
                                                       final PublicKey outputTokenProgram,
                                                       final long amount,
                                                       final Instruction swapInstruction,
                                                       final boolean wrapSOL) {
    final var inputVaultATA = glamAccountClient.findATA(inputTokenProgram, inputMintKey).publicKey();
    final var outputVaultATA = glamAccountClient.findATA(outputTokenProgram, outputMintKey).publicKey();
    final var createVaultOutputATA = glamAccountClient.createATAForOwnerFundedByFeePayer(
        true, outputVaultATA, outputMintKey, outputTokenProgram
    );
    final var glamJupiterSwap = jupiterSwap(
        inputProgramStateKey,
        outputProgramStateKey,
        swapInstruction
    );

    if (wrapSOL && inputMintKey.equals(solanaAccounts.wrappedSolTokenMint())) {
      return List.of(
          glamAccountClient.createATAForOwnerFundedByFeePayer(
              true, inputVaultATA, inputMintKey, inputTokenProgram
          ),
          glamAccountClient.transferSolLamports(inputVaultATA, amount),
          glamAccountClient.syncNative(),
          createVaultOutputATA,
          glamJupiterSwap
      );
    } else {
      return List.of(createVaultOutputATA, glamJupiterSwap);
    }
  }

  @Override
  public Instruction swapWithProgramStateUncheckedAndNoWrap(final PublicKey inputProgramStateKey,
                                                            final PublicKey inputMintKey,
                                                            final PublicKey inputTokenProgram,
                                                            final PublicKey outputProgramStateKey,
                                                            final PublicKey outputMintKey,
                                                            final PublicKey outputTokenProgram,
                                                            final Instruction swapInstruction) {
//    final var inputVaultATA = glamAccountClient.findATA(inputTokenProgram, inputMintKey).publicKey();
//    final var outputVaultATA = glamAccountClient.findATA(outputTokenProgram, outputMintKey).publicKey();
    return jupiterSwap(
        inputProgramStateKey,
        outputProgramStateKey,
        swapInstruction
    );
  }

  @Override
  public List<Instruction> swapWithProgramStateUnchecked(final PublicKey inputProgramStateKey,
                                                         final PublicKey inputMintKey,
                                                         final PublicKey inputTokenProgram,
                                                         final PublicKey outputProgramStateKey,
                                                         final PublicKey outputMintKey,
                                                         final PublicKey outputTokenProgram,
                                                         final long amount,
                                                         final Instruction swapInstruction,
                                                         final boolean wrapSOL) {
    final var glamJupiterSwap = swapWithProgramStateUncheckedAndNoWrap(
        inputProgramStateKey, inputMintKey, inputTokenProgram,
        outputProgramStateKey, outputMintKey, outputTokenProgram,
        swapInstruction
    );
    if (wrapSOL && inputMintKey.equals(solanaAccounts.wrappedSolTokenMint())) {
      final var wrappedSolPDA = glamAccountClient.wrappedSolPDA().publicKey();
      return List.of(
          glamAccountClient.transferSolLamports(wrappedSolPDA, amount),
          glamAccountClient.syncNative(),
          glamJupiterSwap
      );
    } else {
      return List.of(glamJupiterSwap);
    }
  }
}
