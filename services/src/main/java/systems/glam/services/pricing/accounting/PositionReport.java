package systems.glam.services.pricing.accounting;

import software.sava.core.accounts.PublicKey;

import java.math.BigDecimal;

public interface PositionReport extends AggregatePositionReport {

  static PositionReport zero(final PublicKey mint) {
    var leaf = PositionReportLeaf.ZERO_POSITIONS.get(mint);
    if (leaf == null) {
      leaf = new PositionReportLeaf(mint, BigDecimal.ZERO, BigDecimal.ZERO);
      final var previous = PositionReportLeaf.ZERO_POSITIONS.putIfAbsent(mint, leaf);
      return previous != null ? previous : leaf;
    } else {
      return leaf;
    }
  }

  static PositionReport create(final PublicKey mint, final BigDecimal amount, final BigDecimal value) {
    return new PositionReportLeaf(mint, amount.stripTrailingZeros(), value.stripTrailingZeros());
  }

  PublicKey mint();

  BigDecimal amount();
}
