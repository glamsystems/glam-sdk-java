package systems.glam.services.integrations.kamino;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.encoding.ByteUtil;
import software.sava.idl.clients.core.gen.SerDeUtil;
import software.sava.idl.clients.kamino.KaminoAccounts;
import software.sava.idl.clients.kamino.lend.gen.types.*;
import software.sava.idl.clients.kamino.scope.entries.PriceChains;
import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.services.oracles.scope.MappingsContext;

import java.util.*;

import static software.sava.idl.clients.kamino.KaminoAccounts.NULL_KEY;

public record ReserveContext(long slot,
                             byte[] data,
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

  private static ReserveContext createContext(final long slot,
                                              final byte[] data,
                                              final PublicKey reserveKey,
                                              final PublicKey lendingMarketKey,
                                              final PublicKey mintKey,
                                              final long totalCollateral,
                                              final TokenInfo tokenInfo,
                                              final PriceChains priceChains) {
    final byte[] name = tokenInfo.name();
    final var tokenName = SerDeUtil.fixedLengthString(name);
    return new ReserveContext(
        slot,
        data,
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
    return createContext(
        accountInfo.context().slot(),
        accountInfo.pubKey(),
        accountInfo.data(),
        mappingsContextByPriceFeed
    );
  }

  static ReserveContext createContext(final PublicKey reserveKey,
                                      final byte[] data,
                                      final Map<PublicKey, MappingsContext> mappingsContextByPriceFeed) {
    return createContext(0L, reserveKey, data, mappingsContextByPriceFeed);
  }

  private static ReserveContext createContext(final long slot,
                                              final PublicKey reserveKey,
                                              final byte[] data,
                                              final Map<PublicKey, MappingsContext> mappingsContextByPriceFeed) {
    final var lendingMarketKey = PublicKey.readPubKey(data, Reserve.LENDING_MARKET_OFFSET);
    final var mintKey = PublicKey.readPubKey(data, Reserve.LIQUIDITY_OFFSET + ReserveLiquidity.MINT_PUBKEY_OFFSET);
    final var tokenInfo = TokenInfo.read(data, Reserve.CONFIG_OFFSET + ReserveConfig.TOKEN_INFO_OFFSET);
    final var scopeConfiguration = tokenInfo.scopeConfiguration();
    final var priceChains = readPriceChains(mintKey, scopeConfiguration, mappingsContextByPriceFeed);
    long totalCollateral = ByteUtil.getInt64LE(data, Reserve.COLLATERAL_OFFSET + ReserveCollateral.MINT_TOTAL_SUPPLY_OFFSET);
    return createContext(
        slot,
        data,
        reserveKey,
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
        data,
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
}
