package systems.glam.services.integrations.drift;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.drift.DriftAccounts;
import software.sava.idl.clients.drift.DriftPDAs;
import software.sava.idl.clients.drift.gen.types.MarketStatus;
import software.sava.idl.clients.drift.gen.types.PerpMarket;
import software.sava.idl.clients.drift.gen.types.SpotMarket;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import systems.glam.services.rpc.AccountConsumer;
import systems.glam.services.rpc.AccountFetcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Consumer;

import static java.lang.System.Logger.Level.WARNING;
import static software.sava.core.encoding.ByteUtil.getInt16LE;
import static systems.glam.services.io.FileUtils.ACCOUNT_FILE_EXTENSION;

final class DriftMarketCacheImpl implements DriftMarketCache, AccountConsumer, Consumer<AccountInfo<byte[]>> {

  static final System.Logger logger = System.getLogger(DriftMarketCache.class.getName());

  private final Path spotMarketsDirectory;
  private final Path perpMarketsDirectory;
  private final AtomicReferenceArray<DriftSpotMarketContext> spotMarkets;
  private final AtomicReferenceArray<DriftPerpMarketContext> perpMarkets;
  private final DriftAccounts driftAccounts;
  private final AccountFetcher accountFetcher;
  private final Set<PublicKey> pendingAccounts;
  private final Map<PublicKey, DriftListener> criticalEventListeners;
  private final Map<PublicKey, Map<PublicKey, DriftListener>> criticalSpotMarketListeners;
  private final Map<PublicKey, Map<PublicKey, DriftListener>> criticalPerpMarketListeners;

  DriftMarketCacheImpl(final Path spotMarketsDirectory,
                       final Path perpMarketsDirectory,
                       final AtomicReferenceArray<DriftSpotMarketContext> spotMarkets,
                       final AtomicReferenceArray<DriftPerpMarketContext> perpMarkets,
                       final DriftAccounts driftAccounts,
                       final AccountFetcher accountFetcher) {
    this.spotMarketsDirectory = spotMarketsDirectory;
    this.perpMarketsDirectory = perpMarketsDirectory;
    this.spotMarkets = spotMarkets;
    this.perpMarkets = perpMarkets;
    this.driftAccounts = driftAccounts;
    this.accountFetcher = accountFetcher;
    this.pendingAccounts = ConcurrentHashMap.newKeySet(64);
    this.criticalEventListeners = new ConcurrentHashMap<>();
    this.criticalSpotMarketListeners = new ConcurrentHashMap<>();
    this.criticalPerpMarketListeners = new ConcurrentHashMap<>();
  }

  @Override
  public DriftAccounts driftAccounts() {
    return driftAccounts;
  }

  private void queueAccount(final PublicKey marketAccount) {
    if (pendingAccounts.add(marketAccount)) {
      accountFetcher.priorityQueue(marketAccount, this);
    }
  }

  @Override
  public DriftSpotMarketContext spotMarket(final int marketIndex) {
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
  public DriftPerpMarketContext perpMarket(final int marketIndex) {
    final var marketContext = perpMarkets.getOpaque(marketIndex);
    if (marketContext == null) {
      final var marketAccount = DriftPDAs.derivePerpMarketAccount(driftAccounts, marketIndex).publicKey();
      queueAccount(marketAccount);
      return null;
    } else {
      return marketContext;
    }
  }

  private PublicKey spotMarketKey(final int marketIndex) {
    final var marketContext = spotMarkets.get(marketIndex);
    return marketContext == null
        ? DriftPDAs.deriveSpotMarketAccount(driftAccounts, marketIndex).publicKey()
        : marketContext.readMarket().publicKey();
  }

  @Override
  public void refreshSpotMarket(final int marketIndex) {
    queueAccount(spotMarketKey(marketIndex));
  }

  private PublicKey perpMarketKey(final int marketIndex) {
    final var marketContext = perpMarkets.get(marketIndex);
    return marketContext == null
        ? DriftPDAs.derivePerpMarketAccount(driftAccounts, marketIndex).publicKey()
        : marketContext.readMarket().publicKey();
  }

  @Override
  public void refreshPerpMarket(final int marketIndex) {
    queueAccount(perpMarketKey(marketIndex));
  }

  @Override
  public void subscribeToCriticalMarketChanges(final DriftListener listener) {
    criticalEventListeners.put(listener.key(), listener);
  }

  @Override
  public void unSubscribeToCriticalMarketChanges(final DriftListener listener) {
    criticalEventListeners.remove(listener.key());
  }

  @Override
  public void subscribeToCriticalPerpMarketChanges(final PublicKey market, final DriftListener listener) {
    final var listeners = this.criticalPerpMarketListeners.computeIfAbsent(market, _ -> new ConcurrentHashMap<>());
    listeners.put(listener.key(), listener);
  }

  @Override
  public void unSubscribeToCriticalPerpMarketChanges(final PublicKey market, final DriftListener listener) {
    final var listeners = this.criticalPerpMarketListeners.get(market);
    if (listeners != null) {
      listeners.remove(listener.key());
    }
  }

  @Override
  public void subscribeToCriticalPerpMarketChanges(final int marketIndex, final DriftListener listener) {
    subscribeToCriticalPerpMarketChanges(perpMarketKey(marketIndex), listener);
  }

  @Override
  public void unSubscribeToCriticalPerpMarketChanges(final int marketIndex, final DriftListener listener) {
    unSubscribeToCriticalPerpMarketChanges(perpMarketKey(marketIndex), listener);
  }

  @Override
  public void subscribeToCriticalSpotMarketChanges(final PublicKey market, final DriftListener listener) {
    final var listeners = this.criticalSpotMarketListeners.computeIfAbsent(market, _ -> new ConcurrentHashMap<>());
    listeners.put(listener.key(), listener);
  }

  @Override
  public void unSubscribeToCriticalSpotMarketChanges(final PublicKey market, final DriftListener listener) {
    final var listeners = this.criticalSpotMarketListeners.get(market);
    if (listeners != null) {
      listeners.remove(listener.key());
    }
  }

  @Override
  public void subscribeToCriticalSpotMarketChanges(final int marketIndex, final DriftListener listener) {
    subscribeToCriticalSpotMarketChanges(spotMarketKey(marketIndex), listener);
  }

  @Override
  public void unSubscribeToCriticalSpotMarketChanges(final int marketIndex, final DriftListener listener) {
    unSubscribeToCriticalSpotMarketChanges(spotMarketKey(marketIndex), listener);
  }

  static void writeMarketData(final Path directory, final int marketIndex, final byte[] data) {
    try {
      Files.write(directory.resolve(marketIndex + ACCOUNT_FILE_EXTENSION), data);
    } catch (final IOException e) {
      logger.log(WARNING, "Failed to write Drift market account data", e);
    }
  }

  static void writeMarketData(final Path directory,
                              final int marketIndex,
                              final byte[] data,
                              final MarketStatus status) {
    if (status == MarketStatus.Delisted) {
      try {
        Files.deleteIfExists(directory.resolve(marketIndex + ACCOUNT_FILE_EXTENSION));
      } catch (final IOException e) {
        logger.log(WARNING, "Failed to delete Drift market account data", e);
      }
    } else {
      writeMarketData(directory, marketIndex, data);
    }
  }

  @Override
  public void run() {
    // TODO: Add defensive polling if websocket disconnected.
  }

  @Override
  public void accept(final List<AccountInfo<byte[]>> accounts, final Map<PublicKey, AccountInfo<byte[]>> accountMap) {
    final var driftProgram = driftAccounts.driftProgram();
    for (final var accountInfo : accounts) {
      if (!AccountFetcher.isNull(accountInfo) && driftProgram.equals(accountInfo.owner())) {
        accept(accountInfo);
      }
    }
  }

  @Override
  public void accept(final AccountInfo<byte[]> accountInfo) {
    final var pubKey = accountInfo.pubKey();
    final byte[] data = accountInfo.data();
    if (SpotMarket.BYTES == data.length && SpotMarket.DISCRIMINATOR.equals(data, 0)) {
      pendingAccounts.remove(pubKey);
      final int marketIndex = getInt16LE(data, SpotMarket.MARKET_INDEX_OFFSET);
      DriftSpotMarketContext spotMarketContext = null;
      DriftSpotMarketContext witness;
      for (; ; ) {
        witness = spotMarkets.get(marketIndex);
        if (witness == null) {
          if (spotMarketContext == null) {
            spotMarketContext = DriftSpotMarketContext.createContext(accountInfo);
          }
          if (spotMarkets.compareAndSet(marketIndex, null, spotMarketContext)) {
            writeMarketData(spotMarketsDirectory, marketIndex, data, spotMarketContext.spotMarket().status());
            return;
          }
        } else if (spotMarketContext == null) {
          spotMarketContext = witness.createContextFrom(accountInfo);
          if (spotMarketContext == null) {
            return;
          } else if (spotMarkets.compareAndSet(marketIndex, witness, spotMarketContext)) {
            break;
          }
        } else if (Long.compareUnsigned(spotMarketContext.slot(), witness.slot()) <= 0) {
          return;
        } else if (spotMarkets.compareAndSet(marketIndex, witness, spotMarketContext)) {
          break;
        }
      }

      final var spotMarket = spotMarketContext.spotMarket();
      final var criticalChanges = witness.criticalChanges(spotMarket);
      if (criticalChanges != null) {
        for (final var listener : this.criticalEventListeners.values()) {
          listener.onCriticalSpotMarketChange(criticalChanges, witness, spotMarketContext);
        }
        final var listeners = this.criticalSpotMarketListeners.get(spotMarket.pubkey());
        if (listeners != null) {
          for (final var listener : listeners.values()) {
            listener.onCriticalSpotMarketChange(criticalChanges, witness, spotMarketContext);
          }
        }
        writeMarketData(spotMarketsDirectory, marketIndex, data, spotMarket.status());
      }
    } else if (PerpMarket.BYTES == data.length && PerpMarket.DISCRIMINATOR.equals(data, 0)) {
      pendingAccounts.remove(pubKey);
      final int marketIndex = getInt16LE(data, PerpMarket.MARKET_INDEX_OFFSET);
      DriftPerpMarketContext perpMarketContext = null;
      DriftPerpMarketContext witness;
      for (; ; ) {
        witness = perpMarkets.get(marketIndex);
        if (witness == null) {
          if (perpMarketContext == null) {
            perpMarketContext = DriftPerpMarketContext.createContext(accountInfo);
          }
          if (perpMarkets.compareAndSet(marketIndex, null, perpMarketContext)) {
            writeMarketData(perpMarketsDirectory, marketIndex, data, perpMarketContext.perpMarket().status());
            return;
          }
        } else if (perpMarketContext == null) {
          perpMarketContext = witness.createContextFrom(accountInfo);
          if (perpMarketContext == null) {
            return;
          } else if (perpMarkets.compareAndSet(marketIndex, witness, perpMarketContext)) {
            break;
          }
        } else if (Long.compareUnsigned(perpMarketContext.slot(), witness.slot()) <= 0) {
          return;
        } else if (perpMarkets.compareAndSet(marketIndex, witness, perpMarketContext)) {
          break;
        }
      }

      final var perpMarket = perpMarketContext.perpMarket();
      final var criticalChanges = witness.criticalChanges(perpMarket);
      if (criticalChanges != null) {
        for (final var listener : this.criticalEventListeners.values()) {
          listener.onCriticalPerpMarketChange(criticalChanges, witness, perpMarketContext);
        }
        final var listeners = this.criticalPerpMarketListeners.get(perpMarket.pubkey());
        if (listeners != null) {
          for (final var listener : listeners.values()) {
            listener.onCriticalPerpMarketChange(criticalChanges, witness, perpMarketContext);
          }
        }
        writeMarketData(perpMarketsDirectory, marketIndex, data, perpMarket.status());
      }
    } else {
      logger.log(WARNING, "Unknown Drift Market account type " + pubKey);
    }
  }

  @Override
  public void subscribe(final SolanaRpcWebsocket websocket) {
    websocket.programSubscribe(
        driftAccounts.driftProgram(),
        List.of(
            SpotMarket.DISCRIMINATOR_FILTER,
            SpotMarket.SIZE_FILTER
        ),
        this
    );
    websocket.programSubscribe(
        driftAccounts.driftProgram(),
        List.of(
            PerpMarket.DISCRIMINATOR_FILTER,
            PerpMarket.SIZE_FILTER
        ),
        this
    );
  }
}
