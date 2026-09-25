package systems.glam.sdk;

import software.sava.core.accounts.ProgramDerivedAddress;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import systems.glam.ix.proxy.MappingContext;
import systems.glam.sdk.idl.programs.glam.protocol.gen.GlamProtocolPDAs;

public interface GlamVaultAccounts {

  static GlamVaultAccounts createAccounts(final GlamAccounts glamAccounts,
                                          final PublicKey feePayer,
                                          final PublicKey glamStateKey) {
    final var protocolProgram = glamAccounts.protocolProgram();
    final var vaultPDA = GlamProtocolPDAs.glamVaultPDA(protocolProgram, glamStateKey);
    return new GlamVaultAccountsRecord(
        glamAccounts,
        AccountMeta.createReadOnlySigner(feePayer),
        AccountMeta.createWritableSigner(feePayer),
        AccountMeta.createRead(glamStateKey),
        AccountMeta.createWrite(glamStateKey),
        vaultPDA,
        AccountMeta.createRead(vaultPDA.publicKey()),
        AccountMeta.createWrite(vaultPDA.publicKey())
    );
  }

  static GlamVaultAccounts createAccounts(final PublicKey feePayer, final PublicKey glamStatePublicKey) {
    return GlamVaultAccounts.createAccounts(GlamAccounts.MAIN_NET, feePayer, glamStatePublicKey);
  }

  GlamAccounts glamAccounts();

  PublicKey feePayer();

  PublicKey glamStateKey();

  AccountMeta writeGlamState();

  AccountMeta readGlamState();

  ProgramDerivedAddress vaultPDA();

  default PublicKey vaultPublicKey() {
    return vaultPDA().publicKey();
  }

  AccountMeta writeVault();

  AccountMeta readVault();

  /// What the ix-mapper needs from this vault to rewrite an instruction into its GLAM proxy equivalent: the
  /// state and vault keys, the fee payer as the signer, and the integration authority of each proxy program
  /// the [#glamAccounts()] hold.
  default MappingContext mappingContext() {
    return new MappingContext(glamStateKey(), vaultPublicKey(), feePayer(), glamAccounts()::integrationAuthority);
  }

  ProgramDerivedAddress mintPDA(final int id);

  default ProgramDerivedAddress mintPDA() {
    return mintPDA(0);
  }
}
