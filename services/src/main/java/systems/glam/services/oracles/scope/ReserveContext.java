package systems.glam.services.oracles.scope;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.kamino.lend.gen.types.Reserve;
import software.sava.idl.clients.kamino.scope.entries.PriceChains;

import java.util.Arrays;
import java.util.Map;

record ReserveContext(Reserve reserve,
                      String tokenName,
                      PublicKey mint,
                      PriceChains priceChains) {

  static ReserveContext createContext(final Reserve reserve, final Map<PublicKey, MappingsContext> scopeEntryMap) {
    final var mint = reserve.liquidity().mintPubkey();
    final byte[] name = reserve.config().tokenInfo().name();
    int i = name.length - 1;
    while (Character.isISOControl(name[i])) {
      --i;
    }

    final var scopeConfiguration = reserve.config().tokenInfo().scopeConfiguration();
    final var priceFeed = scopeConfiguration.priceFeed();
    final PriceChains priceChains;
    if (priceFeed.equals(PublicKey.NONE)) {
      priceChains = null;
    } else {
      final var scopeEntries = scopeEntryMap.get(priceFeed);
      if (scopeEntries == null) {
        return null;
      } else {
        priceChains = scopeEntries.scopeEntries().readPriceChains(reserve);
      }
    }

    return new ReserveContext(reserve, new String(name, 0, i + 1), mint, priceChains);
  }

  String keysToJson() {
    return String.format("""
            {
              "market": "%s",
              "reserve": "%s",
              "tokenName": "%s",
              "mint": "%s"
            }""",
        reserve.lendingMarket().toBase58(),
        reserve._address(),
        tokenName,
        mint
    );
  }

  String priceChainsToJson() {
    final var tokenInfo = reserve.config().tokenInfo();
    return String.format("""
            {
              "reserve": "%s",
              "tokenName": "%s",
              "mint": "%s",
              "maxTwapDivergenceBps": %d,
              "maxAgePriceSeconds": %d,
              "maxAgeTwapSeconds": %d,
              "priceChain": %s,
              "twapChain": %s
            }""",
        reserve._address(),
        tokenName,
        mint,
        tokenInfo.maxTwapDivergenceBps(),
        tokenInfo.maxAgePriceSeconds(),
        tokenInfo.maxAgeTwapSeconds(),
        ScopeMonitorService.toJson(priceChains.priceChain()),
        ScopeMonitorService.toJson(priceChains.twapChain())
    );
  }

  boolean changed(final ReserveContext o) {
    final var tokenInfo = reserve.config().tokenInfo();
    final var othertTokenInfo = o.reserve.config().tokenInfo();
    return !mint.equals(o.mint)
        || tokenInfo.maxTwapDivergenceBps() != othertTokenInfo.maxTwapDivergenceBps()
        || tokenInfo.maxAgePriceSeconds() != othertTokenInfo.maxAgePriceSeconds()
        || tokenInfo.maxAgeTwapSeconds() != othertTokenInfo.maxAgeTwapSeconds()
        || !Arrays.equals(priceChains.priceChain(), o.priceChains.priceChain())
        || !Arrays.equals(priceChains.twapChain(), o.priceChains.twapChain());
  }
}
