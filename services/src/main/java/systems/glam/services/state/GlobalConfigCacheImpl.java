package systems.glam.services.state;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import systems.glam.sdk.idl.programs.glam.config.gen.types.GlobalConfig;
import systems.glam.sdk.idl.programs.glam.config.gen.types.OracleSource;
import systems.glam.services.mints.AssetMetaContext;
import systems.glam.services.mints.MintCache;
import systems.glam.services.mints.MintContext;
import systems.glam.services.rpc.AccountConsumer;
import systems.glam.services.rpc.AccountFetcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import static java.lang.System.Logger.Level.*;

final class GlobalConfigCacheImpl implements GlobalConfigCache, Consumer<AccountInfo<byte[]>>, AccountConsumer {

  private static final System.Logger logger = System.getLogger(GlobalConfigCache.class.getName());

  record GlobalConfigUpdate(long slot, AssetMetaContext[] assetMetaContexts, byte[] data) {

    public AssetMetaContext get(final int index) {
      return index < assetMetaContexts.length ? assetMetaContexts[index] : null;
    }
  }

  private final Path globalConfigFilePath;
  private final PublicKey configProgram;
  private final PublicKey globalConfigKey;
  private final SolanaAccounts solanaAccounts;
  private final MintCache mintCache;
  private final AccountFetcher accountFetcher;
  private final long fetchDelayNanos;
  private final Map<PublicKey, Set<PublicKey>> stateAccountsThatNeedAssetMeta;
  private final ReentrantReadWriteLock.ReadLock readLock;
  private final ReentrantReadWriteLock.WriteLock writeLock;
  private final Condition invalidGlobalConfig;
  private final Condition newAssetMeta;

  volatile GlobalConfigUpdate globalConfigUpdate;
  volatile Map<PublicKey, AssetMetaContext[]> assetMetaMap;

  GlobalConfigCacheImpl(final Path globalConfigFilePath,
                        final PublicKey configProgram,
                        final PublicKey globalConfigKey,
                        final SolanaAccounts solanaAccounts,
                        final MintCache mintCache,
                        final AccountFetcher accountFetcher,
                        final Duration fetchDelay,
                        final GlobalConfigUpdate globalConfigUpdate,
                        final Map<PublicKey, AssetMetaContext[]> assetMetaMap) {
    this.globalConfigFilePath = globalConfigFilePath;
    this.configProgram = configProgram;
    this.globalConfigKey = globalConfigKey;
    this.solanaAccounts = solanaAccounts;
    this.mintCache = mintCache;
    this.accountFetcher = accountFetcher;
    this.fetchDelayNanos = fetchDelay.toNanos();
    this.stateAccountsThatNeedAssetMeta = new ConcurrentHashMap<>();
    final var lock = new ReentrantReadWriteLock();
    this.readLock = lock.readLock();
    this.writeLock = lock.writeLock();
    this.invalidGlobalConfig = writeLock.newCondition();
    this.newAssetMeta = writeLock.newCondition();
    this.globalConfigUpdate = globalConfigUpdate;
    this.assetMetaMap = assetMetaMap;
  }

  @Override
  public AssetMetaContext watchForMint(final PublicKey mint, final PublicKey stateAccount) {
    readLock.lock();
    try {
      final var assetMetaMap = this.assetMetaMap;
      final var assetMetaEntries = assetMetaMap.get(mint);
      if (assetMetaEntries == null) {
        stateAccountsThatNeedAssetMeta.computeIfAbsent(mint, _ -> new ConcurrentSkipListSet<>()).add(stateAccount);
        return null;
      } else {
        return assetMetaEntries[0];
      }
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public Set<PublicKey> retryStateAccounts() {
    final var assetMetaMap = this.assetMetaMap;
    Set<PublicKey> stateAccounts = null;
    final var iterator = stateAccountsThatNeedAssetMeta.entrySet().iterator();
    while (iterator.hasNext()) {
      final var entry = iterator.next();
      if (assetMetaMap.containsKey(entry.getKey())) {
        if (stateAccounts == null) {
          stateAccounts = entry.getValue();
        } else {
          stateAccounts.addAll(entry.getValue());
        }
        iterator.remove();
      }
    }
    return stateAccounts == null ? Set.of() : stateAccounts;
  }

  GlobalConfigUpdate globalConfigUpdate() {
    return globalConfigUpdate;
  }

  @Override
  public AssetMetaContext getByIndex(final int index) {
    readLock.lock();
    try {
      final var globalConfig = globalConfigUpdate;
      return globalConfig.get(index);
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public AssetMetaContext topPriorityForMint(final PublicKey mint) {
    readLock.lock();
    try {
      final var assetMetaMap = this.assetMetaMap;
      final var assetMetaEntries = assetMetaMap.get(mint);
      return assetMetaEntries == null ? null : assetMetaEntries[0];
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public AssetMetaContext solAssetMeta() {
    return topPriorityForMint(solanaAccounts.wrappedSolTokenMint());
  }

  private AssetMetaContext topPriorityForMintChecked(final PublicKey mint, final int mintDecimals) {
    final AssetMetaContext assetMeta;
    readLock.lock();
    try {
      final var assetMetaMap = this.assetMetaMap;
      final var assetMetaEntries = assetMetaMap.get(mint);
      if (assetMetaEntries == null) {
        return null;
      }
      assetMeta = assetMetaEntries[0];
      if (mintDecimals == assetMeta.decimals()) {
        return assetMeta;
      }
    } finally {
      readLock.unlock();
    }

    if (this.globalConfigUpdate == null || this.assetMetaMap == null) {
      return null;
    }
    writeLock.lock();
    try {
      if (this.globalConfigUpdate == null || this.assetMetaMap == null) {
        return null;
      }
      this.globalConfigUpdate = null;
      this.assetMetaMap = null;
      this.invalidGlobalConfig.signalAll();
      final var msg = String.format("""
              {
               "event": "GlobalConfig decimals for Asset does not match Mint",
               "mintDecimals": %d,
               "entry": %s
              """,
          mintDecimals, assetMeta.toJson()
      );
      logger.log(ERROR, msg);
      // TODO: Trigger alert and exit.
      throw new IllegalStateException(msg);
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public AssetMetaContext topPriorityForMintChecked(final PublicKey mint) {
    final var mintContext = mintCache.get(mint);
    if (mintContext == null) {
      return null;
    } else {
      return topPriorityForMintChecked(mint, mintContext.decimals());
    }
  }

  @Override
  public AssetMetaContext topPriorityForMintChecked(final MintContext mintContext) {
    return topPriorityForMintChecked(mintContext.mint(), mintContext.decimals());
  }

  @Override
  public CompletableFuture<Void> initCache() {
    return accountFetcher.priorityQueue(List.of(globalConfigKey))
        .thenAcceptAsync(result -> this.accept(result.accounts(), result.accountMap()));
  }

  @Override
  public void run() {
    try {
      for (long remainingNanos; ; ) {
        accountFetcher.priorityQueue(globalConfigKey, this);
        writeLock.lock();
        try {
          for (remainingNanos = fetchDelayNanos; ; ) {
            remainingNanos = invalidGlobalConfig.awaitNanos(remainingNanos);
            if (this.globalConfigUpdate == null || this.assetMetaMap == null) {
              return;
            } else if (remainingNanos <= 0) {
              break;
            }
          }
        } finally {
          writeLock.unlock();
        }
      }
    } catch (final InterruptedException e) {
      // exit
    } catch (final Exception e) {
      logger.log(ERROR, "Error queuing global config fetch", e);
    }
  }

  static Map<PublicKey, AssetMetaContext[]> createMapChecked(final long slot,
                                                             final AssetMetaContext[] previousAssetMetaContexts,
                                                             final Map<PublicKey, AssetMetaContext[]> previousMetaMap,
                                                             final AssetMetaContext[] assetMetaContexts,
                                                             final MintCache mintCache) {
    if (previousAssetMetaContexts.length > assetMetaContexts.length) {
      final var msg = String.format("""
              {
               "event": "GlobalConfig Oracle Removed",
               "slot": %s,
               "previousLen": %d,
               "newLen": %d,
              }
              """,
          Long.toUnsignedString(slot), previousAssetMetaContexts.length, assetMetaContexts.length
      );
      logger.log(ERROR, msg);
      // TODO: Trigger alert and exit.
      return null;
    }

    final var previousOracleSourceMap = HashMap.<PublicKey, OracleSource>newHashMap(previousAssetMetaContexts.length);
    for (int i = 0; i < previousAssetMetaContexts.length; ++i) {
      final var previous = previousAssetMetaContexts[i];
      final var current = assetMetaContexts[i];
      if (previous.asset().equals(current.asset()) && previous.oracle().equals(current.oracle())) {
        if (previous.decimals() != current.decimals()) {
          final var msg = String.format("""
                  {
                   "event": "Inconsistent Asset Decimals Across GlobalConfig's",
                   "slot": %s,
                   "index": %d,
                   "previous": %s,
                   "new": %s
                  }
                  """,
              Long.toUnsignedString(slot),
              i,
              previous.toJson(), current.toJson()
          );
          logger.log(ERROR, msg);
          // TODO: Trigger alert and exit.
          return null;
        } else if (previous.oracleSource() != current.oracleSource()) {
          final var msg = String.format("""
                  {
                   "event": "Inconsistent Asset OracleSource Across GlobalConfig's",
                   "slot": %s,
                   "index": %d,
                   "previous": %s,
                   "new": %s
                  }
                  """,
              Long.toUnsignedString(slot),
              i,
              previous.toJson(), current.toJson()
          );
          logger.log(ERROR, msg);
          // TODO: Trigger alert and exit.
          return null;
        }
        // Consistent OracleSource types are checked below.
        if (previous.priority() != current.priority() || previous.maxAgeSeconds() != current.maxAgeSeconds()) {
          logger.log(INFO, String.format("""
                      {
                       "event": "Oracle Configuration Change",
                       "slot": %s,
                       "index": %d,
                       "previous": %s,
                       "new": %s
                      """,
                  Long.toUnsignedString(slot), i,
                  previous.toJson(), current.toJson()
              )
          );
        }
      } else if (previous.priority() < 0) {
        final var msg = String.format("""
                {
                 "event": "GlobalConfig Oracle Entry Rotation",
                 "slot": %s,
                 "index": %d,
                 "previous": %s,
                 "new": %s
                }""",
            Long.toUnsignedString(slot), i, previous.toJson(), current.toJson()
        );
        logger.log(INFO, msg);
      } else {
        final var msg = String.format("""
                {
                 "event": "Unexpected GlobalConfig Oracle Change",
                 "slot": %s,
                 "index": %d,
                 "previous": %s,
                 "new": %s
                }
                """,
            Long.toUnsignedString(slot), i,
            previous.toJson(), current.toJson()
        );
        logger.log(ERROR, msg);
        // TODO: Trigger alert and exit.
        return null;
      }

      previousOracleSourceMap.put(previous.oracle(), previous.oracleSource());
    }

    if (assetMetaContexts.length > previousAssetMetaContexts.length) {
      for (int i = previousAssetMetaContexts.length; i < assetMetaContexts.length; ++i) {
        logger.log(INFO, String.format("""
                    {
                     "event": "New GlobalConfig Oracle Entry",
                     "slot": %s,
                     "index": %d,
                     "entry": %s
                    }
                    """,
                Long.toUnsignedString(slot), i, assetMetaContexts[i].toJson()
            )
        );
      }
    }

    final var assetMetaMap = createMapChecked(slot, assetMetaContexts, previousOracleSourceMap, mintCache);
    if (assetMetaMap == null) {
      return null;
    }

    for (final var entry : assetMetaMap.entrySet()) {
      final var mint = entry.getKey();
      final var assetMetas = entry.getValue();
      final var previousMetas = previousMetaMap.get(mint);
      if (previousMetas != null) {
        final int expectedDecimals = assetMetas[0].decimals();
        final int previousDecimals = previousMetas[0].decimals();
        if (expectedDecimals != previousDecimals) {
          final var msg = String.format("""
                  {
                   "event": "Inconsistent Asset Decimals Across GlobalConfig's",
                   "slot": %s,
                   "previous": %s,
                   "new": %s
                  }
                  """,
              Long.toUnsignedString(slot),
              previousMetas[0].toJson(), assetMetas[0].toJson()
          );
          logger.log(ERROR, msg);
          // TODO: Trigger alert and exit.
          return null;
        }
      }
    }
    return assetMetaMap;
  }

  private static boolean validOracleSource(final AssetMetaContext assetMeta) {
    return switch (assetMeta.oracleSource()) {
      case NotSet, BaseAsset, QuoteAsset, Prelaunch, Pyth, Pyth1K, Pyth1M, PythStableCoin, Switchboard -> false;
      case PythPull, Pyth1KPull, Pyth1MPull, PythStableCoinPull, SwitchboardOnDemand, PythLazer, PythLazer1K,
           PythLazer1M, PythLazerStableCoin, LstPoolState, MarinadeState, ChainlinkRWA -> true;
    };
  }

  static Map<PublicKey, AssetMetaContext[]> createMapChecked(final long slot,
                                                             final AssetMetaContext[] assetMetaContexts,
                                                             final MintCache mintCache) {
    return createMapChecked(slot, assetMetaContexts, Map.of(), mintCache);
  }

  static Map<PublicKey, AssetMetaContext[]> createMapChecked(final long slot,
                                                             final AssetMetaContext[] assetMetaContexts,
                                                             final Map<PublicKey, OracleSource> previousOracleSourceMap,
                                                             final MintCache mintCache) {
    final var distinctOracleSource = HashMap.<PublicKey, AssetMetaContext>newHashMap(assetMetaContexts.length << 2);
    final var assetMetaMap = HashMap.<PublicKey, AssetMetaContext[]>newHashMap(assetMetaContexts.length);
    for (int i = 0; i < assetMetaContexts.length; ++i) {
      final var assetMeta = assetMetaContexts[i];
      final var mintContext = mintCache.get(assetMeta.asset());
      if (mintContext != null && mintContext.decimals() != assetMeta.decimals()) {
        final var msg = String.format("""
                {
                 "event": "GlobalConfig Asset Decimals Does Not Match Mint",
                 "slot": %s,
                 "mintDecimals": %d,
                 "index": %d,
                 "entry": %s
                """,
            Long.toUnsignedString(slot), mintContext.decimals(), i, assetMeta.toJson()
        );
        logger.log(ERROR, msg);
        // TODO: Trigger alert and exit.
        return null;
      }

      if (!validOracleSource(assetMeta)) {
        final var msg = String.format("""
                {
                 "event": "GlobalConfig Invalid OracleSource",
                 "slot": %s,
                 "index": %d,
                 "entry": %s
                """,
            Long.toUnsignedString(slot), i, assetMeta.toJson()
        );
        logger.log(ERROR, msg);
        // TODO: Trigger alert and exit.
        return null;
      }
      final var mint = assetMeta.asset();
      final var oracle = assetMeta.oracle();
      final var oracleSource = assetMeta.oracleSource();

      final var previousOracleSource = previousOracleSourceMap.get(oracle);
      if (previousOracleSource != null && !previousOracleSource.equals(oracleSource)) {
        final var msg = String.format("""
                {
                 "event": "Inconsistent OracleSource Across Configs",
                 "slot": %s,
                 "previousSource": "%s",
                 "index": %d,
                 "b": %s
                }
                """,
            Long.toUnsignedString(slot),
            previousOracleSource,
            i, assetMeta.toJson()
        );
        logger.log(ERROR, msg);
        // TODO: Trigger alert and exit.
        return null;
      }

      final var otherMeta = distinctOracleSource.put(oracle, assetMeta);
      if (otherMeta != null && !otherMeta.oracleSource().equals(oracleSource)) {
        final var msg = String.format("""
                {
                 "event": "Inconsistent OracleSource Within GlobalConfig",
                 "slot": %s,
                 "a": %s,
                 "b": %s
                }
                """,
            Long.toUnsignedString(slot),
            otherMeta.toJson(), assetMeta.toJson()
        );
        logger.log(ERROR, msg);
        // TODO: Trigger alert and exit.
        return null;
      }

      final var entries = assetMetaMap.get(mint);
      if (entries == null) {
        assetMetaMap.put(mint, new AssetMetaContext[]{assetMeta});
      } else {
        for (final var entry : entries) {
          if (entry.oracle().equals(oracle)) {
            final var msg = String.format("""
                    {
                     "event": "Duplicate Oracle For Asset",
                     "slot": %s,
                     "a": %s,
                     "b": %s
                    }""",
                Long.toUnsignedString(slot),
                entry.toJson(), assetMeta.toJson()
            );
            logger.log(ERROR, msg);
            // TODO: Trigger alert and exit.
            return null;
          }
        }

        final int len = entries.length;
        final var newEntries = Arrays.copyOf(entries, len + 1);
        newEntries[len] = assetMeta;
        assetMetaMap.put(mint, newEntries);
      }
    }

    for (final var assetMetas : assetMetaMap.values()) {
      if (assetMetas.length > 1) {
        Arrays.sort(assetMetas);
        final int expectedDecimals = assetMetas[0].decimals();
        for (int i = 1; i < assetMetas.length; ++i) {
          final var assetMeta = assetMetas[i];
          if (assetMeta.decimals() != expectedDecimals) {
            final var msg = String.format("""
                    {
                     "event": "Inconsistent Asset Decimals Within Config",
                     "slot": %s,
                     "a": %s,
                     "b": %s
                    }
                    """,
                Long.toUnsignedString(slot),
                assetMetas[0].toJson(), assetMeta.toJson()
            );
            logger.log(ERROR, msg);
            // TODO: Trigger alert and exit.
            return null;
          }
        }
      }
    }

    return assetMetaMap;
  }

  static boolean checkAccount(final PublicKey expectedOwner, final PublicKey owner,
                              final long slot, final PublicKey account, final byte[] data) {
    if (GlobalConfig.DISCRIMINATOR.equals(data, 0) && owner.equals(expectedOwner)) {
      return true;
    } else {
      final var msg = String.format("""
              {
               "event": "Invalid GlobalConfig Account",
               "slot": %s,
               "program": "%s",
               "account": "%s",
               "data": "%s"
              }""",
          Long.toUnsignedString(slot),
          owner.toBase58(),
          account.toBase58(),
          Base64.getEncoder().encodeToString(data)
      );
      logger.log(WARNING, msg);
      return false;
    }
  }

  @Override
  public void subscribe(final SolanaRpcWebsocket websocket) {
    websocket.accountSubscribe(globalConfigKey, this);
  }

  @Override
  public void accept(final List<AccountInfo<byte[]>> accounts, final Map<PublicKey, AccountInfo<byte[]>> accountMap) {
    final var globalConfigAccountInfo = accountMap.get(globalConfigKey);
    if (!AccountFetcher.isNull(globalConfigAccountInfo)) {
      accept(globalConfigAccountInfo);
    }
    final var assetMap = this.assetMetaMap;
    for (final var accountInfo : accounts) {
      if (accountInfo != null) {
        final var key = accountInfo.pubKey();
        if (assetMap.containsKey(key) && mintCache.get(key) == null) {
          final var mintContext = mintCache.setGet(MintContext.createContext(solanaAccounts, accountInfo));
          topPriorityForMintChecked(mintContext);
        }
      }
    }
  }

  @Override
  public void accept(final AccountInfo<byte[]> accountInfo) {
    if (this.assetMetaMap == null || this.globalConfigUpdate == null) {
      return;
    }
    writeLock.lock();
    try {
      if (this.assetMetaMap == null || this.globalConfigUpdate == null) {
        return;
      }
      final byte[] data = accountInfo.data();
      final long slot = accountInfo.context().slot();
      if (checkAccount(configProgram, accountInfo.owner(), slot, accountInfo.pubKey(), data)) {
        final var previousConfigUpdate = this.globalConfigUpdate;
        final long previousSlot = previousConfigUpdate.slot();
        if (Long.compareUnsigned(slot, previousSlot) <= 0 || Arrays.equals(data, previousConfigUpdate.data())) {
          return;
        }

        final var globalConfig = GlobalConfig.read(accountInfo);
        final var assetMetaContexts = AssetMetaContext.mapAssetMetas(globalConfig);

        final var previousAssetMetaMap = this.assetMetaMap;
        final var assetMetaMap = createMapChecked(slot, previousConfigUpdate.assetMetaContexts(), previousAssetMetaMap, assetMetaContexts, this.mintCache);
        if (assetMetaMap == null) {
          this.assetMetaMap = null;
          this.globalConfigUpdate = null;
          this.invalidGlobalConfig.signalAll();
        } else {
          this.assetMetaMap = assetMetaMap;
          this.globalConfigUpdate = new GlobalConfigUpdate(slot, assetMetaContexts, data);
          persistGlobalConfig(globalConfigFilePath, data);

          final var mintsNeeded = assetMetaMap.keySet().stream().<PublicKey>mapMulti((mint, downstream) -> {
            if (!previousAssetMetaMap.containsKey(mint) && mintCache.get(mint) == null) {
              downstream.accept(mint);
            }
          }).toList();
          if (!mintsNeeded.isEmpty()) {
            this.accountFetcher.priorityQueueBatchable(mintsNeeded, this);
          }

          for (final var mint : assetMetaMap.keySet()) {
            if (!previousAssetMetaMap.containsKey(mint) && this.stateAccountsThatNeedAssetMeta.containsKey(mint)) {
              this.newAssetMeta.signalAll();
            }
          }
        }
      }
    } finally {
      writeLock.unlock();
    }
  }

  @Override
  public void awaitNewAssetMeta(final long awaitNanos) throws InterruptedException {
    writeLock.lock();
    try {
      for (long remainingNanos = awaitNanos; ; ) {
        remainingNanos = newAssetMeta.awaitNanos(remainingNanos);
        if (remainingNanos <= 0) {
          return;
        } else {
          final var assetMetaMap = this.assetMetaMap;
          if (assetMetaMap != null) {
            for (final var mint : assetMetaMap.keySet()) {
              if (assetMetaMap.containsKey(mint)) {
                return;
              }
            }
          }
        }
      }
    } finally {
      writeLock.unlock();
    }
  }

  static void persistGlobalConfig(final Path globalConfigFilePath, final byte[] data) {
    try {
      Files.write(
          globalConfigFilePath,
          data,
          StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING
      );
    } catch (final IOException e) {
      logger.log(WARNING, "Failed to write GlobalConfig to file", e);
    }
  }
}
