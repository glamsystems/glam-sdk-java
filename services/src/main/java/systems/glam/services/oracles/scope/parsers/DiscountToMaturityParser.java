package systems.glam.services.oracles.scope.parsers;

import software.sava.idl.clients.kamino.scope.entries.DiscountToMaturity;
import software.sava.idl.clients.kamino.scope.entries.ScopeEntry;
import software.sava.idl.clients.kamino.scope.gen.types.OracleType;
import systems.comodal.jsoniter.JsonIterator;

final class DiscountToMaturityParser extends BaseScopeEntryParser {

  private int discountPerYearBps;
  private long maturityTimestamp;

  DiscountToMaturityParser(final OracleType oracleType) {
    super(oracleType);
    if (oracleType != OracleType.DiscountToMaturity) {
      throw new IllegalStateException("OracleType must be DiscountToMaturity, not: " + oracleType);
    }
  }

  @Override
  public ScopeEntry createEntry() {
    return new DiscountToMaturity(index, discountPerYearBps, maturityTimestamp);
  }

  @Override
  public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
    if (JsonIterator.fieldEquals("discountPerYearBps", buf, offset, len)) {
      this.discountPerYearBps = ji.readInt();
    } else if (JsonIterator.fieldEquals("maturityTimestamp", buf, offset, len)) {
      this.maturityTimestamp = ji.readLong();
    } else {
      return super.test(buf, offset, len, ji);
    }
    return true;
  }
}
