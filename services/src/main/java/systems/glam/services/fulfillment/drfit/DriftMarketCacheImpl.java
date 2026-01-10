package systems.glam.services.fulfillment.drfit;

final class DriftMarketCacheImpl implements DriftMarketCache {

  private final DriftMarketContext[] contexts;

  DriftMarketCacheImpl(final int size) {
    this.contexts = new DriftMarketContext[size];
  }

  @Override
  public DriftMarketContext spotMarket(final int marketIndex) {
    return null;
  }

  @Override
  public DriftMarketContext perpMarket(final int marketIndex) {
    return null;
  }
}
