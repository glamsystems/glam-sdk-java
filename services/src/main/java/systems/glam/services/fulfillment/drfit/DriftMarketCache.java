package systems.glam.services.fulfillment.drfit;

public interface DriftMarketCache {


  DriftMarketContext spotMarket(final int marketIndex);

  DriftMarketContext perpMarket(final int marketIndex);
}
