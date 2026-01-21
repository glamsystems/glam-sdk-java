package systems.glam.services.integrations;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.drift.DriftAccounts;
import software.sava.idl.clients.kamino.KaminoAccounts;
import systems.glam.sdk.idl.programs.glam.config.gen.types.AssetMeta;
import systems.glam.services.integrations.drift.DriftMarketCache;
import systems.glam.services.integrations.kamino.KaminoVaultCache;
import systems.glam.services.rpc.AccountConsumer;
import systems.glam.services.mints.MintContext;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;

public interface IntegrationServiceContext {

  static Path resolveFileName(final Path directory, final PublicKey accountKey) {
    return directory.resolve(accountKey + ".dat");
  }

  void queue(final Collection<PublicKey> accounts, final AccountConsumer callback);

  PublicKey solUSDOracleKey();

  PublicKey baseAssetUSDOracleKey();

  MintContext mintContext(final PublicKey mint);

  MintContext setMintContext(final MintContext mintContext);

  AssetMeta globalConfigAssetMeta(final PublicKey mint);

  IntegLookupTableCache integTableCache();

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
