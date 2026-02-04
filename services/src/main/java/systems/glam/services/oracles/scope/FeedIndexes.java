package systems.glam.services.oracles.scope;

import software.sava.core.accounts.meta.AccountMeta;

import java.math.BigInteger;

public record FeedIndexes(AccountMeta readPriceFeed,
                          short[] indexes,
                          BigInteger liquidity) implements Comparable<FeedIndexes> {

  @Override
  public int compareTo(final FeedIndexes o) {
    return o.liquidity().compareTo(liquidity);
  }
}
