package systems.glam.services.integrations.kamino;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.encoding.ByteUtil;
import software.sava.idl.clients.kamino.KaminoAccounts;
import software.sava.idl.clients.kamino.lend.gen.types.*;
import software.sava.idl.clients.kamino.scope.entries.PriceChains;
import software.sava.idl.clients.kamino.scope.entries.PriceChainsRecord;
import software.sava.idl.clients.kamino.scope.entries.ScopeEntry;
import software.sava.rpc.json.PublicKeyEncoding;
import software.sava.rpc.json.http.response.AccountInfo;
import systems.comodal.jsoniter.FieldBufferPredicate;
import systems.comodal.jsoniter.JsonIterator;
import systems.glam.services.oracles.scope.MappingsContext;
import systems.glam.services.oracles.scope.parsers.ScopeEntryParser;

import java.util.*;

import static software.sava.idl.clients.kamino.KaminoAccounts.NULL_KEY;

public record ReserveContext(long slot,
                             PublicKey pubKey, AccountMeta writeReserve,
                             PublicKey market,
                             String tokenName,
                             PublicKey mint,
                             long totalCollateral,
                             PriceChains priceChains,
                             TokenInfo tokenInfo) {

  private static final Map<PublicKey, AccountMeta> READ_PRICE_FEED_METAS = new HashMap<>();
  private static final Map<PublicKey, AccountMeta> WRITE_MARKET_METAS = new HashMap<>(128);

  static AccountMeta readPriceFeedMeta(final PublicKey priceFeed) {
    var readPriceFeedMeta = READ_PRICE_FEED_METAS.get(priceFeed);
    if (readPriceFeedMeta == null) {
      readPriceFeedMeta = AccountMeta.createRead(priceFeed);
      READ_PRICE_FEED_METAS.putIfAbsent(priceFeed, readPriceFeedMeta);
    }
    return readPriceFeedMeta;
  }

  static AccountMeta writeMarketMeta(final PublicKey market) {
    var writeMarketMeta = WRITE_MARKET_METAS.get(market);
    if (writeMarketMeta == null) {
      writeMarketMeta = AccountMeta.createWrite(market);
      WRITE_MARKET_METAS.putIfAbsent(market, writeMarketMeta);
    }
    return writeMarketMeta;
  }

  public static ReserveContext parseContext(final JsonIterator ji,
                                            final PublicKey market,
                                            final Map<PublicKey, MappingsContext> mappingsContextByPriceFeed) {
    final var parser = new Parser();
    ji.testObject(parser);
    return parser.createContext(market, mappingsContextByPriceFeed);
  }

  public static String fixedLengthString(final byte[] data) {
    return fixedLengthString(data, 0, data.length);
  }

  public static String fixedLengthString(final byte[] data, final int from, final int to) {
    int i = to - 1;
    while (i >= from && (Character.isISOControl(data[i]) || Character.isWhitespace(data[i]))) {
      --i;
    }
    if (i < from) {
      return null;
    } else {
      final var str = new String(data, from, (i - from) + 1);
      return str.isBlank() ? null : str;
    }
  }

  private static ReserveContext createContext(final long slot,
                                              final PublicKey reserveKey,
                                              final PublicKey lendingMarketKey,
                                              final PublicKey mintKey,
                                              final long totalCollateral,
                                              final TokenInfo tokenInfo,
                                              final PriceChains priceChains) {
    final byte[] name = tokenInfo.name();
    final var tokenName = fixedLengthString(name);
    return new ReserveContext(
        slot,
        reserveKey, AccountMeta.createWrite(reserveKey),
        lendingMarketKey,
        tokenName,
        mintKey,
        totalCollateral,
        priceChains,
        tokenInfo
    );
  }

  private static PriceChains readPriceChains(final PublicKey mintKey,
                                             final ScopeConfiguration scopeConfiguration,
                                             final Map<PublicKey, MappingsContext> mappingsContextByPriceFeed) {
    final var priceFeed = scopeConfiguration.priceFeed();
    if (priceFeed.equals(PublicKey.NONE) || priceFeed.equals(NULL_KEY)) {
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
    long totalCollateral = ByteUtil.getInt64LE(data, Reserve.COLLATERAL_OFFSET + ReserveCollateral.MINT_TOTAL_SUPPLY_OFFSET);
    return createContext(
        accountInfo.context().slot(),
        accountInfo.pubKey(),
        lendingMarketKey,
        mintKey,
        totalCollateral,
        tokenInfo,
        priceChains
    );
  }

  public ReserveContext withPriceChains(final PriceChains priceChains) {
    return new ReserveContext(
        slot,
        pubKey, writeReserve,
        market,
        tokenName,
        mint,
        totalCollateral,
        priceChains,
        tokenInfo
    );
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

  static boolean isNullKey(final PublicKey key) {
    return key.equals(PublicKey.NONE) || key.equals(NULL_KEY);
  }

  private static final AccountMeta NULL_ACCOUNT_META = AccountMeta.createInvoked(KaminoAccounts.MAIN_NET.kLendProgram());

  public void refreshReserveAccounts(final SequencedCollection<AccountMeta> accounts) {
    accounts.add(writeReserve);
    accounts.add(writeMarketMeta(market));

    final var priceFeed = priceFeed();
    if (isNullKey(priceFeed)) {
      final var pythOracle = tokenInfo.pythConfiguration().price();
      if (isNullKey(pythOracle)) {
        final var switchboardConfig = tokenInfo.switchboardConfiguration();
        final var switchboardOracle = switchboardConfig.priceAggregator();
        if (isNullKey(switchboardOracle)) {
          throw new IllegalStateException("No oracle configuration for Kamino Reserve " + pubKey);
        } else {
          accounts.add(NULL_ACCOUNT_META);
          accounts.add(readPriceFeedMeta(switchboardOracle));
          accounts.add(readPriceFeedMeta(switchboardConfig.twapAggregator()));
          accounts.add(NULL_ACCOUNT_META);
        }
      } else {
        accounts.add(readPriceFeedMeta(pythOracle));
        accounts.add(NULL_ACCOUNT_META);
        accounts.add(NULL_ACCOUNT_META);
        accounts.add(NULL_ACCOUNT_META);
      }
    } else {
      accounts.add(NULL_ACCOUNT_META);
      accounts.add(NULL_ACCOUNT_META);
      accounts.add(NULL_ACCOUNT_META);
      accounts.add(readPriceFeedMeta(priceFeed));
    }
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
              "totalCollateral": %d
            }""",
        market.toBase58(),
        pubKey.toBase58(),
        jsonTokenName(),
        mint,
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
                "totalCollateral": %d,
                "priceFeed": "%s",
                "tokenInfo": "%s"
              }""",
          pubKey.toBase58(),
          jsonTokenName(),
          mint.toBase58(),
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
                  "totalCollateral": %d,
                  "priceFeed": "%s",
                  "maxAgePriceSeconds": %d,
                  "priceChain": %s,
                  "tokenInfo": "%s"
                }""",
            pubKey.toBase58(),
            jsonTokenName(),
            mint.toBase58(),
            totalCollateral,
            priceFeed().toBase58(),
            maxAgePriceSeconds(),
            KaminoCacheImpl.toJson(priceChains.priceChain()),
            encodedTokenInfo
        );
      } else {
        return String.format("""
                {
                  "reserve": "%s",
                  "tokenName": %s,
                  "mint": "%s",
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
            totalCollateral,
            priceFeed().toBase58(),
            maxAgePriceSeconds(),
            maxAgeTwapSeconds(),
            maxTwapDivergenceBps(),
            KaminoCacheImpl.toJson(priceChains.priceChain()),
            KaminoCacheImpl.toJson(twapChain),
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
                "totalCollateral": %d,
                "priceFeed": "%s"
              }""",
          pubKey.toBase58(),
          jsonTokenName(),
          mint.toBase58(),
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
                  "totalCollateral": %d,
                  "priceFeed": "%s",
                  "maxAgePriceSeconds": %d,
                  "priceChain": %s
                }""",
            pubKey.toBase58(),
            jsonTokenName(),
            mint.toBase58(),
            totalCollateral,
            priceFeed().toBase58(),
            maxAgePriceSeconds(),
            KaminoCacheImpl.toJson(priceChains.priceChain())
        );
      } else {
        return String.format("""
                {
                  "reserve": "%s",
                  "tokenName": %s,
                  "mint": "%s",
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
            totalCollateral,
            priceFeed().toBase58(),
            maxAgePriceSeconds(),
            maxAgeTwapSeconds(),
            maxTwapDivergenceBps(),
            KaminoCacheImpl.toJson(priceChains.priceChain()),
            KaminoCacheImpl.toJson(twapChain)
        );
      }
    }
  }

  enum ReserveChange {
    MARKET,
    MINT,
    TOKEN_NAME,
    TOTAL_COLLATERAL,
    MAX_AGE_PRICE_SECONDS,
    MAX_AGE_TWAP_SECONDS,
    MAX_TWAP_DIVERGENCE_BPS,
    PRICE_FEED,
    PRICE_CHAIN,
    TWAP_CHAIN
  }

  static boolean onlyCollateralChanged(final Set<ReserveChange> changes) {
    return changes.size() == 1 && changes.contains(ReserveChange.TOTAL_COLLATERAL);
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
          reserve, AccountMeta.createWrite(reserve),
          market,
          tokenName,
          mint,
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
        this.reserve = PublicKeyEncoding.parseBase58Encoded(ji);
      } else if (JsonIterator.fieldEquals("tokenName", buf, offset, len)) {
        this.tokenName = ji.readString();
      } else if (JsonIterator.fieldEquals("mint", buf, offset, len)) {
        this.mint = PublicKeyEncoding.parseBase58Encoded(ji);
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
