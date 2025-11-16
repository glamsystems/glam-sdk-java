package systems.glam.sdk;

import software.sava.core.accounts.ProgramDerivedAddress;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;

public record GlamVaultAccountsRecord(GlamAccounts glamAccounts,
                                      AccountMeta readFeePayer,
                                      AccountMeta writeFeePayer,
                                      AccountMeta readGlamState,
                                      AccountMeta writeGlamState,
                                      ProgramDerivedAddress vaultPDA,
                                      AccountMeta readVault,
                                      AccountMeta writeVault) implements GlamVaultAccounts {

  @Override
  public PublicKey feePayer() {
    return readFeePayer.publicKey();
  }

  @Override
  public PublicKey glamStateKey() {
    return readGlamState.publicKey();
  }

  @Override
  public ProgramDerivedAddress mintPDA(final int id) {
    return glamAccounts.mintPDA(glamStateKey(), id);
  }
}
