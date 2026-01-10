package systems.glam.services.fulfillment.drfit;

import software.sava.core.accounts.meta.AccountMeta;

public record DriftMarketContext(AccountMeta readMarket, AccountMeta readOracle) {
}
