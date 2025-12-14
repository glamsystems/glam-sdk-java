package systems.glam.sdk.idl.programs.glam.jupiter;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.jupiter.JupiterAccounts;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.sdk.GlamVaultAccounts;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public interface GlamJupiterProgramClient {

  static GlamJupiterProgramClient createClient(final GlamAccountClient nativeProgramAccountClient,
                                               final JupiterAccounts jupiterAccounts) {
    return new GlamJupiterProgramClientImpl(nativeProgramAccountClient, jupiterAccounts);
  }

  static GlamJupiterProgramClient createClient(final GlamAccountClient nativeProgramAccountClient) {
    return createClient(nativeProgramAccountClient, JupiterAccounts.MAIN_NET);
  }

  /// Removes signature requirements for the vault key.
  ///
  /// Jupiter assumes a direct call instead of a CPI call, which implicitly considers the calling program to be authorized.
  static List<AccountMeta> fixCPICallerRights(final List<AccountMeta> accountList) {
    final var accounts = accountList.toArray(AccountMeta[]::new);
    for (int i = 0; i < accounts.length; i++) {
      final var account = accounts[i];
      if (account.signer()) {
        accounts[i] = account.write()
            ? AccountMeta.createWrite(account.publicKey())
            : AccountMeta.createRead(account.publicKey());
        break;
      }
    }
    return Arrays.asList(accounts);
  }

  /// Removes signature requirements for the vault key.
  ///
  /// Jupiter assumes a direct call instead of a CPI call, which implicitly considers the calling program to be authorized.
  static Instruction fixCPICallerRights(final Instruction swapIx) {
    return Instruction.createInstruction(
        swapIx.programId(),
        fixCPICallerRights(swapIx.accounts()),
        swapIx.data()
    );
  }

  SolanaAccounts solanaAccounts();

  GlamVaultAccounts glamVaultAccounts();

  JupiterAccounts jupiterAccounts();

  default List<Instruction> swapChecked(final PublicKey inputMintKey,
                                        final PublicKey inputTokenProgram,
                                        final PublicKey outputMintKey,
                                        final PublicKey outputTokenProgram,
                                        final long amount,
                                        final Instruction swapInstruction,
                                        final boolean wrapSOL) {
    return swapWithProgramStateChecked(
        null, inputMintKey, inputTokenProgram,
        null, outputMintKey, outputTokenProgram,
        amount,
        swapInstruction,
        wrapSOL
    );
  }

  default List<Instruction> swapChecked(final PublicKey inputMintKey,
                                        final PublicKey outputMintKey,
                                        final long amount,
                                        final Instruction swapInstruction,
                                        final boolean wrapSOL) {
    final var tokenProgram = solanaAccounts().tokenProgram();
    return swapChecked(inputMintKey, tokenProgram, outputMintKey, tokenProgram, amount, swapInstruction, wrapSOL);
  }

  default List<Instruction> swapChecked(final PublicKey inputMintKey,
                                        final PublicKey outputMintKey,
                                        final long amount,
                                        final Instruction swapInstruction) {
    return swapChecked(inputMintKey, outputMintKey, amount, swapInstruction, true);
  }

  Map<PublicKey, Instruction> createSwapTokenAccountsIdempotent(final PublicKey inputTokenProgram,
                                                                final PublicKey inputMintKey,
                                                                final PublicKey outputTokenProgram,
                                                                final PublicKey outputMintKey);

  default Instruction swapUncheckedAndNoWrap(final PublicKey inputMintKey,
                                             final PublicKey inputTokenProgram,
                                             final PublicKey outputMintKey,
                                             final PublicKey outputTokenProgram,
                                             final Instruction swapInstruction) {
    return swapWithProgramStateUncheckedAndNoWrap(
        null, inputMintKey, inputTokenProgram,
        null, outputMintKey, outputTokenProgram,
        swapInstruction
    );
  }

  default Instruction swapUncheckedAndNoWrap(final PublicKey inputMintKey,
                                             final PublicKey outputMintKey,
                                             final Instruction swapInstruction) {
    final var tokenProgram = solanaAccounts().tokenProgram();
    return swapWithProgramStateUncheckedAndNoWrap(
        null, inputMintKey, tokenProgram,
        null, outputMintKey, tokenProgram,
        swapInstruction
    );
  }

  default List<Instruction> swapUnchecked(final PublicKey inputMintKey,
                                          final PublicKey inputTokenProgram,
                                          final PublicKey outputMintKey,
                                          final PublicKey outputTokenProgram,
                                          long amount,
                                          final Instruction swapInstruction,
                                          final boolean wrapSOL) {
    return swapWithProgramStateUnchecked(
        null, inputMintKey, inputTokenProgram,
        null, outputMintKey, outputTokenProgram,
        amount,
        swapInstruction,
        wrapSOL
    );
  }

  default List<Instruction> swapUnchecked(final PublicKey inputMintKey,
                                          final PublicKey outputMintKey,
                                          final long amount,
                                          final Instruction swapInstruction,
                                          final boolean wrapSOL) {
    final var tokenProgram = solanaAccounts().tokenProgram();
    return swapUnchecked(inputMintKey, tokenProgram, outputMintKey, tokenProgram, amount, swapInstruction, wrapSOL);
  }

  default List<Instruction> swapUnchecked(final PublicKey inputMintKey,
                                          final PublicKey outputMintKey,
                                          final long amount,
                                          final Instruction swapInstruction) {
    final var tokenProgram = solanaAccounts().tokenProgram();
    return swapUnchecked(inputMintKey, tokenProgram, outputMintKey, tokenProgram, amount, swapInstruction, true);
  }

  // in/out program state keys may be used to check permission for swapping assets more generically.
  // For example, a user may have access to swap any LST;
  // this is checked by also passing the corresponding stake pool state program.

  default Instruction swapWithProgramStateUncheckedAndNoWrap(final PublicKey inputProgramStateKey,
                                                             final PublicKey inputMintKey,
                                                             final PublicKey outputProgramStateKey,
                                                             final PublicKey outputMintKey,
                                                             final Instruction swapInstruction) {
    final var tokenProgram = solanaAccounts().tokenProgram();
    return swapWithProgramStateUncheckedAndNoWrap(
        inputProgramStateKey, inputMintKey, tokenProgram,
        outputProgramStateKey, outputMintKey, tokenProgram,
        swapInstruction
    );
  }

  default List<Instruction> swapWithProgramStateChecked(final PublicKey inputProgramStateKey,
                                                        final PublicKey inputMintKey,
                                                        final PublicKey outputProgramStateKey,
                                                        final PublicKey outputMintKey,
                                                        final long amount,
                                                        final Instruction swapInstruction,
                                                        final boolean wrapSOL) {
    final var tokenProgram = solanaAccounts().tokenProgram();
    return swapWithProgramStateChecked(
        inputProgramStateKey, inputMintKey, tokenProgram,
        outputProgramStateKey, outputMintKey, tokenProgram,
        amount, swapInstruction, wrapSOL
    );
  }

  default List<Instruction> swapWithProgramStateChecked(final PublicKey inputProgramStateKey,
                                                        final PublicKey inputMintKey,
                                                        final PublicKey outputProgramStateKey,
                                                        final PublicKey outputMintKey,
                                                        final long amount,
                                                        final Instruction swapInstruction) {
    return swapWithProgramStateChecked(
        inputProgramStateKey, inputMintKey,
        outputProgramStateKey, outputMintKey,
        amount, swapInstruction, true
    );
  }

  List<Instruction> swapWithProgramStateChecked(final PublicKey inputProgramStateKey,
                                                final PublicKey inputMintKey,
                                                final PublicKey inputTokenProgram,
                                                final PublicKey outputProgramStateKey,
                                                final PublicKey outputMintKey,
                                                final PublicKey outputTokenProgram,
                                                final long amount,
                                                final Instruction swapInstruction,
                                                final boolean wrapSOL);

  Instruction swapWithProgramStateUncheckedAndNoWrap(final PublicKey inputProgramStateKey,
                                                     final PublicKey inputMintKey,
                                                     final PublicKey inputTokenProgram,
                                                     final PublicKey outputProgramStateKey,
                                                     final PublicKey outputMintKey,
                                                     final PublicKey outputTokenProgram,
                                                     final Instruction swapInstruction);

  default List<Instruction> swapWithProgramStateUnchecked(final PublicKey inputProgramStateKey,
                                                          final PublicKey inputMintKey,
                                                          final PublicKey outputProgramStateKey,
                                                          final PublicKey outputMintKey,
                                                          final long amount,
                                                          final Instruction swapInstruction,
                                                          final boolean wrapSOL) {
    final var tokenProgram = solanaAccounts().tokenProgram();
    return swapWithProgramStateUnchecked(
        inputProgramStateKey, inputMintKey, tokenProgram,
        outputProgramStateKey, outputMintKey, tokenProgram,
        amount, swapInstruction, wrapSOL
    );
  }

  default List<Instruction> swapWithProgramStateUnchecked(final PublicKey inputProgramStateKey,
                                                          final PublicKey inputMintKey,
                                                          final PublicKey outputProgramStateKey,
                                                          final PublicKey outputMintKey,
                                                          final long amount,
                                                          final Instruction swapInstruction) {
    final var tokenProgram = solanaAccounts().tokenProgram();
    return swapWithProgramStateUnchecked(
        inputProgramStateKey, inputMintKey, tokenProgram,
        outputProgramStateKey, outputMintKey, tokenProgram,
        amount, swapInstruction, true
    );
  }

  List<Instruction> swapWithProgramStateUnchecked(final PublicKey inputProgramStateKey,
                                                  final PublicKey inputMintKey,
                                                  final PublicKey inputTokenProgram,
                                                  final PublicKey outputProgramStateKey,
                                                  final PublicKey outputMintKey,
                                                  final PublicKey outputTokenProgram,
                                                  final long amount,
                                                  final Instruction swapInstruction,
                                                  final boolean wrapSOL);
}
