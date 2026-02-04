package systems.glam.services.oracles.scope.parsers;

import software.sava.idl.clients.kamino.scope.entries.Chainlink;
import software.sava.idl.clients.kamino.scope.entries.ScopeEntry;
import software.sava.idl.clients.kamino.scope.gen.types.OracleType;
import systems.comodal.jsoniter.JsonIterator;

class ChainlinkParser extends ReferencesEntryParser {

  private int confidenceFactor;

  ChainlinkParser(final OracleType oracleType) {
    super(oracleType);
    if (oracleType != OracleType.Chainlink) {
      throw new IllegalStateException("OracleType must be Chainlink, not: " + oracleType);
    }
  }

  @Override
  public ScopeEntry createEntry() {
    return new Chainlink(index, oracle, confidenceFactor, emaTypes(), refPrice);
  }

  @Override
  public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
    if (JsonIterator.fieldEquals("confidenceFactor", buf, offset, len)) {
      this.confidenceFactor = ji.readInt();
    } else {
      return super.test(buf, offset, len, ji);
    }
    return true;
  }
}
