package systems.glam.services.integrations.kamino;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ScopeAggregateIndexesTests {

  @Test
  void theNoneSentinelIsSharedAndAllNegative() {
    assertSame(KaminoCache.NO_AGGREGATE_INDEXES, ScopeAggregateIndexes.none());
    assertArrayEquals(new short[]{-1, -1, -1, -1}, ScopeAggregateIndexes.none());

    final var indexes = ScopeAggregateIndexes.createIndexesArray(3);
    assertEquals(3, indexes.length);
    for (final var row : indexes) {
      assertSame(KaminoCache.NO_AGGREGATE_INDEXES, row);
    }
    assertEquals(0, ScopeAggregateIndexes.createIndexesArray(0).length);
  }
}
