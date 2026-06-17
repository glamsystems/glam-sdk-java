package systems.glam.services.integrations;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.kamino.KaminoAccounts;
import software.sava.idl.clients.kamino.scope.gen.types.OracleType;
import software.sava.idl.clients.loopscale.LoopscaleAccounts;
import software.sava.idl.clients.orca.OrcaAccounts;
import software.sava.idl.clients.phoenix.PhoenixAccounts;
import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.services.ServiceContext;
import systems.glam.services.execution.BaseServiceContext;
import systems.glam.services.integrations.kamino.KaminoCache;
import systems.glam.services.mints.*;
import systems.glam.services.oracles.scope.FeedIndexes;
import systems.glam.services.rpc.AccountConsumer;
import systems.glam.services.rpc.AccountFetcher;
import systems.glam.services.state.GlobalConfigCache;

import java.util.Collection;

final class IntegrationServiceContextImpl extends BaseServiceContext implements IntegrationServiceContext {

  private final MintCache mintCache;
  private final StakePoolCache stakePoolCache;
  private final GlobalConfigCache globalConfigCache;
  private final IntegLookupTableCache integLookupTableCache;
  private final AccountFetcher accountFetcher;
  private final KaminoAccounts kaminoAccounts;
  private final KaminoCache kaminoCache;
  private final LoopscaleAccounts loopscaleAccounts;
  private final OrcaAccounts orcaAccounts;
  private final PhoenixAccounts phoenixAccounts;

  IntegrationServiceContextImpl(final ServiceContext serviceContext,
                                final MintCache mintCache,
                                final StakePoolCache stakePoolCache,
                                final GlobalConfigCache globalConfigCache,
                                final IntegLookupTableCache integLookupTableCache,
                                final AccountFetcher accountFetcher,
                                final KaminoAccounts kaminoAccounts,
                                final KaminoCache kaminoCache,
                                final LoopscaleAccounts loopscaleAccounts,
                                final OrcaAccounts orcaAccounts,
                                final PhoenixAccounts phoenixAccounts) {
    super(serviceContext);
    this.mintCache = mintCache;
    this.stakePoolCache = stakePoolCache;
    this.globalConfigCache = globalConfigCache;
    this.integLookupTableCache = integLookupTableCache;
    this.accountFetcher = accountFetcher;
    this.kaminoAccounts = kaminoAccounts;
    this.kaminoCache = kaminoCache;
    this.loopscaleAccounts = loopscaleAccounts;
    this.orcaAccounts = orcaAccounts;
    this.phoenixAccounts = phoenixAccounts;
  }

  @Override
  public ServiceContext serviceContext() {
    return serviceContext;
  }

  @Override
  public StakePoolContext stakePoolContextForMint(final PublicKey mintKey) {
    return stakePoolCache.get(mintKey);
  }

  @Override
  public AccountFetcher accountFetcher() {
    return accountFetcher;
  }

  @Override
  public MintContext mintContext(final PublicKey mint) {
    return mintCache.get(mint);
  }

  @Override
  public MintContext setMintContext(final MintContext mintContext) {
    return mintCache.setGet(mintContext);
  }

  @Override
  public MintContext setMintContext(final AccountInfo<byte[]> accountInfo) {
    return setMintContext(MintContext.createContext(serviceContext.solanaAccounts(), accountInfo));
  }

  @Override
  public AssetMetaContext solAssetMeta() {
    return globalConfigCache.solAssetMeta();
  }

  @Override
  public GlobalConfigCache globalConfigCache() {
    return globalConfigCache;
  }

  @Override
  public AssetMetaContext globalConfigAssetMeta(final PublicKey mint) {
    return globalConfigCache.topPriorityForMintChecked(mint);
  }

  @Override
  public void queueUnique(final Collection<PublicKey> accounts, final AccountConsumer callback) {
    accountFetcher.queueUnique(accounts, callback);
  }

  @Override
  public KaminoAccounts kaminoAccounts() {
    return kaminoAccounts;
  }

  @Override
  public KaminoCache kaminoCache() {
    return kaminoCache;
  }

  @Override
  public FeedIndexes scopeAggregateIndexes(final PublicKey mint, final PublicKey oracle, final OracleType oracleType) {
    return kaminoCache.indexes(mint, oracle, oracleType);
  }

  @Override
  public PublicKey kLendProgram() {
    return kaminoAccounts.kLendProgram();
  }

  @Override
  public PublicKey kVaultsProgram() {
    return kaminoAccounts.kVaultsProgram();
  }

  @Override
  public LoopscaleAccounts loopscaleAccounts() {
    return loopscaleAccounts;
  }

  @Override
  public PublicKey loopscaleProgram() {
    return loopscaleAccounts.loopscaleProgram();
  }

  @Override
  public OrcaAccounts orcaAccounts() {
    return orcaAccounts;
  }

  @Override
  public PublicKey orcaWhirlpoolProgram() {
    return orcaAccounts.invokedWhirlpoolProgram().publicKey();
  }

  @Override
  public PhoenixAccounts phoenixAccounts() {
    return phoenixAccounts;
  }

  @Override
  public PublicKey phoenixEternalProgram() {
    return phoenixAccounts.invokedEternalProgram().publicKey();
  }

  @Override
  public PublicKey phoenixEmberProgram() {
    return phoenixAccounts.invokedEmberProgram().publicKey();
  }

  @Override
  public IntegLookupTableCache integTableCache() {
    return integLookupTableCache;
  }
}
