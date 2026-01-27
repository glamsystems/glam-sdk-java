package systems.glam.services.integrations.drift;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.drift.DriftAccounts;
import systems.glam.services.tests.ResourceUtil;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReferenceArray;

import static org.junit.jupiter.api.Assertions.*;

final class DriftMarketCacheTests {

  private static final String SPOT_MARKETS_RESOURCE_PATH = "accounts/drift/spot_markets";
  private static final String PERP_MARKETS_RESOURCE_PATH = "accounts/drift/perp_markets";

  private static DriftMarketContext[] SPOT_MARKETS;
  private static DriftMarketContext[] PERP_MARKETS;

  @BeforeAll
  static void loadMarkets() throws IOException, URISyntaxException {
    SPOT_MARKETS = loadMarketsFromResource(SPOT_MARKETS_RESOURCE_PATH, DriftMarketContext::createSpotContext);
    PERP_MARKETS = loadMarketsFromResource(PERP_MARKETS_RESOURCE_PATH, DriftMarketContext::createPerpContext);
  }

  private static DriftMarketContext[] loadMarketsFromResource(final String resourcePath,
                                                              final java.util.function.Function<byte[], DriftMarketContext> contextFactory) throws IOException, URISyntaxException {
    final var classLoader = DriftMarketCacheTests.class.getClassLoader();
    final var resourceUrl = classLoader.getResource(resourcePath);
    if (resourceUrl == null) {
      throw new IllegalStateException("Resource path not found: " + resourcePath);
    }
    final var resourceDir = Path.of(resourceUrl.toURI());

    try (final var files = Files.list(resourceDir)) {
      final var marketFiles = files
          .filter(p -> p.getFileName().toString().endsWith(".dat.zip"))
          .sorted()
          .toList();

      final var markets = new DriftMarketContext[marketFiles.size()];
      for (int i = 0; i < marketFiles.size(); i++) {
        final var fileName = marketFiles.get(i).getFileName().toString();
        final var fullResourcePath = resourcePath + "/" + fileName;
        final byte[] data = ResourceUtil.readResource(fullResourcePath);
        markets[i] = contextFactory.apply(data);
      }
      return markets;
    }
  }

  @Test
  void testSpotMarkets() {
    for (final var market : SPOT_MARKETS) {
      assertTrue(market.marketIndex() >= 0);
      assertTrue(market.poolId() >= 0);
    }
  }

  @Test
  void testPerpMarkets() {
    for (final var market : PERP_MARKETS) {
      assertTrue(market.marketIndex() >= 0);
      assertEquals(0, market.poolId());
    }
  }

  private static int maxMarketIndex(final DriftMarketContext[] marketContexts) {
    int marketIndex = 0;
    for (final var market : marketContexts) {
      marketIndex = Math.max(marketIndex, market.marketIndex());
    }
    return marketIndex;
  }

  @Test
  void testMarketCache() {
    final int maxSpotIndex = maxMarketIndex(SPOT_MARKETS);
    final var spotMarketsArray = new AtomicReferenceArray<DriftMarketContext>(maxSpotIndex + 2);
    for (final var market : SPOT_MARKETS) {
      spotMarketsArray.set(market.marketIndex(), market);
    }
    final int maxPerpIndex = maxMarketIndex(PERP_MARKETS);
    final var perpMarketsArray = new AtomicReferenceArray<DriftMarketContext>(maxPerpIndex + 2);
    for (final var market : PERP_MARKETS) {
      perpMarketsArray.set(market.marketIndex(), market);
    }
    final var driftAccounts = DriftAccounts.MAIN_NET;

    final var cache = new DriftMarketCacheImpl(
        null, null,
        spotMarketsArray, perpMarketsArray, driftAccounts,
        null
    );

    var spotMarketContext = cache.spotMarket(24);
    assertNotNull(spotMarketContext);
    assertEquals(0, spotMarketContext.poolId());
    assertEquals(24, spotMarketContext.marketIndex());
    assertEquals(PublicKey.fromBase58Encoded("13EdTpaMp1MeHSXqFVA3hFfEiLZWpatu4gEf5xCtRufN"), spotMarketContext.market());
    assertEquals(PublicKey.fromBase58Encoded("CX7JCXtUTiC43ZA4uzoH7iQBD15jtVwdBNCnjKHt1BrQ"), spotMarketContext.oracle());

    // Validate spot market index 47 (USDC-4)
    var spotMarketContext47 = cache.spotMarket(47);
    assertNotNull(spotMarketContext47);
    assertEquals(4, spotMarketContext47.poolId());
    assertEquals(47, spotMarketContext47.marketIndex());
    assertEquals(PublicKey.fromBase58Encoded("xas1HioZboo2nYuYa7yFVY1aVg3ypRRMb1A5v6hUXeE"), spotMarketContext47.market());
    assertEquals(PublicKey.fromBase58Encoded("9VCioxmni2gDLv11qufWzT3RDERhQE4iY5Gf7NTfYyAV"), spotMarketContext47.oracle());

    // Validate spot market index 0 (USDC)
    var spotMarketContext0 = cache.spotMarket(0);
    assertNotNull(spotMarketContext0);
    assertEquals(0, spotMarketContext0.poolId());
    assertEquals(0, spotMarketContext0.marketIndex());
    assertEquals(PublicKey.fromBase58Encoded("6gMq3mRCKf8aP3ttTyYhuijVZ2LGi14oDsBbkgubfLB3"), spotMarketContext0.market());
    assertEquals(PublicKey.fromBase58Encoded("9VCioxmni2gDLv11qufWzT3RDERhQE4iY5Gf7NTfYyAV"), spotMarketContext0.oracle());

    // Validate spot market index 62 (USD1)
    var spotMarketContext62 = cache.spotMarket(62);
    assertNotNull(spotMarketContext62);
    assertEquals(0, spotMarketContext62.poolId());
    assertEquals(62, spotMarketContext62.marketIndex());
    assertEquals(PublicKey.fromBase58Encoded("GHZ58w1YEh79NHwEvfKwXH7trQXGwd5a48HKbWc5xzNL"), spotMarketContext62.market());
    assertEquals(PublicKey.fromBase58Encoded("Hk34ANkHfu4LHJhACMNCPNgGbi5ixpom2e3T7oh7EPDG"), spotMarketContext62.oracle());

    // SpotMarket index 63 does not exist and tries to queue with a null AccountFetcher
    assertThrows(NullPointerException.class, () -> cache.spotMarket(maxSpotIndex + 1));

    // Validate perp market index 0 (SOL-PERP)
    var perpMarketContext0 = cache.perpMarket(0);
    assertNotNull(perpMarketContext0);
    assertEquals(0, perpMarketContext0.poolId());
    assertEquals(0, perpMarketContext0.marketIndex());
    assertEquals(PublicKey.fromBase58Encoded("8UJgxaiQx5nTrdDgph5FiahMmzduuLTLf5WmsPegYA6W"), perpMarketContext0.market());
    assertEquals(PublicKey.fromBase58Encoded("3m6i4RFWEDw2Ft4tFHPJtYgmpPe21k56M3FHeWYrgGBz"), perpMarketContext0.oracle());

    // Validate perp market index 2 (ETH-PERP)
    var perpMarketContext2 = cache.perpMarket(2);
    assertNotNull(perpMarketContext2);
    assertEquals(0, perpMarketContext2.poolId());
    assertEquals(2, perpMarketContext2.marketIndex());
    assertEquals(PublicKey.fromBase58Encoded("25Eax9W8SA3wpCQFhJEGyHhQ2NDHEshZEDzyMNtthR8D"), perpMarketContext2.market());
    assertEquals(PublicKey.fromBase58Encoded("93FG52TzNKCnMiasV14Ba34BYcHDb9p4zK4GjZnLwqWR"), perpMarketContext2.oracle());

    // Validate perp market index 8 (BNB-PERP)
    var perpMarketContext8 = cache.perpMarket(8);
    assertNotNull(perpMarketContext8);
    assertEquals(0, perpMarketContext8.poolId());
    assertEquals(8, perpMarketContext8.marketIndex());
    assertEquals(PublicKey.fromBase58Encoded("J6MErLoamPSkr6RzoYo8Da2WLCRmmmMQpanDSaenVCvq"), perpMarketContext8.market());
    assertEquals(PublicKey.fromBase58Encoded("A9J2j1pRB2aPqAbjUTtKy94niSCTuPUrpimfzvpZHKG1"), perpMarketContext8.oracle());

    // Validate perp market index 83 (1KMON-PERP)
    var perpMarketContext83 = cache.perpMarket(83);
    assertNotNull(perpMarketContext83);
    assertEquals(0, perpMarketContext83.poolId());
    assertEquals(83, perpMarketContext83.marketIndex());
    assertEquals(PublicKey.fromBase58Encoded("Cdx9uefR5d8HJQY5cQ69pkY5rFC6iiw4imDvT5W5dihC"), perpMarketContext83.market());
    assertEquals(PublicKey.fromBase58Encoded("585jsthKg9BeTfnFGAxgfNie9krGGyPbd5feMpWneHf7"), perpMarketContext83.oracle());

    // Validate perp market index 84 (LIT-PERP)
    var perpMarketContext84 = cache.perpMarket(84);
    assertNotNull(perpMarketContext84);
    assertEquals(0, perpMarketContext84.poolId());
    assertEquals(84, perpMarketContext84.marketIndex());
    assertEquals(PublicKey.fromBase58Encoded("7CcLj3MGYu2W8AEhaTS2pdvAWbx8BjCXu1YLMLnHFRFx"), perpMarketContext84.market());
    assertEquals(PublicKey.fromBase58Encoded("HsfwxaJdpY5Dvd3ttrrY7YL635T7D9W443XdTwE2Dvbh"), perpMarketContext84.oracle());

    // PerpMarket index 85 does not exist and tries to queue with a null AccountFetcher
    assertThrows(NullPointerException.class, () -> cache.perpMarket(maxPerpIndex + 1));
  }
}
