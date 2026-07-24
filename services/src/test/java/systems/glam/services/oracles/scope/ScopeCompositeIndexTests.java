package systems.glam.services.oracles.scope;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.kamino.scope.entries.CappedFloored;
import software.sava.idl.clients.kamino.scope.entries.CappedMostRecentOf;
import software.sava.idl.clients.kamino.scope.entries.Conditional;
import software.sava.idl.clients.kamino.scope.entries.MostRecentOfEntry;
import software.sava.idl.clients.kamino.scope.entries.MultiplicationChain;
import software.sava.idl.clients.kamino.scope.entries.ScopeEntry;
import software.sava.idl.clients.kamino.scope.entries.SwitchboardOnDemand;
import software.sava.idl.clients.kamino.scope.gen.types.Condition;
import software.sava.idl.clients.kamino.scope.gen.types.OracleMappings;
import software.sava.idl.clients.kamino.scope.gen.types.OracleType;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Direct coverage of the composite-chain recursion over hand-built entry
/// graphs — the real mainnet SOL chain (`KaminoCacheTests`) only exercises a
/// `MostRecentOfEntry` with two direct sources and a null refPrice; these pin
/// every other composite shape without synthesizing OracleMappings bytes.
final class ScopeCompositeIndexTests {

  private static final OracleType TYPE = OracleType.SwitchboardOnDemand;
  private static final PublicKey TARGET = PublicKey.createPubKey(pk(1));
  private static final PublicKey OTHER = PublicKey.createPubKey(pk(2));

  private static byte[] pk(final int id) {
    final byte[] b = new byte[PublicKey.PUBLIC_KEY_LENGTH];
    b[0] = (byte) id;
    return b;
  }

  /// A leaf oracle at `index` reading `oracle` of the target type.
  private static ScopeEntry oracle(final int index, final PublicKey oracle) {
    return new SwitchboardOnDemand(index, oracle, Set.of());
  }

  private static List<Integer> matches(final ScopeEntry entry) {
    final var out = new ArrayList<Integer>();
    final var visited = new boolean[OracleMappings.PRICE_INFO_ACCOUNTS_LEN];
    ScopeFeedContext.collectMatchingIndices(entry, TARGET, TYPE, out::add, visited);
    return out;
  }

  @Test
  void aDirectOracleMatchesByOracleAndType() {
    assertEquals(List.of(7), matches(oracle(7, TARGET)));
    // wrong oracle, and (below) wrong type: no match
    assertEquals(List.of(), matches(oracle(7, OTHER)));
    assertEquals(List.of(), matches(new software.sava.idl.clients.kamino.scope.entries.KToken(7, TARGET, Set.of())));
  }

  @Test
  void mostRecentOfMatchesSourcesAndRefPrice() {
    final var mre = new MostRecentOfEntry(
        3,
        new ScopeEntry[]{oracle(1, TARGET), oracle(2, OTHER)},
        100, 60L,
        oracle(5, TARGET), // refPrice also reads the target oracle
        OptionalInt.empty()
    );
    // the matching source (index 1) and the matching refPrice (index 5); the
    // non-matching source (index 2) is skipped
    assertEquals(List.of(1, 5), matches(mre));
  }

  @Test
  void cappedMostRecentOfMatchesSourcesAndCap() {
    final var capped = new CappedMostRecentOf(
        3,
        new ScopeEntry[]{oracle(1, TARGET)},
        100, 60L,
        oracle(4, TARGET) // cap bound reads the target too
    );
    assertEquals(List.of(1, 4), matches(capped));
  }

  @Test
  void cappedFlooredMatchesAllThreeReferences() {
    // source (1), cap (4) and floor (6) all read the target oracle, so all
    // three recursions must fire (a dropped floor recursion loses index 6)
    final var cf = new CappedFloored(3, oracle(1, TARGET), oracle(4, TARGET), oracle(6, TARGET));
    assertEquals(List.of(1, 4, 6), matches(cf));
    // and a non-matching leg contributes nothing
    assertEquals(List.of(1), matches(new CappedFloored(3, oracle(1, TARGET), oracle(4, OTHER), oracle(6, OTHER))));
  }

  @Test
  void conditionalMatchesEverySource() {
    final var cond = new Conditional(3, Condition.NonZero,
        50, new ScopeEntry[]{oracle(1, TARGET), oracle(2, OTHER), oracle(8, TARGET)});
    assertEquals(List.of(1, 8), matches(cond));
  }

  @Test
  void multiplicationChainMatchesEveryFactor() {
    final var mul = new MultiplicationChain(3,
        new ScopeEntry[]{oracle(1, TARGET), oracle(9, TARGET)}, 60L);
    assertEquals(List.of(1, 9), matches(mul));
  }

  @Test
  void nestedCompositesRecurse() {
    // MostRecentOf -> Conditional -> matching oracle
    final var nested = new MostRecentOfEntry(
        3,
        new ScopeEntry[]{
            new Conditional(10, Condition.NonZero, 0, new ScopeEntry[]{oracle(1, TARGET)})
        },
        100, 60L, null, OptionalInt.empty()
    );
    assertEquals(List.of(1), matches(nested));
  }

  @Test
  void oneSlotReachedByTwoPathsIsCountedOnce() {
    // the shared slot is at index 0 so the visited guard's lower bound is
    // exercised: without `index >= 0` marking slot 0 visited, it matches twice
    final var shared = oracle(0, TARGET);
    // the same entry instance is both a source and the refPrice
    final var mre = new MostRecentOfEntry(
        3, new ScopeEntry[]{shared}, 100, 60L, shared, OptionalInt.empty());
    assertEquals(List.of(0), matches(mre));
  }

  @Test
  void nullChildReferencesAreSkipped() {
    // a MostRecentOf with a null refPrice (SOL's real shape) does not NPE
    final var mre = new MostRecentOfEntry(
        3, new ScopeEntry[]{oracle(1, TARGET)}, 100, 60L, null, OptionalInt.empty());
    assertEquals(List.of(1), matches(mre));
  }
}
