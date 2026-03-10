package systems.glam.services.integrations.drift;

import software.sava.core.accounts.meta.AccountMeta;

public sealed interface DriftMarketContext permits DriftPerpMarketContext, DriftSpotMarketContext {

  int marketIndex();

  int poolId();

  AccountMeta readMarket();

  AccountMeta readOracle();
}
