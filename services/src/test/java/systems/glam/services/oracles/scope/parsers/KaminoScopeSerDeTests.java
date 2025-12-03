package systems.glam.services.oracles.scope.parsers;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.kamino.scope.entries.*;
import software.sava.idl.clients.kamino.scope.gen.types.OracleType;
import systems.glam.services.oracles.scope.ReserveContext;
import systems.glam.services.tests.ResourceUtil;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

final class KaminoScopeSerDeTests {

  @Test
  void parseReserveContexts() throws Exception {
    final byte[] data = ResourceUtil.readResource("scope/klend_reserves.json.zip");

    final var reserveContexts = HashMap.<PublicKey, ReserveContext>newHashMap(512);
    MarketParser.parseReserves(data, reserveContexts);

    assertFalse(reserveContexts.isEmpty());

    var reserveContext = reserveContexts.get(PublicKey.fromBase58Encoded("AEyCizr8c4SoStoueMJXZx55Hr1ftQ1R8ZH1sS8L9dXv"));
    assertEquals(PublicKey.fromBase58Encoded("AEyCizr8c4SoStoueMJXZx55Hr1ftQ1R8ZH1sS8L9dXv"), reserveContext.pubKey());
    assertEquals("USDC", reserveContext.tokenName());
    assertEquals(PublicKey.fromBase58Encoded("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"), reserveContext.mint());
    assertEquals(180L, reserveContext.maxAgePriceSeconds());
    assertEquals(240L, reserveContext.maxAgeTwapSeconds());
    assertEquals(0L, reserveContext.maxTwapDivergenceBps());

    var chain = reserveContext.priceChains().priceChain();
    assertEquals(1, chain.length);
    var fixedPrice = assertInstanceOf(FixedPrice.class, chain[0]);
    assertEquals(1000000000000L, fixedPrice.value());
    assertEquals(18, fixedPrice.exp());
    assertEquals(OracleType.FixedPrice, fixedPrice.oracleType());

    chain = reserveContext.priceChains().twapChain();
    assertEquals(1, chain.length);
    fixedPrice = assertInstanceOf(FixedPrice.class, chain[0]);
    assertEquals(1000000000000L, fixedPrice.value());
    assertEquals(18, fixedPrice.exp());
    assertEquals(OracleType.FixedPrice, fixedPrice.oracleType());


    reserveContext = reserveContexts.get(PublicKey.fromBase58Encoded("4Vjt7rRX8FDwW1RstnSB2XBtk2iSZcDTbqWYX5hQNu7P"));
    assertEquals(PublicKey.fromBase58Encoded("4Vjt7rRX8FDwW1RstnSB2XBtk2iSZcDTbqWYX5hQNu7P"), reserveContext.pubKey());
    assertEquals("BONK", reserveContext.tokenName());
    assertEquals(PublicKey.fromBase58Encoded("DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263"), reserveContext.mint());
    assertEquals(180L, reserveContext.maxAgePriceSeconds());
    assertEquals(240L, reserveContext.maxAgeTwapSeconds());
    assertEquals(0L, reserveContext.maxTwapDivergenceBps());

    chain = reserveContext.priceChains().priceChain();
    assertEquals(1, chain.length);
    fixedPrice = assertInstanceOf(FixedPrice.class, chain[0]);
    assertEquals(1000000000000L, fixedPrice.value());
    assertEquals(18, fixedPrice.exp());
    assertEquals(OracleType.FixedPrice, fixedPrice.oracleType());

    chain = reserveContext.priceChains().twapChain();
    assertEquals(1, chain.length);
    fixedPrice = assertInstanceOf(FixedPrice.class, chain[0]);
    assertEquals(1000000000000L, fixedPrice.value());
    assertEquals(18, fixedPrice.exp());
    assertEquals(OracleType.FixedPrice, fixedPrice.oracleType());

    reserveContext = reserveContexts.get(PublicKey.fromBase58Encoded("97zoywd8mPZsGTg8q1wdD2Wgkdrs2tqusp1Qqcxbyj7E"));

    assertEquals("USDC", reserveContext.tokenName());
    assertEquals(PublicKey.fromBase58Encoded("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"), reserveContext.mint());
    assertEquals(180L, reserveContext.maxAgePriceSeconds());
    assertEquals(240L, reserveContext.maxAgeTwapSeconds());
    assertEquals(300L, reserveContext.maxTwapDivergenceBps());

    chain = reserveContext.priceChains().priceChain();
    assertEquals(1, chain.length);
    var cappedFloored = assertInstanceOf(CappedFloored.class, chain[0]);

    var cap = assertInstanceOf(FixedPrice.class, cappedFloored.capEntry());
    assertEquals(1_000_000_000_000L, cap.value());
    assertEquals(12, cap.exp());
    assertNull(cappedFloored.flooredEntry());

    var mro = assertInstanceOf(MostRecentOfEntry.class, cappedFloored.sourceEntry());
    assertEquals(75, mro.maxDivergenceBps());
    assertEquals(7200L, mro.sourcesMaxAgeS());
    assertEquals(4, mro.sources().length);

    var s0 = assertInstanceOf(PythPull.class, mro.sources()[0]);
    assertEquals(PublicKey.fromBase58Encoded("Dpw1EAVrSB1ibxiDQyTAW6Zip3J4Btk2x4SgApQCeFbX"), s0.oracle());
    assertFalse(s0.twapEnabled());

    var s1 = assertInstanceOf(Chainlink.class, mro.sources()[1]);
    assertEquals(PublicKey.fromBase58Encoded("149eGQvrtmymuPsmd3EhYzYVboeAvDRx4cS3MExZuMj"), s1.oracle());
    assertTrue(s1.twapEnabled());
    assertEquals(200, s1.confidenceFactor());
    var s1Ref = assertInstanceOf(PythPull.class, s1.refPrice());
    assertEquals(PublicKey.fromBase58Encoded("Dpw1EAVrSB1ibxiDQyTAW6Zip3J4Btk2x4SgApQCeFbX"), s1Ref.oracle());
    assertFalse(s1Ref.twapEnabled());

    var s2 = assertInstanceOf(PythLazer.class, mro.sources()[2]);
    assertEquals(PublicKey.fromBase58Encoded("HFn8GnPADiny6XqUoWE8uRPPxb29ikn4yTuPa9MF2fWJ"), s2.oracle());
    assertFalse(s2.twapEnabled());
    assertEquals(7, s2.feedId());
    assertEquals(8, s2.exponent());
    assertEquals(100, s2.confidenceFactor());
    var s2Ref = assertInstanceOf(PythPull.class, s2.refPrice());
    assertEquals(PublicKey.fromBase58Encoded("Dpw1EAVrSB1ibxiDQyTAW6Zip3J4Btk2x4SgApQCeFbX"), s2Ref.oracle());
    assertFalse(s2Ref.twapEnabled());

    var s3 = assertInstanceOf(FixedPrice.class, mro.sources()[3]);
    assertEquals(995000000000L, s3.value());
    assertEquals(12, s3.exp());
    assertEquals("0.995", s3.decimal().toPlainString());

    var mroRef = assertInstanceOf(PythPull.class, mro.refPrice());
    assertEquals(PublicKey.fromBase58Encoded("Dpw1EAVrSB1ibxiDQyTAW6Zip3J4Btk2x4SgApQCeFbX"), mroRef.oracle());
    assertFalse(mroRef.twapEnabled());

    chain = reserveContext.priceChains().twapChain();
    assertEquals(1, chain.length);
    var twap = assertInstanceOf(PythPullEMA.class, chain[0]);
    assertEquals(PublicKey.fromBase58Encoded("Dpw1EAVrSB1ibxiDQyTAW6Zip3J4Btk2x4SgApQCeFbX"), twap.oracle());
    assertFalse(twap.twapEnabled());

    reserveContext = reserveContexts.get(PublicKey.fromBase58Encoded("DidQ9f4FQg1snFjEjNUdidSbyz1ixsmRZqBWKfqDzUoS"));

    assertEquals("PT-fragSOL-31OCT25", reserveContext.tokenName());
    assertEquals(PublicKey.fromBase58Encoded("Aby6y5DYtTrhQD8i7JXLs4H3jdUTwSXDraYqnwn5tKbt"), reserveContext.mint());
    assertEquals(120L, reserveContext.maxAgePriceSeconds());
    assertEquals(240L, reserveContext.maxAgeTwapSeconds());
    assertEquals(1000L, reserveContext.maxTwapDivergenceBps());

    chain = reserveContext.priceChains().priceChain();
    assertEquals(2, chain.length);
    var dtm = assertInstanceOf(DiscountToMaturity.class, chain[0]);
    assertEquals(2500, dtm.discountPerYearBps());
    assertEquals(1761904699L, dtm.maturityTimestamp());
    var didPyth = assertInstanceOf(PythPull.class, chain[1]);
    assertEquals(PublicKey.fromBase58Encoded("7UVimffxr9ow1uXYxsr4LHAcV58mLzhmwaeKvJ1pjLiE"), didPyth.oracle());
    assertFalse(didPyth.twapEnabled());

    chain = reserveContext.priceChains().twapChain();
    assertEquals(2, chain.length);
    dtm = assertInstanceOf(DiscountToMaturity.class, chain[0]);
    assertEquals(2500, dtm.discountPerYearBps());
    assertEquals(1761904699L, dtm.maturityTimestamp());
    var didPythEma = assertInstanceOf(PythPullEMA.class, chain[1]);
    assertEquals(PublicKey.fromBase58Encoded("7UVimffxr9ow1uXYxsr4LHAcV58mLzhmwaeKvJ1pjLiE"), didPythEma.oracle());
    assertFalse(didPythEma.twapEnabled());

    reserveContext = reserveContexts.get(PublicKey.fromBase58Encoded("F1xMZ8em6SrQkCnKQR1pzcxQieSUth35sYDQ2kK6o8tX"));
    assertEquals(PublicKey.fromBase58Encoded("5wJeMrUYECGq41fxRESKALVcHnNX26TAWy4W98yULsua"), reserveContext.market());

    assertEquals("USDG", reserveContext.tokenName());
    assertEquals(PublicKey.fromBase58Encoded("2u1tszSeqZ3qBWF3uNGPFc8TzMk2tdiwknnRMWGWjGWH"), reserveContext.mint());
    assertEquals(180L, reserveContext.maxAgePriceSeconds());
    assertEquals(240L, reserveContext.maxAgeTwapSeconds());
    assertEquals(300L, reserveContext.maxTwapDivergenceBps());

    chain = reserveContext.priceChains().priceChain();
    assertEquals(1, chain.length);
    var usdgcappedFloored = assertInstanceOf(CappedFloored.class, chain[0]);

    var usdgCap = assertInstanceOf(FixedPrice.class, usdgcappedFloored.capEntry());
    assertEquals(1_000_000_000_000L, usdgCap.value());
    assertEquals(12, usdgCap.exp());
    assertNull(usdgcappedFloored.flooredEntry());

    var usdgmro = assertInstanceOf(MostRecentOfEntry.class, usdgcappedFloored.sourceEntry());
    assertEquals(75, usdgmro.maxDivergenceBps());
    assertEquals(7200L, usdgmro.sourcesMaxAgeS());
    assertEquals(3, usdgmro.sources().length);

    var u0 = assertInstanceOf(PythPull.class, usdgmro.sources()[0]);
    assertEquals(PublicKey.fromBase58Encoded("6JkZmXGgWnzsyTQaqRARzP64iFYnpMNT4siiuUDUaB8s"), u0.oracle());
    assertFalse(u0.twapEnabled());

    var u1 = assertInstanceOf(PythLazer.class, usdgmro.sources()[1]);
    assertEquals(PublicKey.fromBase58Encoded("HFn8GnPADiny6XqUoWE8uRPPxb29ikn4yTuPa9MF2fWJ"), u1.oracle());
    assertFalse(u1.twapEnabled());
    assertEquals(232, u1.feedId());
    assertEquals(8, u1.exponent());
    assertEquals(200, u1.confidenceFactor());
    var u1Ref = assertInstanceOf(PythPull.class, u1.refPrice());
    assertEquals(PublicKey.fromBase58Encoded("6JkZmXGgWnzsyTQaqRARzP64iFYnpMNT4siiuUDUaB8s"), u1Ref.oracle());
    assertFalse(u1Ref.twapEnabled());

    var u2 = assertInstanceOf(FixedPrice.class, usdgmro.sources()[2]);
    assertEquals(995000000000L, u2.value());
    assertEquals(12, u2.exp());

    var usdgmroRef = assertInstanceOf(PythPull.class, usdgmro.refPrice());
    assertEquals(PublicKey.fromBase58Encoded("6JkZmXGgWnzsyTQaqRARzP64iFYnpMNT4siiuUDUaB8s"), usdgmroRef.oracle());
    assertFalse(usdgmroRef.twapEnabled());

    chain = reserveContext.priceChains().twapChain();
    assertEquals(1, chain.length);
    var usdgtwap = assertInstanceOf(PythPullEMA.class, chain[0]);
    assertEquals(PublicKey.fromBase58Encoded("6JkZmXGgWnzsyTQaqRARzP64iFYnpMNT4siiuUDUaB8s"), usdgtwap.oracle());
    assertFalse(usdgtwap.twapEnabled());
  }
}
