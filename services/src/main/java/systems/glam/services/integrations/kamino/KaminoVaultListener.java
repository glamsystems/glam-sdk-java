package systems.glam.services.integrations.kamino;

import software.sava.core.accounts.PublicKey;

public interface KaminoVaultListener {

  PublicKey key();

  void onKaminoVaultChange(final KaminoVaultContext vaultContext);
}
