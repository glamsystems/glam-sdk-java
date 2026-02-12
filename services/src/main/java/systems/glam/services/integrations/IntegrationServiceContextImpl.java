package systems.glam.services.integrations;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.drift.DriftAccounts;
import software.sava.idl.clients.kamino.KaminoAccounts;
import software.sava.idl.clients.kamino.scope.gen.types.OracleType;
import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.services.ServiceContext;
import systems.glam.services.execution.BaseServiceContext;
import systems.glam.services.integrations.drift.DriftMarketCache;
import systems.glam.services.integrations.kamino.KaminoVaultCache;
import systems.glam.services.mints.*;
import systems.glam.services.oracles.scope.FeedIndexes;
import systems.glam.services.oracles.scope.KaminoCache;
import systems.glam.services.pricing.accounting.VaultAumRecord;
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
  private final DriftAccounts driftAccounts;
  private final DriftMarketCache driftMarketCache;
  private final KaminoAccounts kaminoAccounts;
  private final KaminoCache kaminoCache;
  private final KaminoVaultCache kaminoVaultCache;

  IntegrationServiceContextImpl(final ServiceContext serviceContext,
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
    super(serviceContext);
    this.mintCache = mintCache;
    this.stakePoolCache = stakePoolCache;
    this.globalConfigCache = globalConfigCache;
    this.integLookupTableCache = integLookupTableCache;
    this.accountFetcher = accountFetcher;
    this.driftAccounts = driftAccounts;
    this.driftMarketCache = driftMarketCache;
    this.kaminoAccounts = kaminoAccounts;
    this.kaminoCache = kaminoCache;
    this.kaminoVaultCache = kaminoVaultCache;
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
  public AssetMetaContext watchForMint(final PublicKey mint, final PublicKey stateAccount) {
    return globalConfigCache.watchForMint(mint, stateAccount);
  }

  @Override
  public void queue(final Collection<PublicKey> accounts, final AccountConsumer callback) {
    accountFetcher.queue(accounts, callback);
  }

  @Override
  public DriftMarketCache driftMarketCache() {
    return driftMarketCache;
  }

  @Override
  public DriftAccounts driftAccounts() {
    return driftAccounts;
  }

  @Override
  public PublicKey driftProgram() {
    return driftAccounts.driftProgram();
  }

  @Override
  public PublicKey driftVaultsProgram() {
    return driftAccounts.driftVaultsProgram();
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
  public void persistAumRecord(final VaultAumRecord aumRecord) throws InterruptedException {
    final var datasource = serviceContext.primaryDatasource();
    final int numInserted = aumRecord.insertRecord(datasource);
    System.out.println(numInserted);
  }

  @Override
  public KaminoVaultCache kaminoVaultCache() {
    return kaminoVaultCache;
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
  public IntegLookupTableCache integTableCache() {
    return integLookupTableCache;
  }
}
