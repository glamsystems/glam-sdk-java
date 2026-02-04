package systems.glam.services.oracles.scope;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.kamino.KaminoAccounts;
import software.sava.idl.clients.kamino.lend.gen.types.Reserve;
import software.sava.idl.clients.kamino.lend.gen.types.ReserveConfig;
import software.sava.idl.clients.kamino.lend.gen.types.TokenInfo;
import software.sava.idl.clients.kamino.scope.entries.*;
import software.sava.idl.clients.kamino.scope.entries.Deprecated;
import software.sava.idl.clients.kamino.scope.gen.types.Configuration;
import software.sava.idl.clients.kamino.scope.gen.types.EmaType;
import software.sava.idl.clients.kamino.scope.gen.types.OracleMappings;
import software.sava.idl.clients.kamino.scope.gen.types.OracleType;
import software.sava.rpc.json.http.client.ProgramAccountsRequest;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import software.sava.services.core.net.http.NotifyClient;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.services.io.FileUtils;
import systems.glam.services.rpc.AccountFetcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.System.Logger.Level.*;
import static java.nio.file.StandardOpenOption.*;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static software.sava.core.accounts.PublicKey.PUBLIC_KEY_LENGTH;
import static systems.glam.services.oracles.scope.ScopeMonitorServiceEntrypoint.writeConfigurations;

final class ScopeMonitorServiceImpl implements ScopeMonitorService {

  static final System.Logger logger = System.getLogger(ScopeMonitorService.class.getName());

  private final NotifyClient notifyClient;
  private final RpcCaller rpcCaller;
  private final AccountFetcher accountFetcher;
  private final PublicKey kLendProgram;
  private final PublicKey scopeProgram;
  private final Set<PublicKey> accountsNeededSet;
  private final long pollingDelayNanos;
  private final Path configurationsPath;
  private final Path mappingsPath;
  private final Path reserveContextsFilePath;
  private final ConcurrentSkipListSet<PublicKey> ignorePriceFeeds;
  private final ConcurrentMap<PublicKey, ReserveContext> reserveContextMap;
  private final ConcurrentMap<PublicKey, MappingsContext> mappingsContextMap;
  private final ConcurrentMap<PublicKey, FeedContext> priceFeedContextMap;
  private final ReentrantLock lock;

  ScopeMonitorServiceImpl(final NotifyClient notifyClient,
                          final RpcCaller rpcCaller,
                          final AccountFetcher accountFetcher,
                          final PublicKey kLendProgram,
                          final PublicKey scopeProgram,
                          final long pollingDelayNanos,
                          final Path configurationsPath,
                          final Path mappingsPath,
                          final Path reserveContextsFilePath,
                          final Map<PublicKey, FeedContext> feedContextMap,
                          final ConcurrentMap<PublicKey, MappingsContext> mappingsContextMap,
                          final ConcurrentMap<PublicKey, ReserveContext> reserveContextMap) {
    this.notifyClient = notifyClient;
    this.rpcCaller = rpcCaller;
    this.accountFetcher = accountFetcher;
    this.kLendProgram = kLendProgram;
    this.scopeProgram = scopeProgram;
    this.pollingDelayNanos = pollingDelayNanos;
    this.configurationsPath = configurationsPath;
    this.mappingsPath = mappingsPath;
    this.reserveContextsFilePath = reserveContextsFilePath;
    this.ignorePriceFeeds = new ConcurrentSkipListSet<>();
    ignorePriceFeeds.add(KaminoAccounts.NULL_KEY);
    ignorePriceFeeds.add(PublicKey.NONE);
    this.reserveContextMap = reserveContextMap;
    this.mappingsContextMap = mappingsContextMap;
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
      indexReserveContext(reserveContext);
    }
    this.lock = new ReentrantLock();
  }

  private void indexReserveContext(final ReserveContext reserveContext) {
    final var scopeConfiguration = reserveContext.tokenInfo().scopeConfiguration();
    final var feedContext = priceFeedContextMap.get(scopeConfiguration.priceFeed());
    feedContext.indexReserveContext(reserveContext);
  }

  @Override
  public FeedIndexes indexes(final PublicKey mint, final PublicKey oracle, final OracleType oracleType) {
    return priceFeedContextMap.values().stream().<FeedIndexes>mapMulti((feedContext, downstream) -> {
      final var feedIndexes = feedContext.indexes(mint, oracle, oracleType);
      if (feedIndexes != null) {
        downstream.accept(feedIndexes);
      }
    }).sorted().findFirst().orElse(null);
  }

  private void handleReserveChange(final ReserveContext previous, final ReserveContext reserveContext) {
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

  private void deleteScopeConfiguration(final FeedContext feedContext) {
    lock.lock();
    try {
      removeConfig(feedContext);
    } finally {
      lock.unlock();
    }
    final var configJson = feedContext.toJson();
    final var msg = String.format("""
        {
          "event": "Scope Account Deleted",
          "config": %s
        }""", configJson
    );
    logger.log(INFO, msg);
    try {
      Files.deleteIfExists(FileUtils.resolveAccountPath(configurationsPath, feedContext.configurationKey()));
    } catch (final IOException e) {
      logger.log(WARNING, "Failed to delete Scope Configuration.", e);
    }
    logger.log(INFO, msg);
    try {
      Files.deleteIfExists(FileUtils.resolveAccountPath(mappingsPath, feedContext.oracleMappings()));
    } catch (final IOException e) {
      logger.log(WARNING, "Failed to delete Scope Mappings.", e);
    }
  }

  private void removeConfig(final FeedContext feedContext) {
    final var priceFeed = feedContext.priceFeed();
    this.ignorePriceFeeds.add(priceFeed);
    final var configKey = feedContext.configurationKey();
    final var mappingsKey = feedContext.oracleMappings();
    this.accountsNeededSet.remove(configKey);
    this.accountsNeededSet.remove(mappingsKey);
    this.priceFeedContextMap.remove(mappingsKey);
    this.priceFeedContextMap.remove(priceFeed);
    this.priceFeedContextMap.remove(configKey);
    this.mappingsContextMap.remove(priceFeed);
    this.mappingsContextMap.remove(mappingsKey);
  }

  private FeedContext handleConfigurationChange(final long slot, final PublicKey configurationKey, final byte[] data) {
    var witness = priceFeedContextMap.get(configurationKey);
    final FeedContext feedContext;
    if (witness == null) {
      feedContext = FeedContext.createContext(
          slot, data,
          configurationKey
      );
      lock.lock();
      try {
        witness = priceFeedContextMap.putIfAbsent(feedContext.priceFeed(), feedContext);
        if (witness == null) {
          this.priceFeedContextMap.put(feedContext.oracleMappings(), feedContext);
          this.priceFeedContextMap.put(configurationKey, feedContext);
          this.accountsNeededSet.add(configurationKey);
        } else if (witness.isStaleOrUnchanged(slot, data)) {
          return witness;
        } else {
          removeConfig(witness);
        }
      } finally {
        lock.unlock();
      }
    } else if (witness.isStaleOrUnchanged(slot, data)) {
      return witness;
    } else {
      removeConfig(witness);
      feedContext = null;
    }

    final var configJson = FeedContext.configurationToJson(slot, configurationKey, data);
    final String msg;
    if (witness == null) {
      msg = String.format("""
          {
            "event": "New Scope Configuration",
            "config": %s
          }""", configJson
      );
      logger.log(INFO, msg);
      try {
        writeConfigurations(configurationsPath, configurationKey, data);
      } catch (final IOException e) {
        logger.log(WARNING, "Failed to persist Scope configuration.", e);
      }
    } else {
      msg = String.format("""
              {
                "event": "Scope Configuration Change",
                "previous": %s,
                "latest": %s
              }""",
          witness.toJson(),
          configJson
      );
      logger.log(ERROR, msg);
      // TODO: Trigger Alert
    }
    notifyClient.postMsg(msg);

    return feedContext;
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
    final FeedContext priceFeedContext;
    lock.lock();
    try {
      priceFeedContext = priceFeedContextMap.get(mappingsKey);
      if (priceFeedContext == null) {
        return;
      }
      witness = mappingsContextMap.get(mappingsKey);
      if (witness == null || witness.changed(accountInfo)) {
        mappingContext = MappingsContext.createContext(accountInfo);
        mappingsContextMap.put(priceFeedContext.priceFeed(), mappingContext);
        mappingsContextMap.put(mappingsKey, mappingContext);
      } else {
        return;
      }
    } finally {
      lock.unlock();
    }

    if (witness == null) {
      persistMappings(mappingContext);
    } else {
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
          final var feedContext = priceFeedContextMap.get(priceFeedContext.priceFeed());
          final var affectedReserves = feedContext.reservesForIndex(i);
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
        persistMappings(mappingContext);
      }
    }
  }

  private void removePreviousEntry(final ReserveContext previousContext) {
    final var feedContext = priceFeedContextMap.get(previousContext.priceFeed());
    feedContext.removePreviousEntry(previousContext);
  }

  private boolean updateIfChanged(final ReserveContext reserveContext) {
    final var key = reserveContext.pubKey();

    var witness = reserveContextMap.putIfAbsent(key, reserveContext);
    if (witness == null) {
      lock.lock();
      try {
        if (reserveContextMap.get(key) != reserveContext) {
          indexReserveContext(reserveContext);
          return true;
        } else {
          return false;
        }
      } finally {
        lock.unlock();
      }
    }
    for (; ; ) {
      if (witness.isBefore(reserveContext)) {
        final var changes = witness.changed(reserveContext);
        if (changes.isEmpty()) {
          return false;
        } else {
          lock.lock();
          try {
            final var previous = reserveContextMap.get(key);
            if (previous != witness) {
              witness = previous;
              continue;
            }
            reserveContextMap.put(key, reserveContext);
            removePreviousEntry(witness);
            indexReserveContext(reserveContext);
            handleReserveChange(witness, reserveContext);
            return true;
          } finally {
            lock.unlock();
          }
        }
      } else {
        return false;
      }
    }
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
  }

  @Override
  public void accept(final AccountInfo<byte[]> accountInfo) {
    try {
      final byte[] data = accountInfo.data();
      if (data.length == Reserve.CONFIG_PADDING_OFFSET && Reserve.DISCRIMINATOR.equals(data, 0)) {
        final var reserveContext = ReserveContext.createContext(accountInfo, mappingsContextMap);
        updateIfChanged(reserveContext);
      } else if (data.length == OracleMappings.BYTES && OracleMappings.DISCRIMINATOR.equals(data, 0)) {
        handleMappingChange(accountInfo);
      } else if (data.length == Configuration.BYTES && Configuration.DISCRIMINATOR.equals(data, 0)) {
        handleConfigurationChange(accountInfo.context().slot(), accountInfo.pubKey(), data);
      }
    } catch (final RuntimeException ex) {
      logger.log(ERROR, "Failed to handle Scope account " + accountInfo.pubKey(), ex);
    }
  }

  @Override
  public void run() {
    final var reserveAccountsRequest = ProgramAccountsRequest.build()
        .filters(List.of(Reserve.SIZE_FILTER, Reserve.DISCRIMINATOR_FILTER))
        .programId(kLendProgram)
        .dataSliceLength(0, Reserve.CONFIG_PADDING_OFFSET)
        .createRequest();

    final var priceFeedKeyBytes = new byte[PUBLIC_KEY_LENGTH];
    final int priceFeedKeyFromOffset = Reserve.CONFIG_OFFSET + ReserveConfig.TOKEN_INFO_OFFSET + TokenInfo.SCOPE_CONFIGURATION_OFFSET;
    final int priceFeedKeyToOffset = priceFeedKeyFromOffset + PUBLIC_KEY_LENGTH;
    final byte[] nullKeyBytes = PublicKey.NONE.toByteArray();
    final byte[] nilKeyBytes = KaminoAccounts.NULL_KEY.toByteArray();

    var accountsNeededList = new ArrayList<>(this.accountsNeededSet);

    for (; ; ) {
      final var reserveAccountsFuture = rpcCaller.courteousCall(
          rpcClient -> rpcClient.getProgramAccounts(reserveAccountsRequest),
          "Kamino Reserve's"
      );

      final var reserveAccounts = reserveAccountsFuture.join();

      for (final var reserveAccountInfo : reserveAccounts) {
        final byte[] data = reserveAccountInfo.data();
        if (Arrays.equals(
            data, priceFeedKeyFromOffset, priceFeedKeyToOffset,
            nullKeyBytes, 0, PUBLIC_KEY_LENGTH
        )) {
          continue;
        } else if (Arrays.equals(
            data, priceFeedKeyFromOffset, priceFeedKeyToOffset,
            nilKeyBytes, 0, PUBLIC_KEY_LENGTH
        )) {
          continue;
        }

        System.arraycopy(reserveAccountInfo.data(), priceFeedKeyFromOffset, priceFeedKeyBytes, 0, PUBLIC_KEY_LENGTH);
        final var priceFeedKey = PublicKey.createPubKey(priceFeedKeyBytes);

        if (!this.mappingsContextMap.containsKey(priceFeedKey)) {
          if (ignorePriceFeeds.contains(priceFeedKey)) {
            continue;
          }
          final PublicKey oracleMappingsKey;
          var feedContext = this.priceFeedContextMap.get(priceFeedKey);
          if (feedContext != null) {
            oracleMappingsKey = feedContext.oracleMappings();
          } else {
            logger.log(WARNING, "Fetching missing Scope Configuration for price feed " + priceFeedKey);
            final var request = ProgramAccountsRequest.build()
                .programId(scopeProgram)
                .filters(List.of(
                    Configuration.SIZE_FILTER,
                    Configuration.createOracleMappingsFilter(priceFeedKey)
                ))
                .dataSliceLength(0, Configuration.PADDING_OFFSET)
                .createRequest();
            final var configAccountList = rpcCaller.courteousGet(
                rpcClient -> rpcClient.getProgramAccounts(request),
                "Scope Configuration for price feed" + priceFeedKey
            );
            final int numConfigAccounts = configAccountList.size();
            if (numConfigAccounts == 0) {
              ignorePriceFeeds.add(priceFeedKey);
              logger.log(WARNING, "Failed to find Scope Configuration for price feed " + priceFeedKey);
              continue;
            } else if (numConfigAccounts > 1) {
              ignorePriceFeeds.add(priceFeedKey);
              logger.log(WARNING, String.format("""
                          Found multiple Scope Configurations ["%s"] for price feed %s.""",
                      configAccountList.stream().map(AccountInfo::pubKey).map(PublicKey::toBase58).collect(Collectors.joining("\", \"")),
                      priceFeedKey
                  )
              );
              continue;
            } else {
              final var accountInfo = configAccountList.getFirst();
              feedContext = handleConfigurationChange(accountInfo.context().slot(), accountInfo.pubKey(), accountInfo.data());
              if (feedContext == null) {
                ignorePriceFeeds.add(priceFeedKey);
                continue;
              }
              oracleMappingsKey = feedContext.oracleMappings();
            }
          }

          if (this.accountsNeededSet.add(oracleMappingsKey)) {
            accountsNeededList = new ArrayList<>(this.accountsNeededSet);
          }
        }
      }

      final var accountInfoListFuture = accountFetcher.priorityQueue(accountsNeededList);

      final var fetchReservesSlot = reserveAccounts.isEmpty()
          ? 0
          : reserveAccounts.getFirst().context().slot();


      int accountsDeleted = 0;
      int i = 0;
      final var accountInfoList = accountInfoListFuture.join().accounts();
      // Handle all new OracleMappings first to ensure reserves have full context.
      for (final var accountInfo : accountInfoList) {
        if (accountInfo == null || accountInfo.data() == null || accountInfo.data().length == 0) {
          ++accountsDeleted;
          final var key = accountsNeededList.get(i);
          this.accountsNeededSet.remove(key);
          final var feedContext = priceFeedContextMap.remove(key);
          if (feedContext != null) {
            deleteScopeConfiguration(feedContext);
          } else {
            this.mappingsContextMap.remove(key);
          }
        } else {
          accept(accountInfo);
        }
        ++i;
      }

      int numReserveEdits = 0;
      final var presentReserves = HashSet.<PublicKey>newHashSet(reserveAccounts.size());
      for (final var reserveAccountInfo : reserveAccounts) {
        final var reservePubKey = reserveAccountInfo.pubKey();
        presentReserves.add(reservePubKey);
        final var reserveContext = ReserveContext.createContext(reserveAccountInfo, this.mappingsContextMap);
        if (updateIfChanged(reserveContext)) {
          ++numReserveEdits;
        }
      }

      if (accountsDeleted > 0) {
        accountsNeededList = new ArrayList<>(this.accountsNeededSet);
      }

      final var iterator = reserveContextMap.entrySet().iterator();
      while (iterator.hasNext()) {
        final var entry = iterator.next();
        final var reserveContext = entry.getValue();
        if (Long.compareUnsigned(reserveContext.slot(), fetchReservesSlot) < 0 && !presentReserves.contains(entry.getKey())) {
          iterator.remove();
          removePreviousEntry(reserveContext);
          ++numReserveEdits;
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
      } else if (accountsDeleted == 0) {
        logger.log(INFO, "No changes to Scope Reserves.");
      }

      try {
        NANOSECONDS.sleep(pollingDelayNanos);
      } catch (final InterruptedException e) {
        return;
      }
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
          }""", e.index());
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
          }""", e.index());
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
}
