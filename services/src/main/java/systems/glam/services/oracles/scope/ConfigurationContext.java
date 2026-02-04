package systems.glam.services.oracles.scope;

import software.sava.core.accounts.PublicKey;

public record ConfigurationContext(PublicKey configurationKey,
                                   PublicKey oracleMappings,
                                   PublicKey priceFeed) {

}
