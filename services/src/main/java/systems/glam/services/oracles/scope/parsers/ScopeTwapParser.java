package systems.glam.services.oracles.scope.parsers;

import software.sava.idl.clients.kamino.scope.entries.ScopeEntry;
import software.sava.idl.clients.kamino.scope.entries.ScopeTwap;
import software.sava.idl.clients.kamino.scope.gen.types.OracleType;
import systems.comodal.jsoniter.JsonIterator;

final class ScopeTwapParser extends ScopeEntryParser {

  private ScopeEntry source;

  ScopeTwapParser(final OracleType oracleType) {
    super(oracleType);
    if (oracleType != OracleType.ScopeTwap) {
      throw new IllegalStateException("OracleType must be ScopeTwap, not: " + oracleType);
    }
  }

  @Override
  ScopeEntry createEntry() {
    return new ScopeTwap(source);
  }

  @Override
  public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
    if (JsonIterator.fieldEquals("source", buf, offset, len)) {
      this.source = readNestedEntryOrNull(ji);
    } else {
      return super.test(buf, offset, len, ji);
    }
    return true;
  }
}
