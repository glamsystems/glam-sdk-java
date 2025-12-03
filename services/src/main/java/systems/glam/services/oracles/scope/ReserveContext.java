package systems.glam.services.oracles.scope;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.kamino.lend.gen.types.Reserve;
import software.sava.idl.clients.kamino.lend.gen.types.ScopeConfiguration;
import software.sava.idl.clients.kamino.lend.gen.types.TokenInfo;
import software.sava.idl.clients.kamino.scope.entries.PriceChains;
import software.sava.idl.clients.kamino.scope.entries.PriceChainsRecord;
import software.sava.idl.clients.kamino.scope.entries.ScopeEntry;
import systems.comodal.jsoniter.FieldBufferPredicate;
import systems.comodal.jsoniter.JsonIterator;
import systems.glam.services.oracles.scope.parsers.ScopeEntryParser;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

public record ReserveContext(PublicKey pubKey,
                             PublicKey market,
                             String tokenName,
                             PublicKey mint,
                             PublicKey priceFeed,
                             PriceChains priceChains,
                             TokenInfo tokenInfo) {

  public static final PublicKey NULL_KEY = PublicKey.fromBase58Encoded("nu11111111111111111111111111111111111111111");

  public static ReserveContext parse(final JsonIterator ji, final PublicKey market) {
    final var parser = new Parser(market);
    ji.testObject(parser);
    return parser.createContext();
  }

  static ReserveContext createContext(final Reserve reserve, final PriceChains priceChains) {
    final var mint = reserve.liquidity().mintPubkey();
    final byte[] name = reserve.config().tokenInfo().name();
    int i = name.length - 1;
    while (Character.isISOControl(name[i])) {
      if (--i < 0) {
        break;
      }
    }
    final var tokenName = i == 0 ? null : new String(name, 0, i + 1);
    final var tokenInfo = reserve.config().tokenInfo();
    return new ReserveContext(
        reserve._address(),
        reserve.lendingMarket(),
        tokenName,
        mint,
        tokenInfo.scopeConfiguration().priceFeed(),
        priceChains,
        tokenInfo
    );
  }

  static ReserveContext createContext(final Reserve reserve,
                                      final Map<PublicKey, MappingsContext> mappingsContextByPriceFeed) {
    final var tokenInfo = reserve.config().tokenInfo();
    final var scopeConfiguration = tokenInfo.scopeConfiguration();
    final var priceFeed = scopeConfiguration.priceFeed();
    final PriceChains priceChains;
    if (priceFeed.equals(PublicKey.NONE) || priceFeed.equals(NULL_KEY)) {
      priceChains = null;
    } else {
      final var scopeEntries = mappingsContextByPriceFeed.get(priceFeed);
      if (scopeEntries == null) {
        return null;
      } else {
        priceChains = scopeEntries.scopeEntries().readPriceChains(reserve);
      }
    }
    return createContext(reserve, priceChains);
  }

  public long maxAgePriceSeconds() {
    return tokenInfo.maxAgePriceSeconds();
  }

  public long maxAgeTwapSeconds() {
    return tokenInfo.maxAgeTwapSeconds();
  }

  public long maxTwapDivergenceBps() {
    return tokenInfo.maxTwapDivergenceBps();
  }

  private String jsonTokenName() {
    if (tokenName == null) {
      return "null";
    } else {
      return '"' + tokenName + '"';
    }
  }

  String keysToJson() {
    return String.format("""
            {
              "market": "%s",
              "reserve": "%s",
              "tokenName": %s,
              "mint": "%s"
            }""",
        market.toBase58(),
        pubKey.toBase58(),
        jsonTokenName(),
        mint
    );
  }

  String priceChainsToJson() {
    final byte[] tokenInfo = new byte[TokenInfo.BYTES];
    this.tokenInfo.write(tokenInfo, 0);
    final var encodedTokenInfo = Base64.getEncoder().encodeToString(tokenInfo);
    if (priceChains == null) {
      return String.format("""
              {
                "reserve": "%s",
                "tokenName": %s,
                "mint": "%s",
                "priceFeed": "%s",
                "tokenInfo": "%s"
              }""",
          pubKey.toBase58(),
          jsonTokenName(),
          mint.toBase58(),
          priceFeed.toBase58(),
          encodedTokenInfo
      );
    } else {
      final var twapChain = priceChains.twapChain();
      if (twapChain.length == 0) {
        return String.format("""
                {
                  "reserve": "%s",
                  "tokenName": %s,
                  "mint": "%s",
                  "priceFeed": "%s",
                  "maxAgePriceSeconds": %d,
                  "priceChain": %s,
                  "tokenInfo": "%s"
                }""",
            pubKey.toBase58(),
            jsonTokenName(),
            mint.toBase58(),
            priceFeed.toBase58(),
            maxAgePriceSeconds(),
            ScopeMonitorServiceImpl.toJson(priceChains.priceChain()),
            encodedTokenInfo
        );
      } else {
        return String.format("""
                {
                  "reserve": "%s",
                  "tokenName": %s,
                  "mint": "%s",
                  "priceFeed": "%s",
                  "maxAgePriceSeconds": %d,
                  "maxAgeTwapSeconds": %d,
                  "maxTwapDivergenceBps": %d,
                  "priceChain": %s,
                  "twapChain": %s,
                  "tokenInfo": "%s"
                }""",
            pubKey.toBase58(),
            jsonTokenName(),
            mint.toBase58(),
            priceFeed.toBase58(),
            maxAgePriceSeconds(),
            maxAgeTwapSeconds(),
            maxTwapDivergenceBps(),
            ScopeMonitorServiceImpl.toJson(priceChains.priceChain()),
            ScopeMonitorServiceImpl.toJson(twapChain),
            encodedTokenInfo
        );
      }
    }
  }

  boolean changed(final ReserveContext o) {
    if (!pubKey.equals(o.pubKey)) {
      throw new IllegalStateException("Cannot compare different reserves");
    } else {
      return !market.equals(o.market)
          || !mint.equals(o.mint)
          || !Objects.equals(tokenName, o.tokenName)
          || !Objects.equals(priceChains, o.priceChains);
    }
  }

  public static final class Parser implements FieldBufferPredicate {

    private static final ScopeEntry[] EMPTY_CHAIN = new ScopeEntry[0];

    private final PublicKey market;
    private PublicKey reserve;
    private String tokenName;
    private PublicKey mint;
    private PublicKey priceFeed;
    private ScopeEntry[] priceChain;
    private ScopeEntry[] twapChain;
    private TokenInfo tokenInfo;

    public Parser(final PublicKey market) {
      this.market = market;
    }

    private ReserveContext createContext() {
      final PriceChains priceChains;
      if (priceChain == null && twapChain == null) {
        priceChains = null;
      } else {
        priceChains = new PriceChainsRecord(
            priceChain == null ? EMPTY_CHAIN : priceChain,
            twapChain == null ? EMPTY_CHAIN : twapChain
        );
      }
      return new ReserveContext(
          reserve,
          market,
          tokenName,
          mint,
          priceFeed,
          priceChains,
          tokenInfo
      );
    }

    private static ScopeEntry[] parseChain(final JsonIterator ji) {
      final var priceChain = new ArrayList<ScopeEntry>(ScopeConfiguration.PRICE_CHAIN_LEN);
      while (ji.readArray()) {
        priceChain.add(ScopeEntryParser.parseEntry(ji));
      }
      return priceChain.isEmpty() ? null : priceChain.toArray(ScopeEntry[]::new);
    }

    @Override
    public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
      if (JsonIterator.fieldEquals("reserve", buf, offset, len)) {
        this.reserve = PublicKey.fromBase58Encoded(ji.readString());
      } else if (JsonIterator.fieldEquals("tokenName", buf, offset, len)) {
        this.tokenName = ji.readString();
      } else if (JsonIterator.fieldEquals("mint", buf, offset, len)) {
        this.mint = PublicKey.fromBase58Encoded(ji.readString());
      } else if (JsonIterator.fieldEquals("priceFeed", buf, offset, len)) {
        this.priceFeed = PublicKey.fromBase58Encoded(ji.readString());
      } else if (JsonIterator.fieldEquals("priceChain", buf, offset, len)) {
        this.priceChain = parseChain(ji);
      } else if (JsonIterator.fieldEquals("twapChain", buf, offset, len)) {
        this.twapChain = parseChain(ji);
      } else if (JsonIterator.fieldEquals("tokenInfo", buf, offset, len)) {
        this.tokenInfo = TokenInfo.read(ji.decodeBase64String(), 0);
      } else {
        ji.skip();
      }
      return true;
    }
  }
}
