package systems.glam.services.integrations;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.idl.clients.jupiter.JupiterAccounts;
import software.sava.idl.clients.kamino.KaminoAccounts;
import software.sava.idl.clients.kamino.scope.gen.types.OracleType;
import software.sava.idl.clients.loopscale.LoopscaleAccounts;
import software.sava.idl.clients.marginfi.v2.MarginfiAccounts;
import software.sava.idl.clients.meteora.MeteoraAccounts;
import software.sava.idl.clients.orca.OrcaAccounts;
import software.sava.idl.clients.phoenix.PhoenixAccounts;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.sdk.GlamEnv;
import systems.glam.services.ServiceContext;
import systems.glam.services.integrations.kamino.KaminoCache;
import systems.glam.services.mints.*;
import systems.glam.services.oracles.scope.FeedIndexes;
import systems.glam.services.rpc.AccountConsumer;
import systems.glam.services.rpc.AccountFetcher;
import systems.glam.services.state.GlobalConfigCache;

import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.ExecutorService;

public interface IntegrationServiceContext {

  static IntegrationServiceContext createContext(final ServiceContext serviceContext,
                                                 final MintCache mintCache,
                                                 final StakePoolCache stakePoolCache,
                                                 final GlobalConfigCache globalConfigCache,
                                                 final IntegLookupTableCache integLookupTableCache,
                                                 final AccountFetcher accountFetcher,
                                                 final KaminoAccounts kaminoAccounts,
                                                 final KaminoCache kaminoCache,
                                                 final LoopscaleAccounts loopscaleAccounts,
                                                 final OrcaAccounts orcaAccounts,
                                                 final PhoenixAccounts phoenixAccounts,
                                                 final MarginfiAccounts marginfiAccounts,
                                                 final JupiterAccounts jupiterAccounts,
                                                 final MeteoraAccounts meteoraAccounts) {
    return new IntegrationServiceContextImpl(
        serviceContext,
        mintCache,
        stakePoolCache,
        globalConfigCache,
        integLookupTableCache,
        accountFetcher,
        kaminoAccounts, kaminoCache,
        loopscaleAccounts,
        orcaAccounts,
        phoenixAccounts,
        marginfiAccounts,
        jupiterAccounts,
        meteoraAccounts
    );
  }

  ServiceContext serviceContext();

  default AccountMeta readClockSysVar() {
    return serviceContext().readClockSysVar();
  }

  boolean isTokenMint(final AccountInfo<byte[]> accountInfo);

  boolean isTokenAccount(final AccountInfo<byte[]> accountInfo);

  StakePoolContext stakePoolContextForMint(final PublicKey mintKey);

  Path resolveGlamStateFilePath(final GlamEnv glamEnv, final PublicKey glamStateKey);

  AccountFetcher accountFetcher();

  void queueUnique(final Collection<PublicKey> accounts, final AccountConsumer callback);

  void executeTask(final Runnable task);

  ExecutorService taskExecutor();

  RpcCaller rpcCaller();

  MintContext mintContext(final PublicKey mint);

  MintContext setMintContext(final MintContext mintContext);

  MintContext setMintContext(final AccountInfo<byte[]> accountInfo);

  AssetMetaContext solAssetMeta();

  GlobalConfigCache globalConfigCache();

  AssetMetaContext globalConfigAssetMeta(final PublicKey mint);

  FeedIndexes scopeAggregateIndexes(final PublicKey mint, final PublicKey oracle, final OracleType oracleType);

  OrcaAccounts orcaAccounts();

  PublicKey orcaWhirlpoolProgram();

  PhoenixAccounts phoenixAccounts();

  PublicKey phoenixEternalProgram();

  PublicKey phoenixEmberProgram();

  MarginfiAccounts marginfiAccounts();

  JupiterAccounts jupiterAccounts();

  IntegLookupTableCache integTableCache();

  KaminoCache kaminoCache();

  KaminoAccounts kaminoAccounts();

  PublicKey kLendProgram();

  PublicKey kVaultsProgram();

  LoopscaleAccounts loopscaleAccounts();

  PublicKey loopscaleProgram();

  MeteoraAccounts meteoraAccounts();
}
