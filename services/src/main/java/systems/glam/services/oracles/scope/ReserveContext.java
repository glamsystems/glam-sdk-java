package systems.glam.services.oracles.scope;

import software.sava.core.accounts.PublicKey;
import software.sava.core.encoding.ByteUtil;
import software.sava.idl.clients.kamino.KaminoAccounts;
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
                             PublicKey mint, long availableLiquidity,
                             long totalCollateral,
                             PriceChains priceChains,
                             TokenInfo tokenInfo) {

  public static ReserveContext parseContext(final JsonIterator ji,
                                            final PublicKey market,
                                            final Map<PublicKey, MappingsContext> mappingsContextByPriceFeed) {
    final var parser = new Parser();
    ji.testObject(parser);
    return parser.createContext(market, mappingsContextByPriceFeed);
  }

  private static ReserveContext createContext(final long slot,
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

  private static PriceChains readPriceChains(final PublicKey mintKey,
                                             final ScopeConfiguration scopeConfiguration,
                                             final Map<PublicKey, MappingsContext> mappingsContextByPriceFeed) {
    final var priceFeed = scopeConfiguration.priceFeed();
    if (priceFeed.equals(PublicKey.NONE) || priceFeed.equals(KaminoAccounts.NULL_KEY)) {
      return null;
    } else {
      final var scopeEntries = mappingsContextByPriceFeed.get(priceFeed);
      return scopeEntries == null ? null : scopeEntries.readPriceChains(mintKey, scopeConfiguration);
    }
  }

  static ReserveContext createContext(final AccountInfo<byte[]> accountInfo,
                                      final Map<PublicKey, MappingsContext> mappingsContextByPriceFeed) {
    final byte[] data = accountInfo.data();
    final var lendingMarketKey = PublicKey.readPubKey(data, Reserve.LENDING_MARKET_OFFSET);
    final var mintKey = PublicKey.readPubKey(data, Reserve.LIQUIDITY_OFFSET + ReserveLiquidity.MINT_PUBKEY_OFFSET);
    final var tokenInfo = TokenInfo.read(data, Reserve.CONFIG_OFFSET + ReserveConfig.TOKEN_INFO_OFFSET);
    final var scopeConfiguration = tokenInfo.scopeConfiguration();
    final var priceChains = readPriceChains(mintKey, scopeConfiguration, mappingsContextByPriceFeed);
    long availableLiquidity = ByteUtil.getInt64LE(data, Reserve.LIQUIDITY_OFFSET + ReserveLiquidity.AVAILABLE_AMOUNT_OFFSET);
    long totalCollateral = ByteUtil.getInt64LE(data, Reserve.COLLATERAL_OFFSET + ReserveCollateral.MINT_TOTAL_SUPPLY_OFFSET);
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

  public ReserveContext withPriceChains(final PriceChains priceChains) {
    return new ReserveContext(slot, pubKey, market, tokenName, mint, availableLiquidity, totalCollateral, priceChains, tokenInfo);
  }

  public ScopeConfiguration scopeConfiguration() {
    return tokenInfo.scopeConfiguration();
  }

  public short[] priceChainIndexes() {
    return scopeConfiguration().priceChain();
  }

  public PublicKey priceFeed() {
    return scopeConfiguration().priceFeed();
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
              "mint": "%s",
              "availableLiquidity": %d,
              "totalCollateral": %d
            }""",
        market.toBase58(),
        pubKey.toBase58(),
        jsonTokenName(),
        mint,
        availableLiquidity,
        totalCollateral
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
                "availableLiquidity": %d,
                "totalCollateral": %d,
                "priceFeed": "%s",
                "tokenInfo": "%s"
              }""",
          pubKey.toBase58(),
          jsonTokenName(),
          mint.toBase58(),
          availableLiquidity,
          totalCollateral,
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
                  "availableLiquidity": %d,
                  "totalCollateral": %d,
                  "priceFeed": "%s",
                  "maxAgePriceSeconds": %d,
                  "priceChain": %s,
                  "tokenInfo": "%s"
                }""",
            pubKey.toBase58(),
            jsonTokenName(),
            mint.toBase58(),
            availableLiquidity,
            totalCollateral,
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
                  "availableLiquidity": %d,
                  "totalCollateral": %d,
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
            availableLiquidity,
            totalCollateral,
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
                "availableLiquidity": %d,
                "totalCollateral": %d,
                "priceFeed": "%s"
              }""",
          pubKey.toBase58(),
          jsonTokenName(),
          mint.toBase58(),
          availableLiquidity,
          totalCollateral,
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
                  "availableLiquidity": %d,
                  "totalCollateral": %d,
                  "priceFeed": "%s",
                  "maxAgePriceSeconds": %d,
                  "priceChain": %s
                }""",
            pubKey.toBase58(),
            jsonTokenName(),
            mint.toBase58(),
            availableLiquidity,
            totalCollateral,
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
                  "availableLiquidity": %d,
                  "totalCollateral": %d,
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
            availableLiquidity,
            totalCollateral,
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

  enum ReserveChange {
    MARKET,
    MINT,
    TOKEN_NAME,
    AVAILABLE_LIQUIDITY,
    TOTAL_COLLATERAL,
    MAX_AGE_PRICE_SECONDS,
    MAX_AGE_TWAP_SECONDS,
    MAX_TWAP_DIVERGENCE_BPS,
    PRICE_FEED,
    PRICE_CHAIN,
    TWAP_CHAIN
  }

  static boolean onlyLiquidityChanged(final Set<ReserveChange> changes) {
    final int numChanges = changes.size();
    if (numChanges == 1
        && (changes.contains(ReserveChange.AVAILABLE_LIQUIDITY) || changes.contains(ReserveChange.TOTAL_COLLATERAL))) {
      return true;
    } else if (numChanges == 2
        && changes.contains(ReserveChange.AVAILABLE_LIQUIDITY)
        && changes.contains(ReserveChange.TOTAL_COLLATERAL)) {
      return true;
    } else {
      return false;
    }
  }

  static final Set<ReserveChange> NO_CHANGES = Set.of();

  private static Set<ReserveChange> addChange(final Set<ReserveChange> changes, final ReserveChange change) {
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

  Set<ReserveChange> changed(final ReserveContext o) {
    if (!pubKey.equals(o.pubKey)) {
      throw new IllegalStateException("Cannot compare different reserves");
    } else {
      var changes = NO_CHANGES;
      if (!market.equals(o.market)) {
        changes = EnumSet.of(ReserveChange.MARKET);
      }
      if (!mint.equals(o.mint)) {
        changes = addChange(changes, ReserveChange.MINT);
      }
      if (!Objects.equals(tokenName, o.tokenName)) {
        changes = addChange(changes, ReserveChange.TOKEN_NAME);
      }
      if (availableLiquidity != o.availableLiquidity()) {
        changes = addChange(changes, ReserveChange.AVAILABLE_LIQUIDITY);
      }
      if (totalCollateral != o.totalCollateral()) {
        changes = addChange(changes, ReserveChange.TOTAL_COLLATERAL);
      }
      if (!priceFeed().equals(o.priceFeed())) {
        changes = addChange(changes, ReserveChange.PRICE_FEED);
      }
      if (maxAgePriceSeconds() != o.maxAgePriceSeconds()) {
        changes = addChange(changes, ReserveChange.MAX_AGE_PRICE_SECONDS);
      }
      if (maxAgeTwapSeconds() != o.maxAgeTwapSeconds()) {
        changes = addChange(changes, ReserveChange.MAX_AGE_TWAP_SECONDS);
      }
      if (maxTwapDivergenceBps() != o.maxTwapDivergenceBps()) {
        changes = addChange(changes, ReserveChange.MAX_TWAP_DIVERGENCE_BPS);
      }
      if (priceChains == null ^ o.priceChains == null) {
        changes = addChange(changes, ReserveChange.PRICE_CHAIN);
        changes.add(ReserveChange.TWAP_CHAIN);
      } else if (priceChains != null) {
        if (!Arrays.equals(priceChains.priceChain(), o.priceChains.priceChain())) {
          changes = addChange(changes, ReserveChange.PRICE_CHAIN);
        }
        if (!Arrays.equals(priceChains.twapChain(), o.priceChains.twapChain())) {
          changes = addChange(changes, ReserveChange.TWAP_CHAIN);
        }
      }
      return changes;
    }
  }

  public static final class Parser implements FieldBufferPredicate {

    private static final ScopeEntry[] EMPTY_CHAIN = new ScopeEntry[0];

    private long slot;
    private PublicKey reserve;
    private String tokenName;
    private PublicKey mint;
    private long availableLiquidity;
    private long totalCollateral;
    private ScopeEntry[] priceChain;
    private ScopeEntry[] twapChain;
    private TokenInfo tokenInfo;

    private Parser() {
    }

    private ReserveContext createContext(final PublicKey market,
                                         final Map<PublicKey, MappingsContext> mappingsContextByPriceFeed) {
      final PriceChains priceChains;
      if (mappingsContextByPriceFeed != null) {
        priceChains = readPriceChains(
            mint, tokenInfo.scopeConfiguration(), mappingsContextByPriceFeed
        );
      } else if (priceChain == null && twapChain == null) {
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
          availableLiquidity,
          totalCollateral,
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
      } else if (JsonIterator.fieldEquals("availableLiquidity", buf, offset, len)) {
        this.availableLiquidity = ji.readLong();
      } else if (JsonIterator.fieldEquals("totalCollateral", buf, offset, len)) {
        this.totalCollateral = ji.readLong();
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
