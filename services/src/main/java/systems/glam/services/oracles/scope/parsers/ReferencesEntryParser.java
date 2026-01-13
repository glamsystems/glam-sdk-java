package systems.glam.services.oracles.scope.parsers;

import software.sava.idl.clients.kamino.scope.entries.PythPull;
import software.sava.idl.clients.kamino.scope.entries.ScopeEntry;
import software.sava.idl.clients.kamino.scope.entries.Securitize;
import software.sava.idl.clients.kamino.scope.gen.types.OracleType;
import systems.comodal.jsoniter.JsonIterator;

class ReferencesEntryParser extends OracleEntryParser {

  protected ScopeEntry refPrice;

  ReferencesEntryParser(final OracleType oracleType) {
    super(oracleType);
  }

  @Override
  ScopeEntry createEntry() {
    return switch (oracleType) {
      case PythPull -> new PythPull(oracle, emaTypes(), refPrice);
      case Securitize -> new Securitize(oracle, emaTypes(), refPrice);
      default -> throw new IllegalStateException("Unexpected references oracle type: " + oracleType);
    };
  }

  @Override
  public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
    if (JsonIterator.fieldEquals("refPrice", buf, offset, len)) {
      this.refPrice = readNestedEntryOrNull(ji);
    } else {
      return super.test(buf, offset, len, ji);
    }
    return true;
  }
}
