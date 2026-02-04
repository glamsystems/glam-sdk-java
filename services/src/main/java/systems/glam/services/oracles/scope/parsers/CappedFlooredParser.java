package systems.glam.services.oracles.scope.parsers;

import software.sava.idl.clients.kamino.scope.entries.CappedFloored;
import software.sava.idl.clients.kamino.scope.entries.ScopeEntry;
import software.sava.idl.clients.kamino.scope.gen.types.OracleType;
import systems.comodal.jsoniter.JsonIterator;

final class CappedFlooredParser extends BaseScopeEntryParser {

  private ScopeEntry source;
  private ScopeEntry cap;
  private ScopeEntry floor;

  CappedFlooredParser(final OracleType oracleType) {
    super(oracleType);
    if (oracleType != OracleType.CappedFloored) {
      throw new IllegalStateException("OracleType must be CappedFloored, not: " + oracleType);
    }
  }

  @Override
  public ScopeEntry createEntry() {
    return new CappedFloored(index, source, cap, floor);
  }

  @Override
  public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
    if (JsonIterator.fieldEquals("source", buf, offset, len)) {
      this.source = readNestedEntryOrNull(ji);
    } else if (JsonIterator.fieldEquals("cap", buf, offset, len)) {
      this.cap = readNestedEntryOrNull(ji);
    } else if (JsonIterator.fieldEquals("floor", buf, offset, len)) {
      this.floor = readNestedEntryOrNull(ji);
    } else {
      return super.test(buf, offset, len, ji);
    }
    return true;
  }
}
