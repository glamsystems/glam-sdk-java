package systems.glam.services.oracles.scope;

import software.sava.core.accounts.PublicKey;
import software.sava.core.encoding.ByteUtil;
import software.sava.idl.clients.kamino.lend.gen.types.*;
import software.sava.idl.clients.kamino.scope.entries.PriceChains;
import software.sava.idl.clients.kamino.scope.entries.PriceChainsRecord;
import software.sava.idl.clients.kamino.scope.entries.ScopeEntry;
import software.sava.rpc.json.http.response.AccountInfo;
import systems.comodal.jsoniter.FieldBufferPredicate;
import systems.comodal.jsoniter.JsonIterator;
import systems.glam.services.oracles.scope.parsers.ScopeEntryParser;

import java.util.*;

public record ReserveContext(long slot,
                             PublicKey pubKey,
                             PublicKey market,
                             String tokenName,
                             PublicKey mint,
                             long availableLiquidity, long totalCollateral,
                             PriceChains priceChains,
                             TokenInfo tokenInfo) {

  public static final PublicKey NULL_KEY = PublicKey.fromBase58Encoded("nu11111111111111111111111111111111111111111");

  public static ReserveContext parse(final JsonIterator ji, final PublicKey market) {
    final var parser = new Parser(market);
    ji.testObject(parser);
    return parser.createContext();
  }

  static ReserveContext createContext(final long slot,
                                      final PublicKey reserveKey,
                                      final PublicKey lendingMarketKey,
                                      final PublicKey mintKey,
                                      final long availableLiquidity,
                                      final long totalCollateral,
                                      final TokenInfo tokenInfo,
                                      final PriceChains priceChains) {
    final byte[] name = tokenInfo.name();
    int i = name.length - 1;
    while (Character.isISOControl(name[i])) {
      if (--i < 0) {
        break;
      }
    }
    final var tokenName = i == 0 ? null : new String(name, 0, i + 1);
    return new ReserveContext(
        slot,
        reserveKey,
        lendingMarketKey,
        tokenName,
        mintKey,
        availableLiquidity,
        totalCollateral,
        priceChains,
        tokenInfo
    );
  }

  static ReserveContext createContext(final AccountInfo<byte[]> accountInfo,
                                      final Map<PublicKey, MappingsContext> mappingsContextByPriceFeed) {
    final byte[] data = accountInfo.data();
    final var lendingMarketKey = PublicKey.readPubKey(data, Reserve.LENDING_MARKET_OFFSET);
    final var mintKey = PublicKey.readPubKey(data, Reserve.COLLATERAL_OFFSET + ReserveLiquidity.MINT_PUBKEY_OFFSET);
    final var tokenInfo = TokenInfo.read(data, Reserve.CONFIG_OFFSET + ReserveConfig.TOKEN_INFO_OFFSET);
    final var scopeConfiguration = tokenInfo.scopeConfiguration();
    final var priceFeed = scopeConfiguration.priceFeed();
    final PriceChains priceChains;
    if (priceFeed.equals(PublicKey.NONE) || priceFeed.equals(NULL_KEY)) {
      priceChains = null;
    } else {
      final var scopeEntries = mappingsContextByPriceFeed.get(priceFeed);
      if (scopeEntries == null) {
        priceChains = null;
      } else {
        priceChains = scopeEntries.scopeEntries().readPriceChains(null);
      }
    }
    long availableLiquidity = ByteUtil.getInt64LE(data, Reserve.LIQUIDITY_OFFSET + ReserveLiquidity.AVAILABLE_AMOUNT_OFFSET);
    long totalCollateral = ByteUtil.getInt64LE(data, Reserve.COLLATERAL_OFFSET + ReserveCollateral.SUPPLY_VAULT_OFFSET);
    return createContext(
        accountInfo.context().slot(),
        accountInfo.pubKey(),
        lendingMarketKey,
        mintKey,
        availableLiquidity, totalCollateral,
        tokenInfo,
        priceChains
    );
  }

  public PublicKey priceFeed() {
    return this.tokenInfo.scopeConfiguration().priceFeed();
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
          priceFeed().toBase58(),
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
            priceFeed().toBase58(),
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
            priceFeed().toBase58(),
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

  String priceChainsToJsonNoTokenInfo() {
    if (priceChains == null) {
      return String.format("""
              {
                "reserve": "%s",
                "tokenName": %s,
                "mint": "%s",
                "priceFeed": "%s"
              }""",
          pubKey.toBase58(),
          jsonTokenName(),
          mint.toBase58(),
          priceFeed().toBase58()
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
                  "priceChain": %s
                }""",
            pubKey.toBase58(),
            jsonTokenName(),
            mint.toBase58(),
            priceFeed().toBase58(),
            maxAgePriceSeconds(),
            ScopeMonitorServiceImpl.toJson(priceChains.priceChain())
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
                  "twapChain": %s
                }""",
            pubKey.toBase58(),
            jsonTokenName(),
            mint.toBase58(),
            priceFeed().toBase58(),
            maxAgePriceSeconds(),
            maxAgeTwapSeconds(),
            maxTwapDivergenceBps(),
            ScopeMonitorServiceImpl.toJson(priceChains.priceChain()),
            ScopeMonitorServiceImpl.toJson(twapChain)
        );
      }
    }
  }

  enum ConfigChange {
    MARKET,
    MINT,
    TOKEN_NAME,
    MAX_AGE_PRICE_SECONDS,
    MAX_AGE_TWAP_SECONDS,
    MAX_TWAP_DIVERGENCE_BPS,
    PRICE_FEED,
    PRICE_CHAIN,
    TWAP_CHAIN
  }

  static final Set<ConfigChange> NO_CHANGES = Set.of();

  private static Set<ConfigChange> addChange(final Set<ConfigChange> changes, final ConfigChange change) {
    if (changes.isEmpty()) {
      return EnumSet.of(change);
    } else {
      changes.add(change);
      return changes;
    }
  }

  boolean isBefore(final ReserveContext o) {
    return Long.compareUnsigned(this.slot, o.slot) > 0;
  }

  Set<ConfigChange> changed(final ReserveContext o) {
    if (!pubKey.equals(o.pubKey)) {
      throw new IllegalStateException("Cannot compare different reserves");
    } else {
      var changes = NO_CHANGES;
      if (!market.equals(o.market)) {
        changes = EnumSet.of(ConfigChange.MARKET);
      }
      if (!mint.equals(o.mint)) {
        changes = addChange(changes, ConfigChange.MINT);
      }
      if (!Objects.equals(tokenName, o.tokenName)) {
        changes = addChange(changes, ConfigChange.TOKEN_NAME);
      }
      if (!priceFeed().equals(o.priceFeed())) {
        changes = addChange(changes, ConfigChange.PRICE_FEED);
      }
      if (maxAgePriceSeconds() != o.maxAgePriceSeconds()) {
        changes = addChange(changes, ConfigChange.MAX_AGE_PRICE_SECONDS);
      }
      if (maxAgeTwapSeconds() != o.maxAgeTwapSeconds()) {
        changes = addChange(changes, ConfigChange.MAX_AGE_TWAP_SECONDS);
      }
      if (maxTwapDivergenceBps() != o.maxTwapDivergenceBps()) {
        changes = addChange(changes, ConfigChange.MAX_TWAP_DIVERGENCE_BPS);
      }
      if (priceChains == null ^ o.priceChains == null) {
        changes = addChange(changes, ConfigChange.PRICE_CHAIN);
        changes.add(ConfigChange.TWAP_CHAIN);
      } else if (priceChains != null) {
        if (!Arrays.equals(priceChains.priceChain(), o.priceChains.priceChain())) {
          changes = addChange(changes, ConfigChange.PRICE_CHAIN);
        }
        if (!Arrays.equals(priceChains.twapChain(), o.priceChains.twapChain())) {
          changes = addChange(changes, ConfigChange.TWAP_CHAIN);
        }
      }
      return changes;
    }
  }

  public static final class Parser implements FieldBufferPredicate {

    private static final ScopeEntry[] EMPTY_CHAIN = new ScopeEntry[0];

    private final PublicKey market;
    private long slot;
    private PublicKey reserve;
    private String tokenName;
    private PublicKey mint;
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
          slot,
          reserve,
          market,
          tokenName,
          mint,
          0, 0,
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
      if (JsonIterator.fieldEquals("slot", buf, offset, len)) {
        this.slot = ji.readLong();
      } else if (JsonIterator.fieldEquals("reserve", buf, offset, len)) {
        this.reserve = PublicKey.fromBase58Encoded(ji.readString());
      } else if (JsonIterator.fieldEquals("tokenName", buf, offset, len)) {
        this.tokenName = ji.readString();
      } else if (JsonIterator.fieldEquals("mint", buf, offset, len)) {
        this.mint = PublicKey.fromBase58Encoded(ji.readString());
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
