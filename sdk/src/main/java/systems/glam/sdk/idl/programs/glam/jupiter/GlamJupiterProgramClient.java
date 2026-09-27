package systems.glam.sdk.idl.programs.glam.jupiter;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.jupiter.JupiterAccounts;
import software.sava.idl.clients.kamino.KaminoAccounts;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.sdk.GlamVaultAccounts;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public interface GlamJupiterProgramClient {

  static GlamJupiterProgramClient createClient(final GlamAccountClient nativeProgramAccountClient,
                                               final JupiterAccounts jupiterAccounts,
                                               final KaminoAccounts kaminoAccounts) {
    return new GlamJupiterProgramClientImpl(nativeProgramAccountClient, jupiterAccounts, kaminoAccounts);
  }

  static GlamJupiterProgramClient createClient(final GlamAccountClient nativeProgramAccountClient,
                                               final JupiterAccounts jupiterAccounts) {
    return createClient(nativeProgramAccountClient, jupiterAccounts, KaminoAccounts.MAIN_NET);
  }

  static GlamJupiterProgramClient createClient(final GlamAccountClient nativeProgramAccountClient) {
    return createClient(nativeProgramAccountClient, JupiterAccounts.MAIN_NET);
  }

  /// Removes the signature requirement of `vault` wherever the route seats it, and of no other account:
  /// Jupiter assumes a direct call, so its route seats the vault as a signer, but the vault PDA signs
  /// through the GLAM program's CPI, and a route may seat other signers before it. A vault seat that
  /// does not sign is rewritten to the same read or write meta.
  static List<AccountMeta> fixCPICallerRights(final List<AccountMeta> accountList, final PublicKey vault) {
    final var accounts = accountList.toArray(AccountMeta[]::new);
    for (int i = 0; i < accounts.length; i++) {
      final var account = accounts[i];
      if (vault.equals(account.publicKey())) {
        accounts[i] = account.write()
            ? AccountMeta.createWrite(vault)
            : AccountMeta.createRead(vault);
      }
    }
    return Arrays.asList(accounts);
  }

  /// The route with `vault`'s signature requirement removed, as [#fixCPICallerRights(List, PublicKey)]
  /// rewrites its accounts; program and data are unchanged.
  static Instruction fixCPICallerRights(final Instruction swapIx, final PublicKey vault) {
    return Instruction.createInstruction(
        swapIx.programId(),
        fixCPICallerRights(swapIx.accounts(), vault),
        swapIx.data()
    );
  }

  SolanaAccounts solanaAccounts();

  GlamVaultAccounts glamVaultAccounts();

  JupiterAccounts jupiterAccounts();

  KaminoAccounts kaminoAccounts();

  /// The instructions of one swap, in order: a `refresh_reserves_batch` over the context's Kamino
  /// reserves that price a role, if any; the wrap prelude for a wSOL input when asked (the vault's wSOL
  /// account funded by a GLAM system transfer carrying the Token program, then synced); the vault's
  /// output token account, and for the checked form its input account, created idempotently; then the
  /// GLAM `jupiter_swap_v2` CPI, which always names the global configuration and the context's oracles so
  /// the program can price the swap whether or not the requested skip is granted.
  List<Instruction> swap(final JupiterSwapContext swapContext);

  Map<PublicKey, Instruction> createSwapTokenAccountsIdempotent(final JupiterSwapContext swapContext);

  Map<PublicKey, Instruction> createSwapTokenAccountsIdempotent(final PublicKey inputTokenProgram,
                                                                final PublicKey inputMintKey,
                                                                final PublicKey outputTokenProgram,
                                                                final PublicKey outputMintKey);
}
