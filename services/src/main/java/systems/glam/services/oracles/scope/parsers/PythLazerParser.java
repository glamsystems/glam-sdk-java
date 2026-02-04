package systems.glam.services.oracles.scope.parsers;

import software.sava.idl.clients.kamino.scope.entries.PythLazer;
import software.sava.idl.clients.kamino.scope.entries.ScopeEntry;
import software.sava.idl.clients.kamino.scope.gen.types.OracleType;
import systems.comodal.jsoniter.JsonIterator;

final class PythLazerParser extends ReferencesEntryParser {

  private int confidenceFactor;
  private Integer feedId;
  private Integer exponent;

  PythLazerParser(final OracleType oracleType) {
    super(oracleType);
    if (oracleType != OracleType.PythLazer) {
      throw new IllegalStateException("OracleType must be PythLazer, not: " + oracleType);
    }
  }

  @Override
  public ScopeEntry createEntry() {
    return new PythLazer(index, oracle, feedId, exponent, confidenceFactor, emaTypes(), refPrice);
  }

  @Override
  public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
    if (JsonIterator.fieldEquals("confidenceFactor", buf, offset, len)) {
      this.confidenceFactor = ji.readInt();
    } else if (JsonIterator.fieldEquals("feedId", buf, offset, len)) {
      this.feedId = ji.readInt();
    } else if (JsonIterator.fieldEquals("exponent", buf, offset, len)) {
      this.exponent = ji.readInt();
    } else {
      return super.test(buf, offset, len, ji);
    }
    return true;
  }
}
