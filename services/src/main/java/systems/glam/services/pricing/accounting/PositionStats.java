package systems.glam.services.pricing.accounting;

import java.math.BigDecimal;
import java.math.MathContext;

public interface PositionStats {

  PositionStats deductShares(final BigDecimal shares);

  PositionStats withShares(final BigDecimal shares);

  PositionStats withShares(final BigDecimal shares, final BigDecimal baseAssetPrice);

  BigDecimal valuePriceAsset();

  BigDecimal shares();

  default BigDecimal price(final MathContext mathContext) {
    final var shares = shares();
    final var valuePriceAsset = valuePriceAsset();
    return shares.signum() == 0 ? null : valuePriceAsset.divide(shares, mathContext);
  }
}
