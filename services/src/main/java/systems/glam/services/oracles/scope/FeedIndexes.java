package systems.glam.services.oracles.scope;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;

import java.math.BigInteger;

public record FeedIndexes(AccountMeta readPriceFeed,
                          AccountMeta readOracleMappings,
                          short[] indexes,
                          BigInteger liquidity) implements Comparable<FeedIndexes> {

  public PublicKey priceFeed() {
    return readPriceFeed.publicKey();
  }

  @Override
  public int compareTo(final FeedIndexes o) {
    return o.liquidity().compareTo(liquidity);
  }
}
