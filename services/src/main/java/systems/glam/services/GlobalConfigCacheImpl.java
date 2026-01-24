package systems.glam.services;

import software.sava.core.accounts.PublicKey;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import systems.glam.sdk.idl.programs.glam.config.gen.types.AssetMeta;
import systems.glam.sdk.idl.programs.glam.config.gen.types.GlobalConfig;
import systems.glam.services.rpc.AccountConsumer;
import systems.glam.services.rpc.AccountFetcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.*;
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
  private final AccountFetcher accountFetcher;
  private final long fetchDelayMillis;
  private final ReentrantReadWriteLock lock;

  private volatile GlobalConfigUpdate globalConfigUpdate;
  private volatile Map<PublicKey, AssetMeta[]> assetMetaMap;

  GlobalConfigCacheImpl(final Path globalConfigFilePath,
                        final PublicKey configProgram,
                        final PublicKey globalConfigKey,
                        final AccountFetcher accountFetcher,
                        final Duration fetchDelay,
                        final GlobalConfigUpdate globalConfigUpdate,
                        final Map<PublicKey, AssetMeta[]> assetMetaMap) {
    this.globalConfigFilePath = globalConfigFilePath;
    this.configProgram = configProgram;
    this.globalConfigKey = globalConfigKey;
    this.accountFetcher = accountFetcher;
    this.fetchDelayMillis = fetchDelay.toMillis();
    this.lock = new ReentrantReadWriteLock();
    this.globalConfigUpdate = globalConfigUpdate;
    this.assetMetaMap = assetMetaMap;
  }


  @Override
  public AssetMeta getByIndex(final int index) {
    final var readLock = lock.readLock();
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
    final var readLock = lock.readLock();
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
  public void run() {
    try {
      for (; ; ) {
        accountFetcher.priorityQueue(configProgram, this);
        //noinspection BusyWait
        Thread.sleep(fetchDelayMillis);
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
              }
            """,
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
                                                      final GlobalConfig globalConfig) {

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
    }

    for (int i = 0; i < previousAssetMetaList.length; ++i) {
      final var previous = previousAssetMetaList[i];
      final var current = assetMetaList[i];
      if (previous.asset().equals(current.asset()) && previous.oracle().equals(current.oracle())) {
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
      }
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

    final var assetMetaMap = createMapChecked(slot, globalConfig);

    for (final var entry : previousMetaMap.entrySet()) {
      final var mint = entry.getKey();
      final var assetMetas = entry.getValue();
      final var previousMetas = previousMetaMap.get(mint);
      if (previousMetas != null) {
        final int expectedDecimals = assetMetas[0].decimals();
        final int previousDecimals = previousMetas[0].decimals();
        if (expectedDecimals != previousDecimals) {
          final var msg = String.format("""
                  {
                   "event": "Inconsistent Asset Decimals",
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
        }
      }
    }
    return assetMetaMap;
  }

  static Map<PublicKey, AssetMeta[]> createMapChecked(final long slot, final GlobalConfig globalConfig) {
    final var assetMetaArray = globalConfig.assetMetas();
    final var distinctOracleSource = HashMap.<PublicKey, AssetMeta>newHashMap(assetMetaArray.length << 2);
    final var assetMetaMap = HashMap.<PublicKey, AssetMeta[]>newHashMap(assetMetaArray.length);
    for (final var assetMeta : assetMetaArray) {
      final var mint = assetMeta.asset();
      final var entries = assetMetaMap.get(mint);
      if (entries == null) {
        assetMetaMap.put(mint, new AssetMeta[]{assetMeta});
      } else {
        final var oracle = assetMeta.oracle();
        final var otherMeta = distinctOracleSource.put(oracle, assetMeta);
        if (otherMeta != null && !otherMeta.oracleSource().equals(assetMeta.oracleSource())) {
          final var msg = String.format("""
                  {
                   "event": "Inconsistent OracleSource",
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
        }

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
                     "event": "Inconsistent Asset Decimals",
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
    final var accountInfo = accountMap.get(globalConfigKey);
    if (accountInfo != null) {
      accept(accountInfo);
    }
  }

  @Override
  public void accept(final AccountInfo<byte[]> accountInfo) {
    final var writeLock = lock.writeLock();
    writeLock.lock();
    try {
      final byte[] data = accountInfo.data();
      final long slot = accountInfo.context().slot();
      if (checkAccount(configProgram, accountInfo.owner(), slot, accountInfo.pubKey(), data)) {
        var previousConfigUpdate = this.globalConfigUpdate;
        long previousSlot = previousConfigUpdate.slot();
        if (Long.compareUnsigned(slot, previousSlot) <= 0 || Arrays.equals(data, previousConfigUpdate.data())) {
          return;
        }

        final var globalConfig = GlobalConfig.read(accountInfo);

        //noinspection NonAtomicOperationOnVolatileField: read/write lock protected.
        this.assetMetaMap = createMapChecked(slot, previousConfigUpdate.config(), this.assetMetaMap, globalConfig);
        this.globalConfigUpdate = new GlobalConfigUpdate(slot, globalConfig, data);
        persistGlobalConfig(globalConfigFilePath, data);
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
