package systems.glam.services.integrations.drift;

import software.sava.core.accounts.PublicKey;
import software.sava.core.encoding.ByteUtil;
import software.sava.core.rpc.Filter;
import software.sava.idl.clients.drift.DriftAccounts;
import software.sava.idl.clients.drift.gen.types.MarketStatus;
import software.sava.idl.clients.drift.gen.types.PerpMarket;
import software.sava.idl.clients.drift.gen.types.SpotMarket;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.services.rpc.AccountFetcher;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.System.Logger.Level.WARNING;
import static systems.glam.services.io.FileUtils.ACCOUNT_FILE_EXTENSION;

public interface DriftMarketCache {

  static CompletableFuture<DriftMarketCache> initCache(final Path driftCacheDirectory,
                                                       final DriftAccounts driftAccounts,
                                                       final RpcCaller rpcCaller,
                                                       final AccountFetcher accountFetcher) {
    final var driftProgram = driftAccounts.driftProgram();
    try {
      final var spotMarketsDirectory = driftCacheDirectory.resolve("spot_markets");
      final CompletableFuture<AtomicReferenceArray<DriftMarketContext>> spotMarketsFuture;
      if (Files.exists(spotMarketsDirectory)) {
        spotMarketsFuture = loadSpotMarkets(spotMarketsDirectory, rpcCaller, driftAccounts);
      } else {
        Files.createDirectories(spotMarketsDirectory);
        spotMarketsFuture = fetchSpotMarkets(spotMarketsDirectory, rpcCaller, driftProgram);
      }

      final var perpMarketsDirectory = driftCacheDirectory.resolve("perp_markets");
      final CompletableFuture<AtomicReferenceArray<DriftMarketContext>> perpMarketsFuture;
      if (Files.exists(perpMarketsDirectory)) {
        perpMarketsFuture = loadPerpMarkets(spotMarketsDirectory, rpcCaller, driftAccounts);
      } else {
        Files.createDirectories(perpMarketsDirectory);
        perpMarketsFuture = fetchPerpMarkets(perpMarketsDirectory, rpcCaller, driftProgram);
      }

      return spotMarketsFuture.thenCombine(perpMarketsFuture, (spotMarkets, perpMarkets) ->
          new DriftMarketCacheImpl(
              spotMarketsDirectory, perpMarketsDirectory,
              spotMarkets, perpMarkets,
              driftAccounts,
              accountFetcher
          )
      );
    } catch (final IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  private static CompletableFuture<AtomicReferenceArray<DriftMarketContext>> loadSpotMarkets(final Path marketsDirectory,
                                                                                             final RpcCaller rpcCaller,
                                                                                             final DriftAccounts driftAccounts) {
    final var driftProgram = driftAccounts.driftProgram();
    return loadMarkets(
        marketsDirectory,
        data -> {
          final var marketKey = PublicKey.readPubKey(data, SpotMarket.PUBKEY_OFFSET);
          final var oracle = PublicKey.readPubKey(data, SpotMarket.ORACLE_OFFSET);
          final int marketIndex = ByteUtil.getInt16LE(data, SpotMarket.MARKET_INDEX_OFFSET);
          final int poolId = data[SpotMarket.POOL_ID_OFFSET] & 0xFF;
          return DriftMarketContext.createContext(poolId, marketIndex, marketKey, oracle);
        },
        () -> fetchSpotMarkets(marketsDirectory, rpcCaller, driftProgram)
    );
  }

  private static CompletableFuture<AtomicReferenceArray<DriftMarketContext>> loadPerpMarkets(final Path marketsDirectory,
                                                                                             final RpcCaller rpcCaller,
                                                                                             final DriftAccounts driftAccounts) {
    final var driftProgram = driftAccounts.driftProgram();
    return loadMarkets(
        marketsDirectory,
        data -> {
          final var marketKey = PublicKey.readPubKey(data, PerpMarket.PUBKEY_OFFSET);
          final var oracle = PublicKey.readPubKey(data, PerpMarket.AMM_OFFSET);
          final int marketIndex = ByteUtil.getInt16LE(data, PerpMarket.MARKET_INDEX_OFFSET);
          final int poolId = data[PerpMarket.POOL_ID_OFFSET] & 0xFF;
          return DriftMarketContext.createContext(poolId, marketIndex, marketKey, oracle);
        },
        () -> fetchPerpMarkets(marketsDirectory, rpcCaller, driftProgram)
    );
  }

  private static CompletableFuture<AtomicReferenceArray<DriftMarketContext>> loadMarkets(final Path marketsDirectory,
                                                                                         final Function<byte[], DriftMarketContext> contextFactory,
                                                                                         final Supplier<CompletableFuture<AtomicReferenceArray<DriftMarketContext>>> fallback) {
    try (final var files = Files.list(marketsDirectory)) {
      final var datFiles = files
          .filter(p -> p.getFileName().toString().endsWith(ACCOUNT_FILE_EXTENSION))
          .toList();

      if (datFiles.isEmpty()) {
        return fallback.get();
      }

      final var marketsArray = new AtomicReferenceArray<DriftMarketContext>(datFiles.size() << 1);
      datFiles.parallelStream().forEach(datFile -> {
        try {
          final byte[] data = Files.readAllBytes(datFile);
          final var marketContext = contextFactory.apply(data);
          if (marketsArray.getAndSet(marketContext.marketIndex(), marketContext) != null) {
            throw new IllegalStateException("Duplicate market index " + marketContext.marketIndex());
          }
        } catch (final Exception ex) {
          DriftMarketCacheImpl.logger.log(WARNING, "Failed to load Drift Market from " + datFile, ex);
        }
      });
      return CompletableFuture.completedFuture(marketsArray);
    } catch (final IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  static List<Filter> spotMarketFilters() {
    return List.of(
        SpotMarket.SIZE_FILTER,
        SpotMarket.DISCRIMINATOR_FILTER,
        Filter.createMemCompFilter(SpotMarket.STATUS_OFFSET, MarketStatus.Active.write())
    );
  }

  private static CompletableFuture<AtomicReferenceArray<DriftMarketContext>> fetchSpotMarkets(final Path marketsDirectory,
                                                                                              final RpcCaller rpcCaller,
                                                                                              final PublicKey driftProgram) {
    return fetchMarkets(marketsDirectory, rpcCaller, spotMarketFilters(), driftProgram, DriftMarketContext::createSpotContext);
  }

  static List<Filter> perpMarketFilters() {
    return List.of(
        PerpMarket.SIZE_FILTER,
        PerpMarket.DISCRIMINATOR_FILTER,
        Filter.createMemCompFilter(PerpMarket.STATUS_OFFSET, MarketStatus.Active.write())
    );
  }

  private static CompletableFuture<AtomicReferenceArray<DriftMarketContext>> fetchPerpMarkets(final Path marketsDirectory,
                                                                                              final RpcCaller rpcCaller,
                                                                                              final PublicKey driftProgram) {
    return fetchMarkets(marketsDirectory, rpcCaller, perpMarketFilters(), driftProgram, DriftMarketContext::createPerpContext);
  }

  private static CompletableFuture<AtomicReferenceArray<DriftMarketContext>> fetchMarkets(final Path marketsDirectory,
                                                                                          final RpcCaller rpcCaller,
                                                                                          final List<Filter> filters,
                                                                                          final PublicKey driftProgram,
                                                                                          final Function<AccountInfo<byte[]>, DriftMarketContext> contextFactory) {
    final var fetchFuture = rpcCaller.courteousCall(
        rpcClient -> rpcClient.getProgramAccounts(driftProgram, filters),
        "rpcClient::getDriftSpotMarkets"
    );
    return fetchFuture.thenApplyAsync(accounts -> {
      final var marketsArray = new AtomicReferenceArray<DriftMarketContext>(accounts.size() << 1);
      accounts.parallelStream().forEach(accountInfo -> {
        try {
          final var marketContext = contextFactory.apply(accountInfo);
          final int marketIndex = marketContext.marketIndex();
          if (marketsArray.getAndSet(marketIndex, marketContext) != null) {
            throw new IllegalStateException("Duplicate market index " + marketIndex);
          }
          DriftMarketCacheImpl.writeMarketData(marketsDirectory, marketIndex, accountInfo.data());
        } catch (final Exception ex) {
          DriftMarketCacheImpl.logger.log(WARNING, "Failed to parse Drift Market account " + accountInfo.pubKey(), ex);
        }
      });
      return marketsArray;
    });
  }

  DriftAccounts driftAccounts();

  DriftMarketContext spotMarket(final int marketIndex);

  DriftMarketContext perpMarket(final int marketIndex);

  void refreshSpotMarket(final int marketIndex);

  void refreshPerpMarket(final int marketIndex);
}
