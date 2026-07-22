package systems.glam.services.mints;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import systems.glam.sdk.idl.programs.glam.config.gen.types.OracleSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

final class AssetMetaContextTests {

  private static AssetMetaContext meta(final int priority) {
    final var key = PublicKey.createPubKey(new byte[PublicKey.PUBLIC_KEY_LENGTH]);
    return new AssetMetaContextRecord(
        0, key, AccountMeta.createRead(key), 6,
        key, AccountMeta.createRead(key),
        OracleSource.PythPull, 30, priority
    );
  }

  @Test
  void nonNegativePrioritiesSortAscending() {
    assertTrue(meta(0).compareTo(meta(1)) < 0);
    assertTrue(meta(2).compareTo(meta(1)) > 0);
    assertEquals(0, meta(1).compareTo(meta(1)));
  }

  @Test
  void negativePrioritiesSortAfterEveryNonNegative() {
    // a negative priority is a deprioritized entry: it must never win
    assertTrue(meta(-1).compareTo(meta(0)) > 0);
    assertTrue(meta(-1).compareTo(meta(Integer.MAX_VALUE)) > 0);
    assertTrue(meta(0).compareTo(meta(-1)) < 0);
    assertTrue(meta(Integer.MAX_VALUE).compareTo(meta(-1)) < 0);
  }

  @Test
  void negativePrioritiesSortByMagnitude() {
    assertTrue(meta(-1).compareTo(meta(-2)) < 0);
    assertTrue(meta(-3).compareTo(meta(-2)) > 0);
    assertEquals(0, meta(-2).compareTo(meta(-2)));
  }

  @Test
  void sortPlacesTheTopPriorityFirst() {
    final var metas = new AssetMetaContext[]{meta(-1), meta(2), meta(0), meta(-3), meta(1)};
    Arrays.sort(metas);
    assertArrayEquals(
        new int[]{0, 1, 2, -1, -3},
        Arrays.stream(metas).mapToInt(AssetMetaContext::priority).toArray()
    );
  }
}
