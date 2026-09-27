package systems.glam.services.mints;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import systems.glam.sdk.idl.programs.glam.config.gen.types.OracleSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

final class AssetMetaContextTests {

  private static AssetMetaContext meta(final int priority) {
    return meta(priority, OracleSource.PythPull);
  }

  private static AssetMetaContext meta(final int priority, final OracleSource oracleSource) {
    final var key = PublicKey.createPubKey(new byte[PublicKey.PUBLIC_KEY_LENGTH]);
    return new AssetMetaContextRecord(
        0, key, AccountMeta.createRead(key), 6,
        key, AccountMeta.createRead(key),
        oracleSource, 30, priority
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

  /// GLAM's oracle-selection rule (the monorepo's `oracleSelection.ts`, `RegisteredOracles` in the
  /// sdk) breaks a tie in priority by source: the Kamino reserve first, although the program
  /// declares it last.
  @Test
  void aKaminoReserveWinsATieInPriority() {
    assertTrue(meta(0, OracleSource.KaminoReserve).compareTo(meta(0, OracleSource.PythPull)) < 0);
    assertTrue(meta(0, OracleSource.PythPull).compareTo(meta(0, OracleSource.KaminoReserve)) > 0);
    assertTrue(meta(0, OracleSource.KaminoReserve).compareTo(meta(0, OracleSource.ChainlinkRWA)) < 0);
  }

  /// Without a reserve in the tie, the program's declaration order decides: PythPull is declared
  /// before ChainlinkRWA.
  @Test
  void otherwiseTheDeclarationOrderBreaksATie() {
    assertTrue(meta(0, OracleSource.PythPull).compareTo(meta(0, OracleSource.ChainlinkRWA)) < 0);
    assertTrue(meta(0, OracleSource.ChainlinkRWA).compareTo(meta(0, OracleSource.PythPull)) > 0);
    assertEquals(0, meta(0, OracleSource.ChainlinkRWA).compareTo(meta(0, OracleSource.ChainlinkRWA)));
  }

  /// The source only breaks ties: a lower priority number still wins, and a deprioritized entry
  /// still sorts after every non-negative one.
  @Test
  void theSourceNeverOutranksThePriority() {
    assertTrue(meta(0, OracleSource.PythPull).compareTo(meta(1, OracleSource.KaminoReserve)) < 0);
    assertTrue(meta(1, OracleSource.KaminoReserve).compareTo(meta(0, OracleSource.PythPull)) > 0);
    assertTrue(meta(0, OracleSource.ChainlinkRWA).compareTo(meta(-1, OracleSource.KaminoReserve)) < 0);
  }

  /// The live global configuration (2026-09-27) registers SOL and USDC with a PythPull oracle
  /// stored ahead of a KaminoReserve oracle at the same priority 0; the reserve is what prices.
  @Test
  void sortSelectsTheReserveOverAnEarlierStoredFeedOfTheSamePriority() {
    final var metas = new AssetMetaContext[]{
        meta(0, OracleSource.PythPull), meta(1, OracleSource.KaminoReserve), meta(0, OracleSource.KaminoReserve)
    };
    Arrays.sort(metas);
    assertEquals(OracleSource.KaminoReserve, metas[0].oracleSource());
    assertEquals(0, metas[0].priority());
    assertEquals(OracleSource.PythPull, metas[1].oracleSource());
    assertEquals(1, metas[2].priority());
  }
}
