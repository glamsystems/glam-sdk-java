package systems.glam.services.fulfillment.drfit;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.drift.DriftAccounts;
import software.sava.idl.clients.drift.DriftPDAs;
import software.sava.idl.clients.drift.gen.types.PerpMarket;
import software.sava.idl.clients.drift.gen.types.SpotMarket;
import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.services.pricing.AccountConsumer;
import systems.glam.services.pricing.AccountFetcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicReferenceArray;

import static java.lang.System.Logger.Level.WARNING;
import static software.sava.core.encoding.ByteUtil.getInt16LE;

final class DriftMarketCacheImpl implements DriftMarketCache, AccountConsumer {

  static final System.Logger logger = System.getLogger(DriftMarketCache.class.getName());

  private final Path spotMarketsDirectory;
  private final Path perpMarketsDirectory;
  private final AtomicReferenceArray<DriftMarketContext> spotMarkets;
  private final AtomicReferenceArray<DriftMarketContext> perpMarkets;
  private final DriftAccounts driftAccounts;
  private final AccountFetcher accountFetcher;
  private final Set<PublicKey> pendingAccounts;

  DriftMarketCacheImpl(final Path spotMarketsDirectory,
                       final Path perpMarketsDirectory,
                       final AtomicReferenceArray<DriftMarketContext> spotMarkets,
                       final AtomicReferenceArray<DriftMarketContext> perpMarkets,
                       final DriftAccounts driftAccounts,
                       final AccountFetcher accountFetcher) {
    this.spotMarketsDirectory = spotMarketsDirectory;
    this.perpMarketsDirectory = perpMarketsDirectory;
    this.spotMarkets = spotMarkets;
    this.perpMarkets = perpMarkets;
    this.driftAccounts = driftAccounts;
    this.accountFetcher = accountFetcher;
    this.pendingAccounts = new ConcurrentSkipListSet<>();
  }

  private void queueAccount(final PublicKey marketAccount) {
    if (pendingAccounts.add(marketAccount)) {
      accountFetcher.priorityQueue(marketAccount, this);
    }
  }

  @Override
  public DriftMarketContext spotMarket(final int marketIndex) {
    final var marketContext = spotMarkets.getOpaque(marketIndex);
    if (marketContext == null) {
      final var marketAccount = DriftPDAs.deriveSpotMarketAccount(driftAccounts, marketIndex).publicKey();
      queueAccount(marketAccount);
      return null;
    } else {
      return marketContext;
    }
  }

  @Override
  public DriftMarketContext perpMarket(final int marketIndex) {
    final var marketContext = perpMarkets.getOpaque(marketIndex);
    if (marketContext == null) {
      final var marketAccount = DriftPDAs.derivePerpMarketAccount(driftAccounts, marketIndex).publicKey();
      queueAccount(marketAccount);
      return null;
    } else {
      return marketContext;
    }
  }

  @Override
  public void refreshSpotMarket(final int marketIndex) {
    final var marketContext = spotMarkets.get(marketIndex);
    final var marketAccount = marketContext == null
        ? DriftPDAs.deriveSpotMarketAccount(driftAccounts, marketIndex).publicKey()
        : marketContext.market();
    queueAccount(marketAccount);
  }

  @Override
  public void refreshPerpMarket(final int marketIndex) {
    final var marketContext = perpMarkets.get(marketIndex);
    final var marketAccount = marketContext == null
        ? DriftPDAs.derivePerpMarketAccount(driftAccounts, marketIndex).publicKey()
        : marketContext.market();
    queueAccount(marketAccount);
  }

  @Override
  public void accept(final List<AccountInfo<byte[]>> accounts, final Map<PublicKey, AccountInfo<byte[]>> accountMap) {
    final var driftProgram = driftAccounts.driftProgram();
    for (final var accountInfo : accounts) {
      if (accountInfo != null && driftProgram.equals(accountInfo.owner())) {
        final var pubKey = accountInfo.pubKey();
        if (pendingAccounts.contains(pubKey)) {
          final byte[] data = accountInfo.data();
          if (SpotMarket.BYTES == data.length && SpotMarket.DISCRIMINATOR.equals(data, 0)) {
            final int marketIndex = getInt16LE(data, SpotMarket.MARKET_INDEX_OFFSET);
            final var oracle = PublicKey.readPubKey(data, SpotMarket.ORACLE_OFFSET);
            final var currentEntry = spotMarkets.get(marketIndex);
            if (currentEntry == null || !currentEntry.oracle().equals(oracle)) {
              perpMarkets.set(marketIndex, DriftMarketContext.createContext(marketIndex, pubKey, oracle));
              writeMarketData(spotMarketsDirectory, marketIndex, data);
            }
            pendingAccounts.remove(pubKey);
          } else if (PerpMarket.BYTES == data.length && PerpMarket.DISCRIMINATOR.equals(data, 0)) {
            final int marketIndex = getInt16LE(data, PerpMarket.MARKET_INDEX_OFFSET);
            final var oracle = PublicKey.readPubKey(accountInfo.data(), PerpMarket.AMM_OFFSET);
            final var currentEntry = perpMarkets.get(marketIndex);
            if (currentEntry == null || !currentEntry.oracle().equals(oracle)) {
              perpMarkets.set(marketIndex, DriftMarketContext.createContext(marketIndex, pubKey, oracle));
              writeMarketData(perpMarketsDirectory, marketIndex, data);
            }
            pendingAccounts.remove(pubKey);
          } else {
            logger.log(WARNING, "Unknown Drift Market account type " + pubKey);
          }
        }
      }
    }
  }

  static void writeMarketData(final Path directory, final int marketIndex, final byte[] data) {
    try {
      Files.write(directory.resolve(marketIndex + ".dat"), data);
    } catch (final IOException e) {
      logger.log(WARNING, "Failed to write Drift market account data", e);
    }
  }

  @Override
  public String toString() {
    return "DriftMarketCacheImpl{" +
        "driftAccounts=" + driftAccounts +
        ", perpMarketsDirectory=" + perpMarketsDirectory +
        ", spotMarketsDirectory=" + spotMarketsDirectory +
        '}';
  }
}
