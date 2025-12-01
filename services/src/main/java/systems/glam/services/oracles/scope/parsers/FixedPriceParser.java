package systems.glam.services.oracles.scope.parsers;

import software.sava.idl.clients.kamino.scope.entries.FixedPrice;
import software.sava.idl.clients.kamino.scope.entries.ScopeEntry;
import software.sava.idl.clients.kamino.scope.gen.types.OracleType;
import systems.comodal.jsoniter.JsonIterator;

final class FixedPriceParser extends ScopeEntryParser {

  private long value;
  private int exp;

  FixedPriceParser(final OracleType oracleType) {
    super(oracleType);
    if (oracleType != OracleType.FixedPrice) {
      throw new IllegalStateException("OracleType must be FixedPrice, not: " + oracleType);
    }
  }

  @Override
  ScopeEntry createEntry() {
    return new FixedPrice(value, exp);
  }

  @Override
  public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
    if (JsonIterator.fieldEquals("value", buf, offset, len)) {
      this.value = ji.readLong();
    } else if (JsonIterator.fieldEquals("exp", buf, offset, len)) {
      this.exp = ji.readInt();
    } else if (JsonIterator.fieldEquals("decimal", buf, offset, len)) {
      ji.skip();
    } else {
      return super.test(buf, offset, len, ji);
    }
    return true;
  }
}
