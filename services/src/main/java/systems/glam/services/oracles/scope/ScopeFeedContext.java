package systems.glam.services.oracles.scope;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.idl.clients.kamino.scope.entries.OracleEntry;
import software.sava.idl.clients.kamino.scope.gen.types.Configuration;
import software.sava.idl.clients.kamino.scope.gen.types.OracleMappings;
import software.sava.idl.clients.kamino.scope.gen.types.OracleType;
import software.sava.rpc.json.http.response.AccountInfo;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReferenceArray;

public record ScopeFeedContext(long slot, byte[] configurationData,
                               PublicKey configurationKey,
                               PublicKey oracleMappings,
                               PublicKey priceFeed, AccountMeta readPriceFeed,
                               AtomicReferenceArray<Map<PublicKey, ReserveContext>> reservesByIndex,
                               ConcurrentMap<PublicKey, ReserveContext[]> reservesByMint) {

  private static final Comparator<ReserveContext> RESERVE_CONTEXT_BY_LIQUIDITY = (a, b) -> Long.compareUnsigned(b.totalCollateral(), a.totalCollateral());

  static ScopeFeedContext createContext(final long slot,
                                        final byte[] configurationData,
                                        final PublicKey configurationKey,
                                        final PublicKey oracleMappings,
                                        final PublicKey priceFeed) {
    final var reservesByIndex = new AtomicReferenceArray<Map<PublicKey, ReserveContext>>(OracleMappings.PRICE_INFO_ACCOUNTS_LEN);
    final var reservesByMint = new ConcurrentHashMap<PublicKey, ReserveContext[]>();
    return new ScopeFeedContext(
        slot,
        configurationData.length > Configuration.PADDING_OFFSET ? Arrays.copyOfRange(configurationData, 0, Configuration.PADDING_OFFSET) : configurationData,
        configurationKey, oracleMappings, priceFeed, AccountMeta.createRead(priceFeed),
        reservesByIndex, reservesByMint
    );
  }

  static ScopeFeedContext createContext(final long slot,
                                        final byte[] configurationData,
                                        final PublicKey configurationKey) {
    final var oracleMappings = PublicKey.readPubKey(configurationData, Configuration.ORACLE_MAPPINGS_OFFSET);
    final var priceFeed = PublicKey.readPubKey(configurationData, Configuration.ORACLE_PRICES_OFFSET);
    return createContext(slot, configurationData, configurationKey, oracleMappings, priceFeed);
  }

  static ScopeFeedContext createContext(final PublicKey configurationKey, final byte[] configurationData) {
    return createContext(0, configurationData, configurationKey);
  }

  static ScopeFeedContext createContext(final AccountInfo<byte[]> accountInfo) {
    final long slot = accountInfo.context().slot();
    return createContext(slot, accountInfo.data(), accountInfo.pubKey());
  }

  static String configurationToJson(final long slot,
                                    final PublicKey configurationKey,
                                    final byte[] data) {
    return configurationToJson(
        slot,
        configurationKey,
        PublicKey.readPubKey(data, Configuration.ORACLE_MAPPINGS_OFFSET),
        PublicKey.readPubKey(data, Configuration.ORACLE_PRICES_OFFSET),
        data
    );
  }

  static String configurationToJson(final long slot,
                                    final PublicKey configurationKey,
                                    final PublicKey oracleMappings,
                                    final PublicKey priceFeed,
                                    final byte[] data) {
    return String.format("""
            {
             "slot": %d,
             "address": "%s",
             "admin": "%s",
             "oracleMappings": "%s",
             "oraclePrices": "%s",
             "tokensMetadata": "%s",
             "oracleTwaps": "%s",
             "adminCached": "%s"
            }""",
        slot, configurationKey,
        PublicKey.readPubKey(data, Configuration.ADMIN_OFFSET),
        oracleMappings,
        priceFeed,
        PublicKey.readPubKey(data, Configuration.TOKENS_METADATA_OFFSET),
        PublicKey.readPubKey(data, Configuration.ORACLE_TWAPS_OFFSET),
        PublicKey.readPubKey(data, Configuration.ADMIN_CACHED_OFFSET)
    );
  }

  public String toJson() {
    return configurationToJson(slot, configurationKey, oracleMappings, priceFeed, configurationData);
  }

  public boolean isStaleOrUnchanged(final long slot, final byte[] data) {
    return Long.compareUnsigned(slot, this.slot) <= 0 || Arrays.equals(
        this.configurationData, 0, Configuration.PADDING_OFFSET,
        data, 0, Configuration.PADDING_OFFSET
    );
  }

  Map<PublicKey, ReserveContext> reservesForIndex(final int index) {
    return reservesByIndex.get(index);
  }

  void resortReserves(final ReserveContext reserveContext) {
    final var key = reserveContext.pubKey();
    final var mint = reserveContext.mint();
    final var reservesForMint = reservesByMint.get(mint);
    if (reservesForMint == null) {
      reservesByMint.put(mint, new ReserveContext[]{reserveContext});
    } else {
      for (int i = 0; i < reservesForMint.length; ++i) {
        if (reservesForMint[i].pubKey().equals(key)) {
          final var newArray = new ReserveContext[reservesForMint.length];
          System.arraycopy(reservesForMint, 0, newArray, 0, reservesForMint.length);
          newArray[i] = reserveContext;
          Arrays.sort(newArray, RESERVE_CONTEXT_BY_LIQUIDITY);
          reservesByMint.put(mint, newArray);
          return;
        }
      }
      final var newArray = new ReserveContext[reservesForMint.length + 1];
      System.arraycopy(reservesForMint, 0, newArray, 0, reservesForMint.length);
      newArray[reservesForMint.length] = reserveContext;
      Arrays.sort(newArray, RESERVE_CONTEXT_BY_LIQUIDITY);
      reservesByMint.put(mint, newArray);
    }

    // Maintain consistent reference to latest ReserveContext
    indexReserveByIndex(reserveContext);
  }

  private void indexReserveByIndex(final ReserveContext reserveContext) {
    final var key = reserveContext.pubKey();
    for (final short index : reserveContext.priceChainIndexes()) {
      if (index < 0) {
        break;
      }
      var reservesForIndex = reservesByIndex.get(index);
      if (reservesForIndex == null) {
        reservesByIndex.set(index, Map.of(key, reserveContext));
        return;
      } else if (reservesForIndex.containsKey(key)) {
        if (reservesForIndex.size() == 1) {
          reservesByIndex.set(index, Map.of(key, reserveContext));
          return;
        }
      }

      final var newMap = new HashMap<>(reservesForIndex);
      newMap.put(key, reserveContext);
      reservesByIndex.set(index, newMap);
    }
  }

  void indexReserveContext(final ReserveContext reserveContext) {
    indexReserveByIndex(reserveContext);
    resortReserves(reserveContext);
  }

  int reIndexReserves(final Map<PublicKey, ReserveContext> reserveContexts, final MappingsContext mappingsContext) {
    int numChanged = 0;
    for (final var reserveContext : reserveContexts.values()) {
      if (reserveContext.priceFeed().equals(priceFeed)) {
        final var priceChains = mappingsContext.readPriceChains(reserveContext.mint(), reserveContext.scopeConfiguration());
        if (!reserveContext.priceChains().equals(priceChains)) {
          final var changed = reserveContext.withPriceChains(priceChains);
          removePreviousEntry(reserveContext);
          reserveContexts.put(reserveContext.pubKey(), changed);
          indexReserveContext(changed);
          ++numChanged;
        }
      }
    }
    return numChanged;
  }

  void removePreviousEntry(final ReserveContext previousContext) {
    final var reservePubKey = previousContext.pubKey();
    for (final short index : previousContext.priceChainIndexes()) {
      if (index < 0) {
        break;
      }
      reservesForIndex(index).remove(reservePubKey);
    }

    final var reservesForMint = this.reservesByMint.get(previousContext.mint());
    if (reservesForMint != null) {
      final int numReserves = reservesForMint.length;
      if (numReserves == 1) {
        if (reservesForMint[0].pubKey().equals(reservePubKey)) {
          this.reservesByMint.remove(previousContext.mint());
        }
      } else {
        for (int i = 0; i < reservesForMint.length; ++i) {
          if (reservesForMint[i].pubKey().equals(reservePubKey)) {
            final var newArray = new ReserveContext[reservesForMint.length - 1];
            System.arraycopy(reservesForMint, 0, newArray, 0, i);
            System.arraycopy(reservesForMint, i + 1, newArray, i, reservesForMint.length - i - 1);
            this.reservesByMint.put(previousContext.mint(), newArray);
            return;
          }
        }
      }
    }
  }

  FeedIndexes indexes(final PublicKey mint, final PublicKey oracle, final OracleType oracleType) {
    final var reservesForMint = this.reservesByMint.get(mint);
    if (reservesForMint == null || reservesForMint.length == 0) {
      return null;
    }

    final short[] indexes = new short[]{-1, -1, -1, -1};
    BigInteger liquidity = null;
    int i = 0;
    DONE:
    for (final var reserveContext : reservesForMint) {
      final var priceChains = reserveContext.priceChains();
      if (priceChains != null) {
        final var priceChain = priceChains.priceChain();
        for (final var scopeEntry : priceChain) {
          if (scopeEntry instanceof OracleEntry oracleEntry) {
            if (oracleEntry.oracleType() == oracleType && oracleEntry.oracle().equals(oracle)) {
              indexes[i] = (short) scopeEntry.index();
              final var reserveLiquidity = BigInteger.valueOf(reserveContext.availableLiquidity());
              liquidity = liquidity == null ? reserveLiquidity : liquidity.add(reserveLiquidity);
              if (++i == 4) {
                break DONE;
              }
            }
          } // else Handle nested OracleTypes, e.g., a MostRecentOf holding the desired type.
        }
      }
    }

    if (i == 0) {
      return null;
    } else {
      return new FeedIndexes(readPriceFeed, indexes, liquidity);
    }
  }
}
