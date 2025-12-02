package systems.glam.services.oracles.scope.parsers;

import org.junit.jupiter.api.Test;
import software.sava.idl.clients.kamino.scope.entries.ScopeEntry;
import systems.comodal.jsoniter.FieldBufferPredicate;
import systems.comodal.jsoniter.JsonIterator;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class KaminoScopeSerDeTests {

  @Test
  void testParseMarketReserveOracles() throws Exception {
    try (final InputStream in = KaminoScopeSerDeTests.class.getClassLoader()
        .getResourceAsStream("scope/market_reserve_oracles.json")) {
      assertNotNull(in, "Resource scope/market_reserve_oracles.json not found on classpath");

      final var parsed = new ArrayList<ScopeEntry>();
      final var ji = JsonIterator.parse(in.readAllBytes());
      // Top-level array
      while (ji.readArray()) {
        // Each element is an object: { market, reserves }
        ji.testObject(new MarketParser(parsed));
      }

      assertFalse(parsed.isEmpty(), "Expected to parse at least one ScopeEntry from the JSON");
      // Basic smoke-check: no null entries
      assertTrue(parsed.stream().allMatch(e -> e != null));
    }
  }

  private record MarketParser(List<ScopeEntry> sink) implements FieldBufferPredicate {

    @Override
      public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
        if (JsonIterator.fieldEquals("reserves", buf, offset, len)) {
          while (ji.readArray()) {
            ji.testObject(new ReserveParser(sink));
          }
        } else {
          ji.skip();
        }
        return true;
      }
    }

  private record ReserveParser(List<ScopeEntry> sink) implements FieldBufferPredicate {

    @Override
      public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
        if (JsonIterator.fieldEquals("priceChain", buf, offset, len)
            || JsonIterator.fieldEquals("twapChain", buf, offset, len)) {
          while (ji.readArray()) {
            final var entry = ScopeEntryParser.parseEntry(ji);
            assertNotNull(entry, "Parsed ScopeEntry must not be null");
            sink.add(entry);
          }
        } else {
          ji.skip();
        }
        return true;
      }
    }
}
