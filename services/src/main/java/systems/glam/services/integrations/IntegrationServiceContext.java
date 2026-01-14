package systems.glam.services.integrations;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.drift.DriftAccounts;
import software.sava.idl.clients.kamino.KaminoAccounts;
import systems.glam.services.fulfillment.drfit.DriftMarketCache;
import systems.glam.services.integrations.kamino.KaminoVaultCache;
import systems.glam.services.pricing.RunnableAccountConsumer;
import systems.glam.services.tokens.MintContext;

import java.util.Collection;

public interface IntegrationServiceContext {

  void queue(final Collection<PublicKey> accounts, final RunnableAccountConsumer callback);

  PublicKey solUSDOracleKey();

  PublicKey baseAssetUSDOracleKey();

  DriftMarketCache driftMarketCache();

  DriftAccounts driftAccounts();

  PublicKey driftProgram();

  PublicKey driftVaultsProgram();

  KaminoVaultCache kaminoVaultCache();

  KaminoAccounts kaminoAccounts();

  PublicKey kLendProgram();

  PublicKey kVaultsProgram();

  MintContext mintContext(final PublicKey mint);
}
