package systems.glam.services.integrations;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.drift.DriftAccounts;
import software.sava.idl.clients.kamino.KaminoAccounts;
import systems.glam.services.fulfillment.drfit.DriftMarketCache;
import systems.glam.services.integrations.kamino.KaminoVaultCache;
import systems.glam.services.pricing.MintCache;
import systems.glam.services.tokens.MintContext;

final class IntegrationServiceContextImpl implements IntegrationServiceContext {

  private final MintCache mintContextMap;
  private final DriftAccounts driftAccounts;
  private final DriftMarketCache driftMarketCache;
  private final KaminoAccounts kaminoAccounts;
  private final KaminoVaultCache kaminoVaultCache;

  IntegrationServiceContextImpl(final MintCache mintContextMap,
                                final DriftAccounts driftAccounts,
                                final DriftMarketCache driftMarketCache,
                                final KaminoAccounts kaminoAccounts,
                                final KaminoVaultCache kaminoVaultCache) {
    this.mintContextMap = mintContextMap;
    this.driftAccounts = driftAccounts;
    this.driftMarketCache = driftMarketCache;
    this.kaminoAccounts = kaminoAccounts;
    this.kaminoVaultCache = kaminoVaultCache;
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
  public PublicKey kLendProgram() {
    return kaminoAccounts.kLendProgram();
  }

  @Override
  public PublicKey kVaultsProgram() {
    return kaminoAccounts.kVaultsProgram();
  }

  @Override
  public MintContext mintContext(final PublicKey mint) {
    return mintContextMap.get(mint);
  }
}
