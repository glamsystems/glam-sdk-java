package systems.glam.services.oracles.scope.parsers;

import software.sava.idl.clients.kamino.scope.entries.ChainlinkRWA;
import software.sava.idl.clients.kamino.scope.entries.ChainlinkX;
import software.sava.idl.clients.kamino.scope.entries.ScopeEntry;
import software.sava.idl.clients.kamino.scope.gen.types.MarketStatusBehavior;
import software.sava.idl.clients.kamino.scope.gen.types.OracleType;
import systems.comodal.jsoniter.JsonIterator;

final class ChainlinkStatusEntryParser extends OracleEntryParser {

  private MarketStatusBehavior marketStatusBehavior;

  ChainlinkStatusEntryParser(final OracleType oracleType) {
    super(oracleType);
  }

  @Override
  ScopeEntry createEntry() {
    return switch (oracleType) {
      case ChainlinkRWA -> new ChainlinkRWA(oracle, marketStatusBehavior, emaTypes());
      case ChainlinkX -> new ChainlinkX(oracle, marketStatusBehavior, emaTypes());
      default -> throw new IllegalStateException("Unexpected chainlink status oracle type: " + oracleType);
    };
  }

  @Override
  public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
    if (JsonIterator.fieldEquals("marketStatusBehavior", buf, offset, len)) {
      final var behavior = ji.readString();
      this.marketStatusBehavior = behavior == null || behavior.isBlank() ? null : MarketStatusBehavior.valueOf(behavior);
    } else {
      return super.test(buf, offset, len, ji);
    }
    return true;
  }
}
