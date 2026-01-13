package systems.glam.services.integrations;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.drift.DriftAccounts;
import software.sava.idl.clients.kamino.KaminoAccounts;
import systems.glam.services.fulfillment.drfit.DriftMarketCache;
import systems.glam.services.integrations.kamino.KaminoVaultCache;
import systems.glam.services.tokens.MintContext;

public interface IntegrationServiceContext {
  DriftMarketCache driftMarketCache();

  DriftAccounts driftAccounts();

  PublicKey driftProgram();

  PublicKey driftVaultsProgram();

  KaminoVaultCache kaminoVaultCache();

  KaminoAccounts kaminoAccounts();

  PublicKey kLendProgram();

  PublicKey kVaultsProgram();

  MintContext mintContext(PublicKey mint);
}
