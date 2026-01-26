package systems.glam.services.integrations;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.drift.DriftAccounts;
import software.sava.idl.clients.kamino.KaminoAccounts;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.sdk.idl.programs.glam.config.gen.types.AssetMeta;
import systems.glam.services.GlobalConfigCache;
import systems.glam.services.ServiceContext;
import systems.glam.services.integrations.drift.DriftMarketCache;
import systems.glam.services.integrations.kamino.KaminoVaultCache;
import systems.glam.services.mints.MintCache;
import systems.glam.services.mints.MintContext;
import systems.glam.services.rpc.AccountConsumer;
import systems.glam.services.rpc.AccountFetcher;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;

import static systems.glam.services.io.FileUtils.ACCOUNT_FILE_EXTENSION;

public interface IntegrationServiceContext {

  static IntegrationServiceContext createContext(final ServiceContext serviceContext,
                                                 final MintCache mintCache,
                                                 final GlobalConfigCache globalConfigCache,
                                                 final IntegLookupTableCache integLookupTableCache,
                                                 final AccountFetcher accountFetcher,
                                                 final DriftAccounts driftAccounts,
                                                 final DriftMarketCache driftMarketCache,
                                                 final KaminoAccounts kaminoAccounts,
                                                 final KaminoVaultCache kaminoVaultCache) {
    return new IntegrationServiceContextImpl(
        serviceContext,
        mintCache,
        globalConfigCache,
        integLookupTableCache,
        accountFetcher,
        driftAccounts, driftMarketCache,
        kaminoAccounts, kaminoVaultCache
    );
  }

  static Path resolveFileName(final Path directory, final PublicKey accountKey) {
    return directory.resolve(accountKey + ACCOUNT_FILE_EXTENSION);
  }

  ServiceContext serviceContext();

  boolean isTokenMint(final AccountInfo<byte[]> accountInfo);

  boolean isTokenAccount(final AccountInfo<byte[]> accountInfo);

  Path resolveGlamStateFilePath(final PublicKey glamStateKey);

  void queue(final Collection<PublicKey> accounts, final AccountConsumer callback);

  void executeTask(final Runnable task);

  RpcCaller rpcCaller();

  MintContext mintContext(final PublicKey mint);

  MintContext setMintContext(final MintContext mintContext);

  MintContext setMintContext(final AccountInfo<byte[]> accountInfo);

  AssetMeta globalConfigAssetMeta(final PublicKey mint);

  IntegLookupTableCache integTableCache();

  default Path driftCacheDirectory() {
    return serviceContext().accountsCacheDirectory().resolve("drift");
  }

  DriftMarketCache driftMarketCache();

  DriftAccounts driftAccounts();

  PublicKey driftProgram();

  PublicKey driftVaultsProgram();

  KaminoVaultCache kaminoVaultCache();

  KaminoAccounts kaminoAccounts();

  PublicKey kLendProgram();

  PublicKey kVaultsProgram();
}
