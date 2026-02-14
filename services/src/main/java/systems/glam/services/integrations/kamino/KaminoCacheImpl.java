package systems.glam.services.integrations.kamino;

import software.sava.core.accounts.PublicKey;
import software.sava.core.encoding.ByteUtil;
import software.sava.idl.clients.kamino.lend.gen.types.Reserve;
import software.sava.idl.clients.kamino.scope.entries.*;
import software.sava.idl.clients.kamino.scope.entries.Deprecated;
import software.sava.idl.clients.kamino.scope.gen.types.Configuration;
import software.sava.idl.clients.kamino.scope.gen.types.EmaType;
import software.sava.idl.clients.kamino.scope.gen.types.OracleMappings;
import software.sava.idl.clients.kamino.scope.gen.types.OracleType;
import software.sava.idl.clients.kamino.vaults.gen.types.VaultState;
import software.sava.rpc.json.http.client.ProgramAccountsRequest;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import software.sava.services.core.net.http.NotifyClient;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.services.io.FileUtils;
import systems.glam.services.oracles.scope.FeedIndexes;
import systems.glam.services.oracles.scope.MappingsContext;
import systems.glam.services.oracles.scope.ScopeFeedContext;
import systems.glam.services.rpc.AccountFetcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.System.Logger.Level.*;
import static java.nio.file.StandardOpenOption.*;
import static systems.glam.services.integrations.kamino.ReserveContext.fixedLengthString;

final class KaminoCacheImpl implements KaminoCache {

  static final System.Logger logger = System.getLogger(KaminoCache.class.getName());

  private final NotifyClient notifyClient;
  private final RpcCaller rpcCaller;
  private final AccountFetcher accountFetcher;
  private final PublicKey kLendProgram;
  private final PublicKey scopeProgram;
  private final PublicKey kVaultsProgram;
  private final ProgramAccountsRequest<byte[]> kVaultsRequest;
  private final Set<PublicKey> accountsNeededSet;
  private final long pollingDelayNanos;
  private final Path configurationsPath;
  private final Path mappingsPath;
  private final Path reserveContextsFilePath;
  private final ConcurrentMap<PublicKey, ReserveContext> reserveContextMap;
  private final ConcurrentMap<PublicKey, MappingsContext> mappingsContextMap;
  private final ConcurrentMap<PublicKey, ScopeFeedContext> priceFeedContextMap;
  private final ConcurrentMap<PublicKey, KaminoVaultContext> vaultStateContextMap;
  private final ReentrantReadWriteLock.WriteLock writeLock;
  private final Condition reserveScopeChangeCondition;
  private final ReentrantReadWriteLock.ReadLock readLock;
  private final AtomicInteger asyncReserveUpdates;

  KaminoCacheImpl(final NotifyClient notifyClient,
                  final RpcCaller rpcCaller,
                  final AccountFetcher accountFetcher,
                  final PublicKey kLendProgram,
                  final PublicKey scopeProgram,
                  final PublicKey kVaultsProgram,
                  final ProgramAccountsRequest<byte[]> kVaultsRequest,
                  final Duration pollingDelay,
                  final Path configurationsPath,
                  final Path mappingsPath,
                  final Path reserveContextsFilePath,
                  final Map<PublicKey, ScopeFeedContext> feedContextMap,
                  final ConcurrentMap<PublicKey, MappingsContext> mappingsContextMap,
                  final ConcurrentMap<PublicKey, ReserveContext> reserveContextMap,
                  final ConcurrentMap<PublicKey, KaminoVaultContext> vaultStateContextMap) {
    this.notifyClient = notifyClient;
    this.rpcCaller = rpcCaller;
    this.accountFetcher = accountFetcher;
    this.kLendProgram = kLendProgram;
    this.scopeProgram = scopeProgram;
    this.kVaultsProgram = kVaultsProgram;
    this.kVaultsRequest = kVaultsRequest;
    this.pollingDelayNanos = pollingDelay.toNanos();
    this.configurationsPath = configurationsPath;
    this.mappingsPath = mappingsPath;
    this.reserveContextsFilePath = reserveContextsFilePath;
    this.reserveContextMap = reserveContextMap;
    this.mappingsContextMap = mappingsContextMap;
    this.vaultStateContextMap = vaultStateContextMap;
    this.priceFeedContextMap = new ConcurrentHashMap<>();
    this.accountsNeededSet = ConcurrentHashMap.newKeySet(feedContextMap.size() << 1);
    for (final var feedContext : feedContextMap.values()) {
      priceFeedContextMap.put(feedContext.configurationKey(), feedContext);
      priceFeedContextMap.put(feedContext.oracleMappings(), feedContext);
      priceFeedContextMap.put(feedContext.priceFeed(), feedContext);
      accountsNeededSet.add(feedContext.oracleMappings());
      accountsNeededSet.add(feedContext.configurationKey());
    }
    for (final var reserveContext : reserveContextMap.values()) {
      final var feedContext = priceFeedContextMap.get(reserveContext.priceFeed());
      if (feedContext != null) {
        feedContext.indexReserveContext(reserveContext);
      }
    }
    final var lock = new ReentrantReadWriteLock();
    this.writeLock = lock.writeLock();
    this.readLock = lock.readLock();
    this.reserveScopeChangeCondition = writeLock.newCondition();
    this.asyncReserveUpdates = new AtomicInteger();
  }

  static void writeScopeConfiguration(final Path configurationsPath,
                                      final ScopeFeedContext scopeFeedContext) throws IOException {
    final var filePath = FileUtils.resolveAccountPath(configurationsPath, scopeFeedContext.configurationKey());
    Files.write(
        filePath,
        scopeFeedContext.configurationData(),
        CREATE, TRUNCATE_EXISTING, WRITE
    );
  }

  @Override
  public ReserveContext reserveContext(final PublicKey pubKey) {
    return reserveContextMap.get(pubKey);
  }

  @Override
  public KaminoVaultContext vaultForShareMint(final PublicKey sharesMint) {
    return vaultStateContextMap.get(sharesMint);
  }

  @Override
  public FeedIndexes indexes(final PublicKey mint, final PublicKey oracle, final OracleType oracleType) {
    readLock.lock();
    try {
      return priceFeedContextMap.values().stream().<FeedIndexes>mapMulti((scopeFeedContext, downstream) -> {
        final var feedIndexes = scopeFeedContext.indexes(mint, oracle, oracleType);
        if (feedIndexes != null) {
          downstream.accept(feedIndexes);
        }
      }).sorted().findFirst().orElse(null);
    } finally {
      readLock.unlock();
    }
  }

  private void deleteScopeConfiguration(final ScopeFeedContext scopeFeedContext) {
    writeLock.lock();
    try {
      removeConfig(scopeFeedContext);
    } finally {
      writeLock.unlock();
    }
    final var configJson = scopeFeedContext.toJson();
    final var msg = String.format("""
        {
          "event": "Scope Account Deleted",
          "config": %s
        }""", configJson
    );
    logger.log(INFO, msg);
    try {
      Files.deleteIfExists(FileUtils.resolveAccountPath(configurationsPath, scopeFeedContext.configurationKey()));
    } catch (final IOException e) {
      logger.log(WARNING, "Failed to delete Scope Configuration.", e);
    }
    logger.log(INFO, msg);
    try {
      Files.deleteIfExists(FileUtils.resolveAccountPath(mappingsPath, scopeFeedContext.oracleMappings()));
    } catch (final IOException e) {
      logger.log(WARNING, "Failed to delete Scope Mappings.", e);
    }
  }

  private void removeConfig(final ScopeFeedContext scopeFeedContext) {
    final var configKey = scopeFeedContext.configurationKey();
    final var mappingsKey = scopeFeedContext.oracleMappings();

    this.accountsNeededSet.remove(configKey);
    this.accountsNeededSet.remove(mappingsKey);

    final var priceFeedKey = scopeFeedContext.priceFeed();
    this.priceFeedContextMap.remove(priceFeedKey);
    this.priceFeedContextMap.remove(mappingsKey);
    this.priceFeedContextMap.remove(configKey);

    this.mappingsContextMap.remove(priceFeedKey);
    this.mappingsContextMap.remove(mappingsKey);
  }

  private void handleConfigurationChange(final long slot,
                                         final PublicKey configurationKey,
                                         final byte[] data) {
    var witness = priceFeedContextMap.get(configurationKey);
    if (witness == null) {
      final var scopeFeedContext = ScopeFeedContext.createContext(
          slot, data,
          configurationKey
      );
      writeLock.lock();
      try {
        witness = priceFeedContextMap.putIfAbsent(scopeFeedContext.priceFeed(), scopeFeedContext);
        if (witness == null) {
          this.priceFeedContextMap.put(scopeFeedContext.oracleMappings(), scopeFeedContext);
          this.priceFeedContextMap.put(configurationKey, scopeFeedContext);
          this.accountsNeededSet.add(configurationKey);
        } else if (witness.isStaleOrUnchanged(slot, data)) {
          return;
        } else {
          removeConfig(witness);
        }
      } finally {
        writeLock.unlock();
      }
      notifyNewConfiguration(scopeFeedContext);
    } else if (!witness.isStaleOrUnchanged(slot, data)) {
      removeConfig(witness);
      notifyConfigurationChange(witness, slot, data);
    }
  }

  private void persistMappings(final MappingsContext mappingContext) {
    final var mappingsPath = FileUtils.resolveAccountPath(this.mappingsPath, mappingContext.publicKey());
    try {
      Files.write(mappingsPath, mappingContext.data(), CREATE, TRUNCATE_EXISTING, WRITE);
    } catch (final IOException e) {
      logger.log(WARNING, "Failed to persist mappings.", e);
    }
  }

  private void handleMappingChange(final AccountInfo<byte[]> accountInfo) {
    final var mappingsKey = accountInfo.pubKey();
    if (!priceFeedContextMap.containsKey(mappingsKey)) {
      return;
    }
    var witness = mappingsContextMap.get(mappingsKey);
    if (witness != null && !witness.changed(accountInfo)) {
      return;
    }
    final MappingsContext mappingContext;
    final ScopeFeedContext scopeFeedContext;
    writeLock.lock();
    try {
      scopeFeedContext = priceFeedContextMap.get(mappingsKey);
      if (scopeFeedContext == null) {
        return;
      }
      witness = mappingsContextMap.get(mappingsKey);
      if (witness == null || witness.changed(accountInfo)) {
        mappingContext = MappingsContext.createContext(accountInfo);
        mappingsContextMap.put(scopeFeedContext.priceFeed(), mappingContext);
        mappingsContextMap.put(mappingsKey, mappingContext);
        final int numChanges = scopeFeedContext.reIndexReserves(reserveContextMap, mappingContext);
        if (numChanges > 0) {
          asyncReserveUpdates.addAndGet(numChanges);
          reserveScopeChangeCondition.signalAll();
        }
      } else {
        return;
      }
    } finally {
      writeLock.unlock();
    }

    if (witness != null) {
      notifyMappingsChange(scopeFeedContext, witness, mappingContext);
    }

    persistMappings(mappingContext);
  }

  private void updateIfChanged(final ReserveContext reserveContext) {
    final var priceFeed = reserveContext.priceFeed();
    if (!priceFeedContextMap.containsKey(priceFeed)) {
      return;
    }
    final var key = reserveContext.pubKey();

    var witness = reserveContextMap.putIfAbsent(key, reserveContext);
    if (witness == null) {
      writeLock.lock();
      try {
        witness = reserveContextMap.get(key);
        if (witness == reserveContext) {
          final var feedContext = priceFeedContextMap.get(priceFeed);
          if (feedContext == null) {
            return;
          } else {
            feedContext.indexReserveContext(reserveContext);
            this.asyncReserveUpdates.incrementAndGet();
            this.reserveScopeChangeCondition.signal();
          }
        }
      } finally {
        writeLock.unlock();
      }
    }
    for (; ; ) {
      if (witness.isBefore(reserveContext)) {
        final var changes = witness.changed(reserveContext);
        if (changes.isEmpty()) {
          return;
        } else {
          writeLock.lock();
          try {
            final var previous = reserveContextMap.get(key);
            if (previous != witness) {
              witness = previous;
              continue;
            }
            reserveContextMap.put(key, reserveContext);
            final var feedContext = priceFeedContextMap.get(priceFeed);
            if (feedContext == null) {
              return;
            } else if (ReserveContext.onlyCollateralChanged(changes)) {
              feedContext.resortReserves(reserveContext);
              return;
            } else {
              feedContext.removePreviousEntry(witness);
              feedContext.indexReserveContext(reserveContext);
              notifyReserveChange(witness, reserveContext);
              this.asyncReserveUpdates.incrementAndGet();
              this.reserveScopeChangeCondition.signal();
            }
          } finally {
            writeLock.unlock();
          }
        }
      } else {
        return;
      }
    }
  }

  private void handleVaultStateChange(final AccountInfo<byte[]> accountInfo) {

    final byte[] data = accountInfo.data();
    final var sharesMint = PublicKey.readPubKey(accountInfo.data(), VaultState.SHARES_MINT_OFFSET);
    final long slot = accountInfo.context().slot();
    var previous = vaultStateContextMap.get(sharesMint);
    if (previous != null && Long.compareUnsigned(previous.slot(), slot) >= 0) {
      return;
    }

    final var reserveKeys = KaminoVaultContext.parseReserveKeys(data);
    final var vaultLookupTable = PublicKey.readPubKey(data, VaultState.VAULT_LOOKUP_TABLE_OFFSET);

    final KaminoVaultContext kaminoVaultContext;
    if (previous == null) {
      final var name = fixedLengthString(data, VaultState.NAME_OFFSET, VaultState.NAME_OFFSET + VaultState.NAME_LEN);
      kaminoVaultContext = new KaminoVaultContext(
          slot, accountInfo.pubKey(),
          PublicKey.readPubKey(data, VaultState.TOKEN_MINT_OFFSET),
          ByteUtil.getInt64LE(data, VaultState.TOKEN_MINT_DECIMALS_OFFSET),
          PublicKey.readPubKey(data, VaultState.TOKEN_PROGRAM_OFFSET),
          sharesMint,
          ByteUtil.getInt64LE(data, VaultState.SHARES_MINT_DECIMALS_OFFSET),
          reserveKeys,
          name,
          vaultLookupTable
      );
    } else {
      kaminoVaultContext = previous.withReserves(slot, reserveKeys, vaultLookupTable);
      if (kaminoVaultContext == previous) {
        return;
      }
    }

    vaultStateContextMap.merge(
        sharesMint,
        kaminoVaultContext,
        (a, b) -> Long.compareUnsigned(a.slot(), b.slot()) >= 0 ? a : b
    );
  }

  @Override
  public void subscribe(final SolanaRpcWebsocket websocket) {
    websocket.programSubscribe(
        scopeProgram, List.of(OracleMappings.SIZE_FILTER, OracleMappings.DISCRIMINATOR_FILTER), this
    );
    websocket.programSubscribe(
        scopeProgram, List.of(Configuration.SIZE_FILTER, Configuration.DISCRIMINATOR_FILTER), this
    );
    websocket.programSubscribe(
        kLendProgram, List.of(Reserve.SIZE_FILTER, Reserve.DISCRIMINATOR_FILTER), this
    );
    websocket.programSubscribe(
        kVaultsProgram, List.of(VaultState.SIZE_FILTER, VaultState.DISCRIMINATOR_FILTER), this
    );
  }

  @Override
  public ReserveContext acceptReserve(final AccountInfo<byte[]> accountInfo) {
    final byte[] data = accountInfo.data();
    if (data.length == Reserve.BYTES && Reserve.DISCRIMINATOR.equals(data, 0)) {
      final var reserveContext = ReserveContext.createContext(accountInfo, mappingsContextMap);
      updateIfChanged(reserveContext);
      return reserveContext;
    } else {
      return null;
    }
  }

  @Override
  public void accept(final AccountInfo<byte[]> accountInfo) {
    try {
      final byte[] data = accountInfo.data();
      if (data.length == Reserve.BYTES && Reserve.DISCRIMINATOR.equals(data, 0)) {
        final var reserveContext = ReserveContext.createContext(accountInfo, mappingsContextMap);
        updateIfChanged(reserveContext);
      } else if (data.length == VaultState.BYTES && VaultState.DISCRIMINATOR.equals(data, 0)) {
        handleVaultStateChange(accountInfo);
      } else if (data.length == OracleMappings.BYTES && OracleMappings.DISCRIMINATOR.equals(data, 0)) {
        handleMappingChange(accountInfo);
      } else if (data.length == Configuration.BYTES && Configuration.DISCRIMINATOR.equals(data, 0)) {
        handleConfigurationChange(accountInfo.context().slot(), accountInfo.pubKey(), data);
      } else {
        logger.log(WARNING, "Unhandled Scope account " + accountInfo.pubKey());
      }
    } catch (final RuntimeException ex) {
      logger.log(ERROR, "Failed to handle Scope account " + accountInfo.pubKey(), ex);
    }
  }

  @Override
  public void run() {
    try {
      var accountsNeededList = new ArrayList<>(this.accountsNeededSet);
      for (; ; ) {
        final var kVaultsFuture = rpcCaller.courteousCall(
            rpcClient -> rpcClient.getProgramAccounts(kVaultsRequest),
            "rpcClient#getKaminoVaultAccounts"
        );

        final var accountInfoListFuture = accountFetcher.priorityQueue(accountsNeededList);

        int accountsDeleted = 0;
        int i = 0;
        final var accountMap = accountInfoListFuture.join().accountMap();
        for (final var accountNeeded : accountsNeededList) {
          final var accountInfo = accountMap.get(accountNeeded);
          if (accountInfo == null) {
            ++accountsDeleted;
            final var key = accountsNeededList.get(i);
            this.accountsNeededSet.remove(key);
            final var feedContext = priceFeedContextMap.remove(key);
            if (feedContext != null) {
              deleteScopeConfiguration(feedContext);
            } else {
              this.mappingsContextMap.remove(key);
              logger.log(WARNING, "Scope OracleMappings account has been deleted " + key);
            }
          } else {
            accept(accountInfo);
          }
          ++i;
        }
        if (accountsDeleted > 0) {
          accountsNeededList = new ArrayList<>(this.accountsNeededSet);
        }

        for (final var accountInfo : kVaultsFuture.join()) {
          handleVaultStateChange(accountInfo);
        }

        readLock.lock();
        try {
          if (this.asyncReserveUpdates.getAndSet(0) > 0) {
            writeReserves(reserveContextsFilePath, reserveContextMap);
          } else if (accountsDeleted == 0) {
            logger.log(INFO, "No changes to Scope Reserves.");
          }
        } finally {
          readLock.unlock();
        }

        writeLock.lock();
        try {
          for (long remaining = pollingDelayNanos; asyncReserveUpdates.get() == 0; ) {
            remaining = reserveScopeChangeCondition.awaitNanos(remaining);
            if (remaining <= 0) {
              break;
            }
          }
        } finally {
          writeLock.unlock();
        }
      }

    } catch (final InterruptedException e) {
      // exit
    } catch (final RuntimeException e) {
      logger.log(ERROR, "Failed to poll Scope accounts.", e);
    }
  }

  static void writeReserves(final Path reserveContextsFilePath,
                            final Map<PublicKey, ReserveContext> reserveContextMap) {
    final var marketsJson = reserveContextMap.values().stream()
        .collect(Collectors.groupingBy(ReserveContext::market))
        .entrySet().stream()
        .map(entry -> {
          final var market = entry.getKey();
          final var reserveJSON = entry.getValue().stream()
              .map(ReserveContext::priceChainsToJson)
              .collect(Collectors.joining(",\n"))
              .indent(2);
          return String.format("""
              {
                "market": "%s",
                "reserves": [
                %s]
              }""", market, reserveJSON
          );
        }).collect(Collectors.joining(",\n", "[", "]"));

    try {
      Files.writeString(
          reserveContextsFilePath,
          marketsJson,
          CREATE, TRUNCATE_EXISTING, WRITE
      );
    } catch (final IOException e) {
      logger.log(ERROR, "Failed to write Kamino Markets Reserve Scope Price Chains.", e);
    }
  }

  private static String toJson(final Set<EmaType> emaTypes) {
    return emaTypes == null || emaTypes.isEmpty()
        ? ""
        : emaTypes.stream().map(EmaType::name).collect(Collectors.joining("\",\"", ",\n  \"emaTypes\": [\"", "\"]"));
  }

  private static String toJson(final ScopeEntry scopeEntry) {
    if (scopeEntry == null) {
      return null;
    }
    return switch (scopeEntry) {
      case OracleEntry e -> {
        final var prefix = String.format("""
                {
                  "type": "%s",
                  "index": %d,
                  "oracle": "%s"%s""",
            e.oracleType().name(),
            scopeEntry.index(),
            e.oracle(),
            toJson(e.emaTypes())
        );
        yield switch (e) {
          case ReferencesEntry re -> {
            final var refPrefix = String.format("""
                    %s,
                      "refPrice": %s""",
                prefix,
                toJson(re.refPrice())
            );
            yield switch (re) {
              case Chainlink chainlink -> String.format("""
                      %s,
                        "confidenceFactor": %d
                      }""",
                  refPrefix,
                  chainlink.confidenceFactor()
              );
              case PythLazer pythLazer -> String.format("""
                      %s,
                        "feedId": %d,
                        "exponent": %d,
                        "confidenceFactor": %d
                      }""",
                  refPrefix,
                  pythLazer.feedId(),
                  pythLazer.exponent(),
                  pythLazer.confidenceFactor()
              );
              default -> refPrefix + "\n}";
            };
          }
          case ChainlinkStatusEntry chainlinkStatusEntry -> String.format("""
                  %s,
                    "marketStatusBehavior": "%s"
                  }""",
              prefix,
              chainlinkStatusEntry.marketStatusBehavior()
          );
          default -> prefix + "\n}";
        };
      }
      case CappedFloored cappedFloored -> String.format("""
              {
                "type": "%s",
                "index": %d,
                "source": %s,
                "cap": %s,
                "floor": %s
              }""",
          cappedFloored.oracleType().name(),
          cappedFloored.index(),
          toJson(cappedFloored.sourceEntry()),
          toJson(cappedFloored.capEntry()),
          toJson(cappedFloored.flooredEntry())
      );
      case MostRecentOf mostRecentOf -> {
        final var prefix = String.format("""
                {
                  "type": "%s",
                  "index": %d,
                  "sources": %s,
                  "maxDivergenceBps": %d,
                  "sourcesMaxAgeS": %d,""",
            mostRecentOf.oracleType().name(),
            mostRecentOf.index(),
            toJson(mostRecentOf.sources()),
            mostRecentOf.maxDivergenceBps(),
            mostRecentOf.sourcesMaxAgeS()
        );
        yield switch (mostRecentOf) {
          case CappedMostRecentOf cappedMostRecentOf -> String.format("""
                  %s
                    "cap": %s
                  }""",
              prefix,
              toJson(cappedMostRecentOf.capEntry())
          );
          case MostRecentOfEntry mostRecentOfRecord -> String.format("""
                  %s
                    "refPrice": %s
                  }""",
              prefix,
              toJson(mostRecentOfRecord.refPrice())
          );
        };
      }
      case Deprecated e -> String.format("""
          {
            "type": "Deprecated",
            "index": %d
          }""", e.index()
      );
      case DiscountToMaturity discountToMaturity -> String.format("""
              {
                "type": "%s",
                "index": %d,
                "discountPerYearBps": %d,
                "maturityTimestamp": %d
              }""",
          discountToMaturity.oracleType().name(),
          discountToMaturity.index(),
          discountToMaturity.discountPerYearBps(),
          discountToMaturity.maturityTimestamp()
      );
      case FixedPrice fixedPrice -> String.format("""
              {
                "type": "%s",
                "index": %d,
                "value": %d,
                "exp": %d,
                "decimal": "%s"
              }""",
          fixedPrice.oracleType().name(),
          fixedPrice.index(),
          fixedPrice.value(), fixedPrice.exp(),
          fixedPrice.decimal().toPlainString()
      );
      case NotYetSupported notYetSupported -> String.format("""
              {
                "type": "%s",
                "index": %d,
                "oracle": "%s",
                "twapSource": %s,%s
                "refPrice": %s,
                "generic": "%s"
              }""",
          notYetSupported.oracleType().name(),
          notYetSupported.index(),
          notYetSupported.priceAccount(),
          toJson(notYetSupported.twapSource()),
          toJson(notYetSupported.emaTypes()),
          toJson(notYetSupported.refPrice()),
          Base64.getEncoder().encodeToString(notYetSupported.generic())
      );
      case ScopeTwap scopeTwap -> String.format("""
              {
                "type": "%s",
                "index": %d,
                "source": %s
              }""",
          scopeTwap.oracleType().name(),
          scopeTwap.index(),
          toJson(scopeTwap.sourceEntry())
      );
      case Unused e -> String.format("""
          {
            "type": "Unused",
            "index": %d
          }""", e.index()
      );
    };
  }

  static String toJson(final ScopeEntry[] priceChain) {
    return Arrays.stream(priceChain).<String>mapMulti((entry, downstream) -> {
      if (entry != null) {
        final var json = toJson(entry);
        if (json != null) {
          downstream.accept(json.indent(4).stripTrailing());
        }
      }
    }).collect(Collectors.joining(",\n", "[\n", "\n  ]"));
  }

  private void notifyReserveChange(final ReserveContext previous, final ReserveContext reserveContext) {
    final var changes = previous.changed(reserveContext);
    final var changesString = changes.stream().map(Enum::name).collect(Collectors.joining("\",\""));
    var msg = String.format("""
            {
              "event": "Kamino Reserve Change",
              "changes": ["%s"]
              "previous": %s,
              "latest": %s,
            }""",
        changesString,
        previous.priceChainsToJson(), reserveContext.priceChainsToJson()
    );
    logger.log(INFO, msg);
    // TODO retry handling.
    msg = String.format("""
            {
              "event": "Kamino Reserve Change",
              "changes": ["%s"]
              "previous": %s,
              "latest": %s,
            }""",
        changesString,
        previous.priceChainsToJsonNoTokenInfo(), reserveContext.priceChainsToJsonNoTokenInfo()
    );
    notifyClient.postMsg(msg);
  }

  private void notifyNewConfiguration(final ScopeFeedContext scopeFeedContext) {
    final var configJson = scopeFeedContext.toJson();
    final var msg = String.format("""
        {
          "event": "New Scope Configuration",
          "config": %s
        }""", configJson
    );
    logger.log(INFO, msg);
    try {
      writeScopeConfiguration(configurationsPath, scopeFeedContext);
    } catch (final IOException e) {
      logger.log(WARNING, "Failed to persist Scope configuration.", e);
    }
    notifyClient.postMsg(msg);
  }

  private void notifyConfigurationChange(final ScopeFeedContext witness, final long slot, final byte[] data) {
    final var msg = String.format("""
            {
              "event": "Scope Configuration Change",
              "previous": %s,
              "latest": %s
            }""",
        witness.toJson(),
        ScopeFeedContext.configurationToJson(slot, witness.configurationKey(), data)
    );
    logger.log(ERROR, msg);
    // TODO: Trigger Alert
  }

  private void notifyMappingsChange(final ScopeFeedContext scopeFeedContext,
                                    final MappingsContext witness,
                                    final MappingsContext mappingContext) {
    final var mappingsKey = mappingContext.publicKey();
    final var previousEntries = witness.scopeEntries();
    final var newEntries = mappingContext.scopeEntries();
    final int numEntries = newEntries.numEntries();
    if (previousEntries.numEntries() != numEntries) {
      throw new IllegalStateException(String.format(
          "Scope Mappings %s changed in length from %d to %d.",
          mappingsKey, previousEntries.numEntries(), numEntries
      ));
    }

    final var changes = IntStream.range(0, numEntries).mapToObj(i -> {
      final var prevEntry = previousEntries.scopeEntry(i);
      final var newEntry = newEntries.scopeEntry(i);
      if (prevEntry.equals(newEntry)) {
        return null;
      } else {
        final var affectedReserves = scopeFeedContext.reservesForIndex(i);
        if (affectedReserves == null || affectedReserves.isEmpty()) {
          return null;
        }
        return String.format("""
                {
                  "previous": %s,
                  "latest": %s,
                  "reserves": [%s]
                }""",
            prevEntry, newEntry,
            affectedReserves.values().stream()
                .map(ReserveContext::keysToJson)
                .collect(Collectors.joining(",\n"))
        );
      }
    }).filter(Objects::nonNull).toList();

    final int numChanges = changes.size();
    if (numChanges > 0) {
      final var msg = String.format("""
              {
                "event": "Scope Mappings Change",
                "priceFeed": "%s",
                "numChanges": %d,
                "slot": %s,
                "changes": [%s]
              }""",
          mappingsKey.toBase58(),
          numChanges,
          Long.toUnsignedString(mappingContext.slot()),
          String.join(",\n", changes)
      );
      logger.log(INFO, msg);
      // TODO retry handling.
      notifyClient.postMsg(msg);
    }
  }
}
