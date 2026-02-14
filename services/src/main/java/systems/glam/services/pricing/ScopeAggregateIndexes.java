package systems.glam.services.pricing;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.kamino.scope.gen.types.OracleType;
import systems.glam.services.oracles.scope.FeedIndexes;
import systems.glam.services.integrations.kamino.KaminoCache;

import java.util.Arrays;

public interface ScopeAggregateIndexes {

  short[] NO_AGGREGATE_INDEXES = new short[]{-1, -1, -1, -1};

  static short[][] createIndexesArray(int numAssets) {
    final var aggIndexes = new short[numAssets][4];
    Arrays.fill(aggIndexes, ScopeAggregateIndexes.none());
    return aggIndexes;
  }

  static short[] none() {
    return KaminoCache.NO_AGGREGATE_INDEXES;
  }

  FeedIndexes indexes(final PublicKey mint, final PublicKey oracle, final OracleType oracleType);
}
