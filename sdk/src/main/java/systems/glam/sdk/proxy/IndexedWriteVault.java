package systems.glam.sdk.proxy;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import systems.glam.ix.proxy.DynamicAccount;
import systems.glam.sdk.GlamVaultAccounts;

public record IndexedWriteVault(int index) implements DynamicAccount<GlamVaultAccounts> {

  @Override
  public void setAccount(final AccountMeta[] mappedAccounts,
                         final PublicKey proxyProgram,
                         final AccountMeta cpiProgram,
                         final AccountMeta feePayer,
                         final GlamVaultAccounts runtimeAccounts) {
    mappedAccounts[index] = runtimeAccounts.writeVault();
  }
}
