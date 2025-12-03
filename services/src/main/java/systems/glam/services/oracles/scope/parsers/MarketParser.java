package systems.glam.services.oracles.scope.parsers;

import software.sava.core.accounts.PublicKey;
import systems.comodal.jsoniter.FieldBufferPredicate;
import systems.comodal.jsoniter.JsonIterator;
import systems.glam.services.oracles.scope.ReserveContext;

import java.util.Map;
import java.util.Objects;

public final class MarketParser implements FieldBufferPredicate {

  private final Map<PublicKey, ReserveContext> reserveContexts;
  private PublicKey market;

  public static void parseReserves(final byte[] data, final Map<PublicKey, ReserveContext> reserveContexts) {
    final var ji = JsonIterator.parse(data);
    while (ji.readArray()) {
      final var parser = new MarketParser(reserveContexts);
      ji.testObject(parser);
    }
  }

  private MarketParser(final Map<PublicKey, ReserveContext> reserveContexts) {
    this.reserveContexts = reserveContexts;
  }

  @Override
  public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
    if (JsonIterator.fieldEquals("market", buf, offset, len)) {
      this.market = PublicKey.fromBase58Encoded(ji.readString());
    } else if (JsonIterator.fieldEquals("reserves", buf, offset, len)) {
      Objects.requireNonNull(market);
      while (ji.readArray()) {
        final var reserveContext = ReserveContext.parse(ji, market);
        reserveContexts.put(reserveContext.pubKey(), reserveContext);
      }
      this.market = null;
    } else {
      ji.skip();
    }
    return true;
  }
}
