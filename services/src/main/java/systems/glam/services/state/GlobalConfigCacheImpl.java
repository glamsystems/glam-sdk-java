package systems.glam.services.state;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import systems.glam.sdk.idl.programs.glam.config.gen.types.AssetMeta;
import systems.glam.sdk.idl.programs.glam.config.gen.types.GlobalConfig;
import systems.glam.sdk.idl.programs.glam.config.gen.types.OracleSource;
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
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import static java.lang.System.Logger.Level.*;

final class GlobalConfigCacheImpl implements GlobalConfigCache, Consumer<AccountInfo<byte[]>>, AccountConsumer {

  private static final System.Logger logger = System.getLogger(GlobalConfigCache.class.getName());

  private static final Comparator<AssetMeta> BY_PRIORITY = (a, b) -> {
    final int pA = a.priority();
    final int pB = b.priority();
    if (pA < 0) {
      if (pB < 0) {
        return Integer.compare(-pA, -pB);
      } else {
        return 1;
      }
    } else if (pB < 0) {
      return -1;
    } else {
      return Integer.compare(pA, pB);
    }
  };

  record GlobalConfigUpdate(long slot, GlobalConfig config, byte[] data) {

    public AssetMeta get(final int index) {
      final var assetMetaList = config.assetMetas();
      return index < assetMetaList.length ? assetMetaList[index] : null;
    }
  }

  private final Path globalConfigFilePath;
  private final PublicKey configProgram;
  private final PublicKey globalConfigKey;
  private final SolanaAccounts solanaAccounts;
  private final MintCache mintCache;
  private final AccountFetcher accountFetcher;
  private final long fetchDelayNanos;
  private final ReentrantReadWriteLock.ReadLock readLock;
  private final ReentrantReadWriteLock.WriteLock writeLock;
  private final Condition invalidGlobalConfig;

  volatile GlobalConfigUpdate globalConfigUpdate;
  volatile Map<PublicKey, AssetMeta[]> assetMetaMap;

  GlobalConfigCacheImpl(final Path globalConfigFilePath,
                        final PublicKey configProgram,
                        final PublicKey globalConfigKey,
                        final SolanaAccounts solanaAccounts,
                        final MintCache mintCache,
                        final AccountFetcher accountFetcher,
                        final Duration fetchDelay,
                        final GlobalConfigUpdate globalConfigUpdate,
                        final Map<PublicKey, AssetMeta[]> assetMetaMap) {
    this.globalConfigFilePath = globalConfigFilePath;
    this.configProgram = configProgram;
    this.globalConfigKey = globalConfigKey;
    this.solanaAccounts = solanaAccounts;
    this.mintCache = mintCache;
    this.accountFetcher = accountFetcher;
    this.fetchDelayNanos = fetchDelay.toMillis();
    final var lock = new ReentrantReadWriteLock();
    this.readLock = lock.readLock();
    this.writeLock = lock.writeLock();
    this.invalidGlobalConfig = writeLock.newCondition();
    this.globalConfigUpdate = globalConfigUpdate;
    this.assetMetaMap = assetMetaMap;
  }

  @Override
  public GlobalConfig globalConfig() {
    return this.globalConfigUpdate.config();
  }

  @Override
  public AssetMeta getByIndex(final int index) {
    readLock.lock();
    try {
      final var globalConfig = globalConfigUpdate;
      return globalConfig.get(index);
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public AssetMeta topPriorityForMint(final PublicKey mint) {
    readLock.lock();
    try {
      final var assetMetaMap = this.assetMetaMap;
      final var assetMetaEntries = assetMetaMap.get(mint);
      return assetMetaEntries == null ? null : assetMetaEntries[0];
    } finally {
      readLock.unlock();
    }
  }

  private AssetMeta topPriorityForMintChecked(final PublicKey mint, final int mintDecimals) {
    readLock.lock();
    try {
      final var assetMetaMap = this.assetMetaMap;
      final var assetMetaEntries = assetMetaMap.get(mint);
      if (assetMetaEntries == null) {
        return null;
      } else {
        final var assetMeta = assetMetaEntries[0];
        if (mintDecimals != assetMeta.decimals()) {
          return assetMeta;
        } else {
          writeLock.lock();
          try {
            this.globalConfigUpdate = null;
            this.assetMetaMap = null;
            this.invalidGlobalConfig.signal();
            final var msg = String.format("""
                    {
                     "event": "GlobalConfig decimals for Asset does not match Mint",
                     "mintDecimals": %d,
                     "entry": %s
                    """,
                mintDecimals, toJson(assetMeta)
            );
            logger.log(ERROR, msg);
            // TODO: Trigger alert and exit.
            throw new IllegalStateException(msg);
          } finally {
            writeLock.unlock();
          }
        }
      }
    } finally {
      readLock.unlock();
    }
  }

  @Override
  public AssetMeta topPriorityForMintChecked(final PublicKey mint) {
    final var mintContext = mintCache.get(mint);
    if (mintContext == null) {
      return null;
    } else {
      return topPriorityForMintChecked(mint, mintContext.decimals());
    }
  }

  @Override
  public AssetMeta topPriorityForMintChecked(final MintContext mintContext) {
    return topPriorityForMintChecked(mintContext.mint(), mintContext.decimals());
  }

  @Override
  public void run() {
    try {
      for (long remainingNanos; ; ) {
        accountFetcher.priorityQueue(configProgram, this);

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

  private static String toJson(final AssetMeta assetMeta) {
    return String.format("""
            {
             "asset": "%s",
             "decimals": %d,
             "oracle": "%s",
             "oracleSource": "%s",
             "priority": %d,
             "maxAgeSeconds": %d
            }""",
        assetMeta.asset().toBase58(),
        assetMeta.decimals(),
        assetMeta.oracle().toBase58(),
        assetMeta.oracleSource(),
        assetMeta.priority(),
        assetMeta.maxAgeSeconds()
    );
  }

  static Map<PublicKey, AssetMeta[]> createMap(final GlobalConfig globalConfig) {
    final var assetMetaArray = globalConfig.assetMetas();
    final var assetMetaMap = HashMap.<PublicKey, AssetMeta[]>newHashMap(assetMetaArray.length);
    for (final var assetMeta : assetMetaArray) {
      final var mint = assetMeta.asset();
      final var entries = assetMetaMap.get(mint);
      if (entries == null) {
        assetMetaMap.put(mint, new AssetMeta[]{assetMeta});
      } else {
        final int len = entries.length;
        final var newEntries = Arrays.copyOf(entries, len + 1);
        newEntries[len] = assetMeta;
        assetMetaMap.put(mint, newEntries);
      }
    }
    for (final var assetMetas : assetMetaMap.values()) {
      if (assetMetas.length > 1) {
        Arrays.sort(assetMetas, BY_PRIORITY);
      }
    }
    return assetMetaMap;
  }

  static Map<PublicKey, AssetMeta[]> createMapChecked(final long slot,
                                                      final GlobalConfig previousGlobalConfig,
                                                      final Map<PublicKey, AssetMeta[]> previousMetaMap,
                                                      final GlobalConfig globalConfig,
                                                      final MintCache mintCache) {
    final var previousAssetMetaList = previousGlobalConfig.assetMetas();
    final var assetMetaList = globalConfig.assetMetas();
    if (previousAssetMetaList.length > assetMetaList.length) {
      final var msg = String.format("""
              {
               "event": "GlobalConfig Oracle Removed",
               "slot": %s,
               "previousLen": %d,
               "newLen": %d,
              }
              """,
          Long.toUnsignedString(slot), previousAssetMetaList.length, assetMetaList.length
      );
      logger.log(ERROR, msg);
      // TODO: Trigger alert and exit.
      return null;
    }

    final var previousOracleSourceMap = HashMap.<PublicKey, OracleSource>newHashMap(previousAssetMetaList.length);
    for (int i = 0; i < previousAssetMetaList.length; ++i) {
      final var previous = previousAssetMetaList[i];
      final var current = assetMetaList[i];
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
              toJson(previous), toJson(current)
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
                  toJson(previous), toJson(current)
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
            Long.toUnsignedString(slot), i, toJson(previous), toJson(current)
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
            toJson(previous), toJson(current)
        );
        logger.log(ERROR, msg);
        // TODO: Trigger alert and exit.
        return null;
      }

      previousOracleSourceMap.put(previous.oracle(), previous.oracleSource());
    }

    if (assetMetaList.length > previousAssetMetaList.length) {
      for (int i = previousAssetMetaList.length; i < assetMetaList.length; ++i) {
        logger.log(INFO, String.format("""
                    {
                     "event": "New GlobalConfig Oracle Entry",
                     "slot": %s,
                     "index": %d,
                     "entry": %s
                    }
                    """,
                Long.toUnsignedString(slot), i, toJson(assetMetaList[i])
            )
        );
      }
    }

    final var assetMetaMap = createMapChecked(slot, globalConfig, previousOracleSourceMap, mintCache);
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
              toJson(previousMetas[0]), toJson(assetMetas[0])
          );
          logger.log(ERROR, msg);
          // TODO: Trigger alert and exit.
          return null;
        }
      }
    }
    return assetMetaMap;
  }

  static Map<PublicKey, AssetMeta[]> createMapChecked(final long slot,
                                                      final GlobalConfig globalConfig,
                                                      final MintCache mintCache) {
    return createMapChecked(slot, globalConfig, Map.of(), mintCache);
  }

  static Map<PublicKey, AssetMeta[]> createMapChecked(final long slot,
                                                      final GlobalConfig globalConfig,
                                                      final Map<PublicKey, OracleSource> previousOracleSourceMap,
                                                      final MintCache mintCache) {
    final var assetMetaArray = globalConfig.assetMetas();
    final var distinctOracleSource = HashMap.<PublicKey, AssetMeta>newHashMap(assetMetaArray.length << 2);
    final var assetMetaMap = HashMap.<PublicKey, AssetMeta[]>newHashMap(assetMetaArray.length);
    for (int i = 0; i < assetMetaArray.length; ++i) {
      final var assetMeta = assetMetaArray[i];
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
            Long.toUnsignedString(slot), mintContext.decimals(), i, toJson(assetMeta)
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
            i, toJson(assetMeta)
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
            toJson(otherMeta), toJson(assetMeta)
        );
        logger.log(ERROR, msg);
        // TODO: Trigger alert and exit.
        return null;
      }

      final var entries = assetMetaMap.get(mint);
      if (entries == null) {
        assetMetaMap.put(mint, new AssetMeta[]{assetMeta});
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
                toJson(entry), toJson(assetMeta)
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
        Arrays.sort(assetMetas, BY_PRIORITY);
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
                toJson(assetMetas[0]), toJson(assetMeta)
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
    if (globalConfigAccountInfo != null) {
      accept(globalConfigAccountInfo);
    }
    final var assetMap = this.assetMetaMap;
    for (final var accountInfo : accounts) {
      final var key = accountInfo.pubKey();
      if (assetMap.containsKey(key) && mintCache.get(key) == null) {
        final var mintContext = mintCache.setGet(MintContext.createContext(solanaAccounts, accountInfo));
        topPriorityForMintChecked(mintContext);
      }
    }
  }

  @Override
  public void accept(final AccountInfo<byte[]> accountInfo) {
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

        final var previousAssetMetaMap = this.assetMetaMap;
        final var assetMetaMap = createMapChecked(slot, previousConfigUpdate.config(), previousAssetMetaMap, globalConfig, this.mintCache);
        if (assetMetaMap == null) {
          this.assetMetaMap = null;
          this.globalConfigUpdate = null;
          this.invalidGlobalConfig.signal();
          return;
        }
        this.assetMetaMap = assetMetaMap;
        this.globalConfigUpdate = new GlobalConfigUpdate(slot, globalConfig, data);
        persistGlobalConfig(globalConfigFilePath, data);

        final var mintsNeeded = assetMetaMap.keySet().stream().<PublicKey>mapMulti((mint, downstream) -> {
          if (!previousAssetMetaMap.containsKey(mint) && mintCache.get(mint) == null) {
            downstream.accept(mint);
          }
        }).toList();
        if (!mintsNeeded.isEmpty()) {
          this.accountFetcher.priorityQueueBatchable(mintsNeeded, this);
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
