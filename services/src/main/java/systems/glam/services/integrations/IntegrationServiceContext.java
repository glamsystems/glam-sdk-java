package systems.glam.services.integrations;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.idl.clients.drift.DriftAccounts;
import software.sava.idl.clients.kamino.KaminoAccounts;
import software.sava.idl.clients.kamino.scope.gen.types.OracleType;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.services.ServiceContext;
import systems.glam.services.integrations.drift.DriftMarketCache;
import systems.glam.services.integrations.kamino.KaminoVaultCache;
import systems.glam.services.mints.*;
import systems.glam.services.oracles.scope.FeedIndexes;
import systems.glam.services.oracles.scope.KaminoCache;
import systems.glam.services.pricing.accounting.VaultAumRecord;
import systems.glam.services.rpc.AccountConsumer;
import systems.glam.services.rpc.AccountFetcher;
import systems.glam.services.state.GlobalConfigCache;

import java.nio.file.Path;
import java.util.Collection;

import static systems.glam.services.io.FileUtils.ACCOUNT_FILE_EXTENSION;

public interface IntegrationServiceContext {

  static IntegrationServiceContext createContext(final ServiceContext serviceContext,
                                                 final MintCache mintCache,
                                                 final StakePoolCache stakePoolCache,
                                                 final GlobalConfigCache globalConfigCache,
                                                 final IntegLookupTableCache integLookupTableCache,
                                                 final AccountFetcher accountFetcher,
                                                 final DriftAccounts driftAccounts,
                                                 final DriftMarketCache driftMarketCache,
                                                 final KaminoAccounts kaminoAccounts,
                                                 final KaminoCache kaminoCache,
                                                 final KaminoVaultCache kaminoVaultCache) {
    return new IntegrationServiceContextImpl(
        serviceContext,
        mintCache,
        stakePoolCache,
        globalConfigCache,
        integLookupTableCache,
        accountFetcher,
        driftAccounts, driftMarketCache,
        kaminoAccounts, kaminoCache, kaminoVaultCache
    );
  }

  static Path resolveFileName(final Path directory, final PublicKey accountKey) {
    return directory.resolve(accountKey + ACCOUNT_FILE_EXTENSION);
  }

  ServiceContext serviceContext();

  default AccountMeta readClockSysVar() {
    return serviceContext().readClockSysVar();
  }

  boolean isTokenMint(final AccountInfo<byte[]> accountInfo);

  boolean isTokenAccount(final AccountInfo<byte[]> accountInfo);

  StakePoolContext stakePoolContextForMint(final PublicKey mintKey);

  Path resolveGlamStateFilePath(final PublicKey glamStateKey);

  void queue(final Collection<PublicKey> accounts, final AccountConsumer callback);

  void executeTask(final Runnable task);

  RpcCaller rpcCaller();

  MintContext mintContext(final PublicKey mint);

  MintContext setMintContext(final MintContext mintContext);

  MintContext setMintContext(final AccountInfo<byte[]> accountInfo);

  AssetMetaContext solAssetMeta();

  GlobalConfigCache globalConfigCache();

  AssetMetaContext globalConfigAssetMeta(final PublicKey mint);

  AssetMetaContext watchForMint(final PublicKey mint, final PublicKey stateAccount);

  FeedIndexes scopeAggregateIndexes(final PublicKey mint, final PublicKey oracle, final OracleType oracleType);

  void persistAumRecord(final VaultAumRecord aumRecord) throws InterruptedException;

  IntegLookupTableCache integTableCache();

  DriftMarketCache driftMarketCache();

  DriftAccounts driftAccounts();

  PublicKey driftProgram();

  PublicKey driftVaultsProgram();

  KaminoCache kaminoCache();

  KaminoVaultCache kaminoVaultCache();

  KaminoAccounts kaminoAccounts();

  PublicKey kLendProgram();

  PublicKey kVaultsProgram();
}
