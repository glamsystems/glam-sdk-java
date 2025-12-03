package systems.glam.services.oracles.scope;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.kamino.lend.gen.types.Reserve;
import software.sava.idl.clients.kamino.scope.entries.*;
import software.sava.idl.clients.kamino.scope.entries.Deprecated;
import software.sava.idl.clients.kamino.scope.gen.types.Configuration;
import software.sava.idl.clients.kamino.scope.gen.types.OracleMappings;
import software.sava.rpc.json.http.client.SolanaRpcClient;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import software.sava.services.core.net.http.NotifyClient;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.services.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.System.Logger.Level.*;
import static java.nio.file.StandardOpenOption.*;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static systems.glam.services.oracles.scope.ScopeMonitorServiceEntrypoint.writeConfigurations;

final class ScopeMonitorServiceImpl implements ScopeMonitorService {

  static final System.Logger logger = System.getLogger(ScopeMonitorService.class.getName());

  private final NotifyClient notifyClient;
  private final RpcCaller rpcCaller;
  private final PublicKey kLendProgram;
  private final PublicKey scopeProgram;
  private final Set<PublicKey> accountsNeededSet;
  private final long pollingDelayNanos;
  private final Path configurationsPath;
  private final Path mappingsPath;
  private final Path reserveContextsFilePath;
  private final Map<PublicKey, Configuration> configurationMap;
  private final Map<PublicKey, PublicKey> scopeFeedToMappings;
  private final Map<PublicKey, PublicKey> mappingsToScopeFeed;
  private final Map<PublicKey, MappingsContext> mappingsContextByPriceFeed;
  private final Map<PublicKey, ReserveContext> reserveContextMap;
  private final Map<PublicKey, AtomicReferenceArray<Map<PublicKey, ReserveContext>>> reservesUsingScopeIndex;
  private final ReentrantLock lock;

  ScopeMonitorServiceImpl(final NotifyClient notifyClient,
                          final RpcCaller rpcCaller,
                          final PublicKey kLendProgram,
                          final PublicKey scopeProgram,
                          final long pollingDelayNanos,
                          final Path configurationsPath,
                          final Path mappingsPath,
                          final Path reserveContextsFilePath,
                          final Map<PublicKey, Configuration> scopeConfigurations,
                          final Map<PublicKey, MappingsContext> mappingsContextByPriceFeed) {
    this.notifyClient = notifyClient;
    this.rpcCaller = rpcCaller;
    this.kLendProgram = kLendProgram;
    this.scopeProgram = scopeProgram;
    this.accountsNeededSet = ConcurrentHashMap.newKeySet(SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS);
    this.pollingDelayNanos = pollingDelayNanos;
    this.configurationsPath = configurationsPath;
    this.mappingsPath = mappingsPath;
    this.reserveContextsFilePath = reserveContextsFilePath;
    this.configurationMap = new ConcurrentHashMap<>();
    this.scopeFeedToMappings = new ConcurrentHashMap<>();
    this.mappingsToScopeFeed = new ConcurrentHashMap<>();
    this.mappingsContextByPriceFeed = mappingsContextByPriceFeed;
    this.reserveContextMap = new ConcurrentHashMap<>();
    this.reservesUsingScopeIndex = new ConcurrentHashMap<>();
    this.lock = new ReentrantLock();
    for (final var configuration : scopeConfigurations.values()) {
      if (configurationMap.put(configuration._address(), configuration) == null) {
        syncConfigMappings(configuration);
      }
    }
  }

  @Override
  public void accept(final AccountInfo<byte[]> accountInfo) {
    final byte[] data = accountInfo.data();
    if (data.length == OracleMappings.BYTES && OracleMappings.DISCRIMINATOR.equals(data, 0)) {
      final var mappingContext = MappingsContext.createContext(accountInfo);
      handleMappingChange(mappingContext);
    } else if (data.length == Configuration.BYTES && Configuration.DISCRIMINATOR.equals(data, 0)) {
      final var configuration = Configuration.read(accountInfo);
      handleConfigurationChange(configuration);
    }
  }

  private void handleReserveChange(final ReserveContext previous, final ReserveContext reserveContext) {
    final var msg = String.format("""
            {
              "event": "Kamino Reserve Change",
              "previous": %s,
              "latest": %s,
            }""",
        previous.priceChainsToJson(), reserveContext.priceChainsToJson()
    );
    logger.log(INFO, msg);
    // TODO retry handling.
    notifyClient.postMsg(msg);
  }

  private static String configurationToJson(final Configuration c) {
    return String.format("""
            {
              "address": "%s",
              "admin": "%s",
              "oracleMappings": "%s",
              "oraclePrices": "%s",
              "tokensMetadata": "%s",
              "oracleTwaps": "%s",
              "adminCached": "%s"
            }""",
        c._address().toBase58(),
        c.admin().toBase58(),
        c.oracleMappings().toBase58(),
        c.oraclePrices().toBase58(),
        c.tokensMetadata().toBase58(),
        c.oracleTwaps().toBase58(),
        c.adminCached().toBase58()
    );
  }

  private void syncConfigMappings(final Configuration configuration) {
    final var oraclePrices = configuration.oraclePrices();
    final var mappings = configuration.oracleMappings();
    scopeFeedToMappings.put(oraclePrices, mappings);
    mappingsToScopeFeed.put(mappings, oraclePrices);
    accountsNeededSet.add(configuration._address());
    accountsNeededSet.add(configuration.oracleMappings());
    reservesUsingScopeIndex.put(oraclePrices, new AtomicReferenceArray<>(OracleMappings.PRICE_INFO_ACCOUNTS_LEN));
  }

  private void handleConfigurationChange(final Configuration configuration) {
    final var key = configuration._address();
    final var previous = configurationMap.put(key, configuration);
    final var configJson = configurationToJson(configuration);
    final String msg;
    if (previous == null) {
      syncConfigMappings(configuration);
      msg = String.format("""
          {
            "event": "New Scope Configuration",
            "config": %s
          }""", configJson
      );
    } else if (!configuration.admin().equals(previous.admin())
        || !configuration.oracleMappings().equals(previous.oracleMappings())
        || !configuration.oraclePrices().equals(previous.oraclePrices())
        || !configuration.tokensMetadata().equals(previous.tokensMetadata())
        || !configuration.oracleTwaps().equals(previous.oracleTwaps())
        || !configuration.adminCached().equals(previous.adminCached())) {
      msg = String.format("""
              {
                "event": "Scope Configuration Change",
                "previous": %s,
                "latest": %s
              }""",
          configurationToJson(previous),
          configJson
      );
    } else {
      return;
    }
    logger.log(INFO, msg);
    // TODO retry handling.
    notifyClient.postMsg(msg);
    try {
      writeConfigurations(configurationsPath, configuration);
    } catch (final IOException e) {
      logger.log(WARNING, "Failed to persist Scope configuration.", e);
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

  private Map<PublicKey, ReserveContext> reservesUsingFeedIndex(final PublicKey priceFeed, final int index) {
    final var allReserves = reservesUsingScopeIndex.get(priceFeed);
    return allReserves.get(index);
  }

  private void handleMappingChange(final MappingsContext mappingContext) {
    final var mappingsPubKey = mappingContext.publicKey();
    lock.lock();
    try {
      final var priceFeed = mappingsToScopeFeed.get(mappingsPubKey);
      final var previousContext = mappingsContextByPriceFeed.put(priceFeed, mappingContext);
      if (previousContext == null) {
        persistMappings(mappingContext);
      } else if (mappingContext.isAfter(previousContext) && !Arrays.equals(previousContext.data(), mappingContext.data())) {
        final var previousEntries = previousContext.scopeEntries();
        final var newEntries = mappingContext.scopeEntries();
        final int numEntries = newEntries.numEntries();
        if (previousEntries.numEntries() != numEntries) {
          throw new IllegalStateException(String.format(
              "Scope Mappings %s changed in length from %d to %d.",
              mappingsPubKey, previousEntries.numEntries(), numEntries
          ));
        }

        final var changes = IntStream.range(0, numEntries).mapToObj(i -> {
          final var prevEntry = previousEntries.scopeEntry(i);
          final var newEntry = newEntries.scopeEntry(i);
          if (prevEntry.equals(newEntry)) {
            return null;
          } else {
            final var affectedReserves = reservesUsingFeedIndex(priceFeed, i);
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
              mappingsPubKey.toBase58(),
              numChanges,
              Long.toUnsignedString(mappingContext.slot()),
              String.join(",\n", changes)
          );
          logger.log(INFO, msg);
          // TODO retry handling.
          notifyClient.postMsg(msg);
          persistMappings(mappingContext);
        }
      }
    } finally {
      lock.unlock();
    }
  }

  private void removePreviousEntry(final ReserveContext previousContext) {
    final var reservePubKey = previousContext.pubKey();
    final var scopeConfiguration = previousContext.tokenInfo().scopeConfiguration();
    final var allReserves = reservesUsingScopeIndex.get(scopeConfiguration.priceFeed());
    for (final short index : scopeConfiguration.priceChain()) {
      if (index < 0) {
        break;
      }
      allReserves.get(index).remove(reservePubKey);
    }
    for (final short index : scopeConfiguration.twapChain()) {
      if (index < 0) {
        break;
      }
      allReserves.get(index).remove(reservePubKey);
    }
  }

  private Map<PublicKey, ReserveContext> reservesUsingIndex(final AtomicReferenceArray<Map<PublicKey, ReserveContext>> allReserves,
                                                            final short index) {
    final var reserves = allReserves.get(index);
    if (reserves == null) {
      final var newMap = new HashMap<PublicKey, ReserveContext>();
      allReserves.set(index, newMap);
      return newMap;
    } else {
      return reserves;
    }
  }

  private void addEntry(final ReserveContext reserveContext) {
    final var key = reserveContext.pubKey();
    final var scopeConfiguration = reserveContext.tokenInfo().scopeConfiguration();
    final var allReserves = reservesUsingScopeIndex.get(scopeConfiguration.priceFeed());
    for (final short index : scopeConfiguration.priceChain()) {
      if (index < 0) {
        break;
      }
      reservesUsingIndex(allReserves, index).put(key, reserveContext);
    }
    for (final short index : scopeConfiguration.twapChain()) {
      if (index < 0) {
        break;
      }
      reservesUsingIndex(allReserves, index).put(key, reserveContext);
    }
  }

  private void deleteScopeConfiguration(final Configuration configuration) {
    final var configurationKey = configuration._address();
    final var configJson = configurationToJson(configuration);
    final var msg = String.format("""
        {
          "event": "Scope Configuration Deleted",
          "config": %s
        }""", configJson
    );
    logger.log(INFO, msg);
    try {
      Files.delete(FileUtils.resolveAccountPath(configurationsPath, configurationKey));
    } catch (final IOException e) {
      logger.log(WARNING, "Failed to delete Scope configuration.", e);
    }
  }

  private void deleteOracleMappings(final MappingsContext mappingsContext) {
    final var scopeEntries = mappingsContext.scopeEntries();
    final var mappingsKey = mappingsContext.publicKey();
    final var priceFeed = mappingsToScopeFeed.remove(mappingsKey);
    this.scopeFeedToMappings.remove(priceFeed);
    final var affectedReserves = reserveContextMap.values().stream().<String>mapMulti((reserveContext, downstream) -> {
      if (reserveContext.tokenInfo().scopeConfiguration().priceFeed().equals(priceFeed)) {
        downstream.accept(reserveContext.pubKey().toBase58());
      }
    }).collect(Collectors.joining("\", \""));

    if (!affectedReserves.isBlank()) {
      final var msg = String.format("""
              {
                "event": "Scope Mappings Deleted",
                "mapping": "%s",
                "priceFeed": "%s",
                "reserves": ["%s"]
              }""",
          mappingsKey.toBase58(),
          priceFeed.toBase58(),
          affectedReserves
      );
      logger.log(INFO, msg);
      notifyClient.postMsg(msg);
    }
    try {
      Files.delete(FileUtils.resolveAccountPath(mappingsPath, mappingsKey));
    } catch (IOException e) {
      logger.log(WARNING, "Failed to delete Scope Mappings.", e);
    }
  }

  @Override
  public void run() {
    final var reserveAccountFilters = List.of(Reserve.SIZE_FILTER, Reserve.DISCRIMINATOR_FILTER);
    var accountsNeededList = new ArrayList<>(this.accountsNeededSet);

    NEW_CONFIG:
    for (; ; ) {
      final var reserveAccountsFuture = rpcCaller.courteousCall(
          rpcClient -> rpcClient.getProgramAccounts(kLendProgram, reserveAccountFilters),
          "Kamino Reserve's"
      );
      final var accountsNeeded = accountsNeededList;
      final var accountInfoList = rpcCaller.courteousCall(
          rpcClient -> rpcClient.getAccounts(accountsNeeded),
          "Scope accounts"
      ).join();

      final var reserveAccounts = reserveAccountsFuture.join();
      final var mutatedReserves = new ArrayList<ReserveContext>(reserveAccounts.size());
      final var presentReserves = HashSet.<PublicKey>newHashSet(reserveAccounts.size());
      final int numPreviousReserves = reserveContextMap.size();
      for (final var reserveAccountInfo : reserveAccounts) {
        final var reservePubKey = reserveAccountInfo.pubKey();
        presentReserves.add(reservePubKey);
        final var reserve = Reserve.read(reserveAccountInfo);

        final var reserveContext = ReserveContext.createContext(reserve, mappingsContextByPriceFeed);
        if (reserveContext == null) { // Need OracleMappings Account
          final var scopeConfiguration = reserve.config().tokenInfo().scopeConfiguration();
          final var priceFeedKey = scopeConfiguration.priceFeed();
          var oracleMappings = scopeFeedToMappings.get(priceFeedKey);
          if (oracleMappings == null) {
            logger.log(WARNING, "Fetching missing Scope Configuration for price feed " + priceFeedKey);
            final var filters = List.of(
                Configuration.SIZE_FILTER,
                Configuration.createOracleMappingsFilter(priceFeedKey)
            );
            final var configAccountList = rpcCaller.courteousGet(
                rpcClient -> rpcClient.getProgramAccounts(scopeProgram, filters),
                "Scope Configuration for price feed" + priceFeedKey
            );
            final int numConfigAccounts = configAccountList.size();
            if (numConfigAccounts == 0) {
              logger.log(WARNING, "Failed to find Scope Configuration for price feed " + priceFeedKey);
              continue;
            } else if (numConfigAccounts > 1) {

              logger.log(WARNING, String.format("""
                          Found multiple Scope Configurations ["%s"] for price feed %s.""",
                      configAccountList.stream().map(AccountInfo::pubKey).map(PublicKey::toBase58).collect(Collectors.joining("\", \"")),
                      priceFeedKey
                  )
              );
              continue;
            } else {
              final var config = Configuration.read(configAccountList.getFirst());
              syncConfigMappings(config);
              oracleMappings = config.oracleMappings();
            }
          }

          if (this.accountsNeededSet.add(oracleMappings)) {
            accountsNeededList = new ArrayList<>(this.accountsNeededSet);
            continue NEW_CONFIG;
          } else {
            continue; // Oracle Mapping does not exist onchain.
          }
        }

        if (numPreviousReserves == 0) {
          reserveContextMap.put(reservePubKey, reserveContext);
        } else {
          final var previousContext = reserveContextMap.get(reservePubKey);
          if (previousContext != null) {
            if (previousContext.changed(reserveContext)) {
              removePreviousEntry(previousContext);
              mutatedReserves.add(reserveContext);
            } else {
              continue;
            }
          }
        }

        addEntry(reserveContext);
      }

      int i = 0;
      int accountsDeleted = 0;
      for (final var accountInfo : accountInfoList) {
        if (accountInfo == null) {
          ++accountsDeleted;
          final var key = accountsNeededList.get(i);
          this.accountsNeededSet.remove(key);
          final var deletedConfig = configurationMap.remove(key);
          if (deletedConfig != null) {
            deleteScopeConfiguration(deletedConfig);
          } else {
            final var priceFeed = mappingsToScopeFeed.get(key);
            if (priceFeed != null) {
              final var deletedMapping = mappingsContextByPriceFeed.remove(priceFeed);
              if (deletedMapping != null) {
                deleteOracleMappings(deletedMapping);
              }
            }
          }
        } else {
          accept(accountInfo);
        }
        ++i;
      }
      if (accountsDeleted > 0) {
        accountsNeededList = new ArrayList<>(this.accountsNeededSet);
      }

      int numReserveEdits = 0;
      if (numPreviousReserves == 0) {
        numReserveEdits = reserveContextMap.size();
      } else {
        for (final var previousContext : reserveContextMap.values()) {
          if (!presentReserves.contains(previousContext.pubKey())) {
            removePreviousEntry(previousContext);
            ++numReserveEdits;
          }
        }

        for (final var reserveContext : mutatedReserves) {
          final var previous = reserveContextMap.put(reserveContext.pubKey(), reserveContext);
          if (previous != null) {
            handleReserveChange(previous, reserveContext);
            ++numReserveEdits;
          }
        }
      }

      if (numReserveEdits > 0) {
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
            }).collect(Collectors.joining(",\n"));

        try {
          Files.writeString(
              reserveContextsFilePath,
              String.format("""
                  [
                  %s]
                  """, marketsJson
              ),
              CREATE, TRUNCATE_EXISTING, WRITE
          );
        } catch (final IOException e) {
          logger.log(ERROR, "Failed to write Kamino Markets Reserve Scope Price Chains.", e);
        }
      }

      try {
        NANOSECONDS.sleep(pollingDelayNanos);
      } catch (final InterruptedException e) {
        return;
      }
    }
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
                  "oracle": "%s",
                  "twapEnabled": %b""",
            e.oracleType().name(),
            e.oracle(),
            e.twapEnabled()
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
              default -> prefix + "\n}";
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
                "source": %s,
                "cap": %s,
                "floor": %s
              }""",
          cappedFloored.oracleType().name(),
          toJson(cappedFloored.sourceEntry()),
          toJson(cappedFloored.capEntry()),
          toJson(cappedFloored.flooredEntry())
      );
      case MostRecentOf mostRecentOf -> {
        final var prefix = String.format("""
                {
                  "type": "%s",
                  "sources": %s,
                  "maxDivergenceBps": %d,
                  "sourcesMaxAgeS": %d,""",
            mostRecentOf.oracleType().name(),
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
      case Deprecated _ -> """
          {
            "type": "Deprecated"
          }""";
      case DiscountToMaturity discountToMaturity -> String.format("""
              {
                "type": "%s",
                "discountPerYearBps": %d,
                "maturityTimestamp": %d
              }""",
          discountToMaturity.oracleType().name(),
          discountToMaturity.discountPerYearBps(),
          discountToMaturity.maturityTimestamp()
      );
      case FixedPrice fixedPrice -> String.format("""
              {
                "type": "%s",
                "value": %d,
                "exp": %d,
                "decimal": "%s"
              }""",
          fixedPrice.oracleType().name(),
          fixedPrice.value(), fixedPrice.exp(),
          fixedPrice.decimal().toPlainString()
      );
      case NotYetSupported notYetSupported -> String.format("""
              {
                "type": "%s",
                "oracle": "%s",
                "twapSource": %s,
                "twapEnabled": %b,
                "refPrice": %s,
                "generic": "%s"
              }""",
          notYetSupported.oracleType().name(),
          notYetSupported.priceAccount(),
          toJson(notYetSupported.twapSource()),
          notYetSupported.twapEnabled(),
          toJson(notYetSupported.refPrice()),
          Base64.getEncoder().encodeToString(notYetSupported.generic())
      );
      case ScopeTwap scopeTwap -> String.format("""
              {
                "type": "%s",
                "source": %s
              }""",
          scopeTwap.oracleType().name(),
          toJson(scopeTwap.sourceEntry())
      );
      case Unused _ -> """
          {
            "type": "Unused"
          }""";
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

  @Override
  public void subscribe(final SolanaRpcWebsocket websocket) {
    websocket.programSubscribe(
        scopeProgram, List.of(OracleMappings.SIZE_FILTER, OracleMappings.DISCRIMINATOR_FILTER), this
    );
    websocket.programSubscribe(
        scopeProgram, List.of(Configuration.SIZE_FILTER, Configuration.DISCRIMINATOR_FILTER), this
    );
  }
}
