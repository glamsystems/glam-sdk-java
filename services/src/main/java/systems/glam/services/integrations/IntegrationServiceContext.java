package systems.glam.services.integrations;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.drift.DriftAccounts;
import software.sava.idl.clients.kamino.KaminoAccounts;
import systems.glam.services.fulfillment.drfit.DriftMarketCache;
import systems.glam.services.integrations.kamino.KaminoVaultCache;
import systems.glam.services.pricing.IntegTableCache;
import systems.glam.services.pricing.RunnableAccountConsumer;
import systems.glam.services.tokens.MintContext;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;

public interface IntegrationServiceContext {

  static Path resolveFileName(final Path directory, final PublicKey accountKey) {
    return directory.resolve(accountKey + ".dat");
  }

  void queue(final Collection<PublicKey> accounts, final RunnableAccountConsumer callback);

  PublicKey solUSDOracleKey();

  PublicKey baseAssetUSDOracleKey();

  MintContext mintContext(final PublicKey mint);

  IntegTableCache integTableCache();

  DriftMarketCache driftMarketCache();

  DriftAccounts driftAccounts();

  PublicKey driftProgram();

  PublicKey driftVaultsProgram();

  KaminoVaultCache kaminoVaultCache();

  KaminoAccounts kaminoAccounts();

  Set<PublicKey> kaminoTableKeys();

  PublicKey kLendProgram();

  PublicKey kVaultsProgram();
}
