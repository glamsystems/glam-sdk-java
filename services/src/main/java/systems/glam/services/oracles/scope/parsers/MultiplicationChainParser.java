package systems.glam.services.oracles.scope.parsers;

import software.sava.idl.clients.kamino.scope.entries.MultiplicationChain;
import software.sava.idl.clients.kamino.scope.entries.ScopeEntry;
import software.sava.idl.clients.kamino.scope.gen.types.OracleType;
import systems.comodal.jsoniter.JsonIterator;

import java.util.ArrayList;

import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

final class MultiplicationChainParser extends BaseScopeEntryParser {

  private ScopeEntry[] sourceEntries;
  private long sourcesMaxAgeS;

  MultiplicationChainParser(final OracleType oracleType) {
    super(oracleType);
  }

  @Override
  public ScopeEntry createEntry() {
    return new MultiplicationChain(index, sourceEntries, sourcesMaxAgeS);
  }

  @Override
  public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
    if (fieldEquals("sourceEntries", buf, offset, len)) {
      final var list = new ArrayList<ScopeEntry>();
      while (ji.readArray()) {
        list.add(ScopeEntryParser.parseEntry(ji));
      }
      this.sourceEntries = list.toArray(ScopeEntry[]::new);
    } else if (fieldEquals("sourcesMaxAgeS", buf, offset, len)) {
      this.sourcesMaxAgeS = ji.readLong();
    } else {
      return super.test(buf, offset, len, ji);
    }
    return true;
  }
}
