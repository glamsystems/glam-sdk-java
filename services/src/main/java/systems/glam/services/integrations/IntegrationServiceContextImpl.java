package systems.glam.services.integrations;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.drift.DriftAccounts;
import software.sava.idl.clients.kamino.KaminoAccounts;
import systems.glam.sdk.idl.programs.glam.config.gen.types.AssetMeta;
import systems.glam.services.fulfillment.drfit.DriftMarketCache;
import systems.glam.services.integrations.kamino.KaminoVaultCache;
import systems.glam.services.pricing.AccountConsumer;
import systems.glam.services.pricing.AccountFetcher;
import systems.glam.services.pricing.IntegTableCache;
import systems.glam.services.pricing.MintCache;
import systems.glam.services.tokens.MintContext;

import java.util.Collection;
import java.util.Set;

final class IntegrationServiceContextImpl implements IntegrationServiceContext {

  private final PublicKey solUSDOracleKey;
  private final PublicKey baseAssetUSDOracleKey;
  private final MintCache mintCache;
  private final IntegTableCache integTableCache;
  private final AccountFetcher accountFetcher;
  private final DriftAccounts driftAccounts;
  private final DriftMarketCache driftMarketCache;
  private final KaminoAccounts kaminoAccounts;
  private final KaminoVaultCache kaminoVaultCache;

  IntegrationServiceContextImpl(final PublicKey solUSDOracleKey,
                                final PublicKey baseAssetUSDOracleKey,
                                final MintCache mintCache,
                                final IntegTableCache integTableCache,
                                final AccountFetcher accountFetcher,
                                final DriftAccounts driftAccounts,
                                final DriftMarketCache driftMarketCache,
                                final KaminoAccounts kaminoAccounts,
                                final KaminoVaultCache kaminoVaultCache) {
    this.solUSDOracleKey = solUSDOracleKey;
    this.baseAssetUSDOracleKey = baseAssetUSDOracleKey;
    this.mintCache = mintCache;
    this.integTableCache = integTableCache;
    this.accountFetcher = accountFetcher;
    this.driftAccounts = driftAccounts;
    this.driftMarketCache = driftMarketCache;
    this.kaminoAccounts = kaminoAccounts;
    this.kaminoVaultCache = kaminoVaultCache;
  }

  public PublicKey solUSDOracleKey() {
    return solUSDOracleKey;
  }

  public PublicKey baseAssetUSDOracleKey() {
    return baseAssetUSDOracleKey;
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
  public KaminoVaultCache kaminoVaultCache() {
    return kaminoVaultCache;
  }

  @Override
  public KaminoAccounts kaminoAccounts() {
    return kaminoAccounts;
  }

  @Override
  public Set<PublicKey> kaminoTableKeys() {
    return Set.of();
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
  public MintContext mintContext(final PublicKey mint) {
    return mintCache.get(mint);
  }

  @Override
  public MintContext setMintContext(final MintContext mintContext) {
    return mintCache.setGet(mintContext);
  }

  @Override
  public AssetMeta globalConfigAssetMeta(final PublicKey mint) {
    return null;
  }

  @Override
  public IntegTableCache integTableCache() {
    return integTableCache;
  }
}
