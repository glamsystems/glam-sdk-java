package systems.glam.services.oracles.scope.parsers;

import software.sava.idl.clients.kamino.scope.entries.CappedMostRecentOf;
import software.sava.idl.clients.kamino.scope.entries.MostRecentOfEntry;
import software.sava.idl.clients.kamino.scope.entries.ScopeEntry;
import software.sava.idl.clients.kamino.scope.gen.types.OracleType;
import systems.comodal.jsoniter.JsonIterator;

import java.util.ArrayList;

import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

final class MostRecentOfParser extends BaseScopeEntryParser {

  private ScopeEntry[] sources;
  private int maxDivergenceBps;
  private long sourcesMaxAgeS;
  private ScopeEntry refPrice;

  MostRecentOfParser(final OracleType oracleType) {
    super(oracleType);
  }

  @Override
  public ScopeEntry createEntry() {
    if (oracleType == OracleType.CappedMostRecentOf) {
      return new CappedMostRecentOf(index, sources, maxDivergenceBps, sourcesMaxAgeS, refPrice);
    } else if (oracleType == OracleType.MostRecentOf) {
      return new MostRecentOfEntry(index, sources, maxDivergenceBps, sourcesMaxAgeS, refPrice);
    } else {
      throw new IllegalStateException("Unexpected oracle type: " + oracleType);
    }
  }

  @Override
  public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
    if (fieldEquals("sources", buf, offset, len)) {
      final var list = new ArrayList<ScopeEntry>();
      while (ji.readArray()) {
        list.add(ScopeEntryParser.parseEntry(ji));
      }
      this.sources = list.toArray(ScopeEntry[]::new);
    } else if (fieldEquals("maxDivergenceBps", buf, offset, len)) {
      this.maxDivergenceBps = ji.readInt();
    } else if (fieldEquals("sourcesMaxAgeS", buf, offset, len)) {
      this.sourcesMaxAgeS = ji.readLong();
    } else if (fieldEquals("cap", buf, offset, len) || fieldEquals("refPrice", buf, offset, len)) {
      this.refPrice = readNestedEntryOrNull(ji);
    } else {
      return super.test(buf, offset, len, ji);
    }
    return true;
  }
}
