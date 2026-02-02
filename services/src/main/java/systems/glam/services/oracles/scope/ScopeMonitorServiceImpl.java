package systems.glam.services.oracles.scope;

import software.sava.core.accounts.PublicKey;
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
import software.sava.rpc.json.http.client.SolanaRpcClient;
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
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.System.Logger.Level.*;
import static java.nio.file.StandardOpenOption.*;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static software.sava.core.accounts.PublicKey.PUBLIC_KEY_LENGTH;
import static systems.glam.services.oracles.scope.ReserveContext.NULL_KEY;
import static systems.glam.services.oracles.scope.ScopeMonitorServiceEntrypoint.writeConfigurations;

final class ScopeMonitorServiceImpl implements ScopeMonitorService {

  static final System.Logger logger = System.getLogger(ScopeMonitorService.class.getName());

  private static final Comparator<ReserveContext> RESERVE_CONTEXT_BY_LIQUIDITY = (a, b) -> Long.compareUnsigned(b.totalCollateral(), a.totalCollateral());

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
  private final ConcurrentMap<PublicKey, Configuration> configurationMap;
  private final ConcurrentSkipListSet<PublicKey> ignorePriceFeeds;
  private final ConcurrentMap<PublicKey, PublicKey> scopeFeedToMappings;
  private final ConcurrentMap<PublicKey, PublicKey> mappingsToScopeFeed;
  private final ConcurrentMap<PublicKey, MappingsContext> mappingsContextByPriceFeed;
  private final ConcurrentMap<PublicKey, ReserveContext> reserveContextMap;
  private final ConcurrentMap<PublicKey, ReserveContext[]> reserveContextsByMint;
  private final ConcurrentMap<PublicKey, AtomicReferenceArray<Map<PublicKey, ReserveContext>>> reservesUsingScopeIndex;
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
                          final Map<PublicKey, Configuration> scopeConfigurations,
                          final ConcurrentMap<PublicKey, MappingsContext> mappingsContextByPriceFeed,
                          final ConcurrentMap<PublicKey, ReserveContext> reserveContextMap) {
    this.notifyClient = notifyClient;
    this.rpcCaller = rpcCaller;
    this.accountFetcher = accountFetcher;
    this.kLendProgram = kLendProgram;
    this.scopeProgram = scopeProgram;
    this.accountsNeededSet = ConcurrentHashMap.newKeySet(SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS);
    this.pollingDelayNanos = pollingDelayNanos;
    this.configurationsPath = configurationsPath;
    this.mappingsPath = mappingsPath;
    this.reserveContextsFilePath = reserveContextsFilePath;
    this.scopeFeedToMappings = new ConcurrentHashMap<>();
    this.ignorePriceFeeds = new ConcurrentSkipListSet<>();
    ignorePriceFeeds.add(NULL_KEY);
    ignorePriceFeeds.add(PublicKey.NONE);
    this.mappingsToScopeFeed = new ConcurrentHashMap<>();
    this.mappingsContextByPriceFeed = mappingsContextByPriceFeed;
    this.reserveContextMap = reserveContextMap;
    this.reserveContextsByMint = new ConcurrentHashMap<>();
    this.reservesUsingScopeIndex = new ConcurrentHashMap<>();
    this.lock = new ReentrantLock();
    this.configurationMap = new ConcurrentHashMap<>();
    for (final var configuration : scopeConfigurations.values()) {
      if (configurationMap.put(configuration._address(), configuration) == null) {
        syncConfigMappings(configuration);
      }
    }
    for (final var reserveContext : reserveContextMap.values()) {
      indexReserveContext(reserveContext);
    }
  }

  @Override
  public short[] indexes(final PublicKey mint, final OracleType oracleType) {
    // TODO:
    //  * Handle nested OracleTypes, e.g. a MostRecentOf holding the desired type.

    final var reservesForMint = this.reserveContextsByMint.get(mint);
    if (reservesForMint == null) {
      return null;
    }
    final int[] oracleIndexes = Arrays.stream(reservesForMint).mapMultiToInt((reserveContext, downstream) -> {
      final var priceChains = reserveContext.priceChains();
      if (priceChains != null) {
        final var priceChain = priceChains.priceChain();
        for (int i = 0; i < priceChain.length; i++) {
          if (priceChain[i].oracleType() == oracleType) { // Contains oracle type?
            final int index = reserveContext.tokenInfo().scopeConfiguration().priceChain()[i];
            downstream.accept(index);
            return;
          }
        }
      }

    }).distinct().limit(4).toArray();

    if (oracleIndexes.length == 0) {
      return null;
    } else {
      final short[] indexes = new short[]{-1, -1, -1, -1};
      for (int i = 0; i < oracleIndexes.length; i++) {
        indexes[i] = (short) oracleIndexes[i];
      }
      return indexes;
    }
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
    lock.lock();
    try {
      // TODO: check slot
      scopeFeedToMappings.put(oraclePrices, mappings);
      mappingsToScopeFeed.put(mappings, oraclePrices);
      accountsNeededSet.add(configuration._address());
      accountsNeededSet.add(configuration.oracleMappings());
      reservesUsingScopeIndex.put(oraclePrices, new AtomicReferenceArray<>(OracleMappings.PRICE_INFO_ACCOUNTS_LEN));
    } finally {
      lock.unlock();
    }
  }

  private void handleConfigurationChange(final Configuration configuration) {
    // TODO: check slot
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
      // TODO: check slot
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

    lock.lock();
    try {
      final var reservesForMint = this.reserveContextsByMint.get(previousContext.mint());
      if (reservesForMint != null) {
        for (int i = 0; i < reservesForMint.length; ++i) {
          if (reservesForMint[i].pubKey().equals(reservePubKey)) {
            final var newArray = new ReserveContext[reservesForMint.length - 1];
            System.arraycopy(reservesForMint, 0, newArray, 0, i);
            System.arraycopy(reservesForMint, i + 1, newArray, i, reservesForMint.length - i - 1);
            this.reserveContextsByMint.put(previousContext.mint(), newArray);
            return;
          }
        }
      }
    } finally {
      lock.unlock();
    }
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

  private Map<PublicKey, ReserveContext> reservesUsingIndex(
      final AtomicReferenceArray<Map<PublicKey, ReserveContext>> allReserves,
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

  private void indexReserveContext(final ReserveContext reserveContext) {
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

    final var mint = reserveContext.mint();
    final var reservesForMint = this.reserveContextsByMint.get(mint);
    if (reservesForMint == null) {
      this.reserveContextsByMint.put(mint, new ReserveContext[]{reserveContext});
    } else {
      for (int i = 0; i < reservesForMint.length; ++i) {
        if (reservesForMint[i].pubKey().equals(key)) {
          final var newArray = new ReserveContext[reservesForMint.length];
          System.arraycopy(reservesForMint, 0, newArray, 0, reservesForMint.length);
          newArray[i] = reserveContext;
          Arrays.sort(newArray, RESERVE_CONTEXT_BY_LIQUIDITY);
          this.reserveContextsByMint.put(mint, newArray);
          return;
        }
      }
      final var newArray = new ReserveContext[reservesForMint.length + 1];
      System.arraycopy(reservesForMint, 0, newArray, 0, reservesForMint.length);
      newArray[reservesForMint.length] = reserveContext;
      Arrays.sort(newArray, RESERVE_CONTEXT_BY_LIQUIDITY);
      this.reserveContextsByMint.put(mint, newArray);
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
  public void accept(final AccountInfo<byte[]> accountInfo) {
    final byte[] data = accountInfo.data();
    if (data.length == Reserve.CONFIG_PADDING_OFFSET && Reserve.DISCRIMINATOR.equals(data, 0)) {
      final var reserveContext = ReserveContext.createContext(accountInfo, mappingsContextByPriceFeed);
      updateIfChanged(reserveContext);
    } else if (data.length == OracleMappings.BYTES && OracleMappings.DISCRIMINATOR.equals(data, 0)) {
      final var mappingContext = MappingsContext.createContext(accountInfo);
      handleMappingChange(mappingContext);
    } else if (data.length == Configuration.BYTES && Configuration.DISCRIMINATOR.equals(data, 0)) {
      final var configuration = Configuration.read(accountInfo);
      handleConfigurationChange(configuration);
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
    final byte[] nilKeyBytes = NULL_KEY.toByteArray();

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

        if (!this.mappingsContextByPriceFeed.containsKey(priceFeedKey) && !ignorePriceFeeds.contains(priceFeedKey)) {
          var oracleMappingsKey = this.scopeFeedToMappings.get(priceFeedKey);
          if (oracleMappingsKey == null) {
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
              final var config = Configuration.read(configAccountList.getFirst());
              syncConfigMappings(config);
              oracleMappingsKey = config.oracleMappings();
            }
          }

          if (this.accountsNeededSet.add(oracleMappingsKey)) {
            accountsNeededList = new ArrayList<>(this.accountsNeededSet);
          } else {
            ignorePriceFeeds.add(priceFeedKey); // Oracle Mapping does not exist onchain.
          }
        }
      }

      final var accountInfoListFuture = accountFetcher.priorityQueue(accountsNeededList);

      int numReserveEdits = 0;
      final var presentReserves = HashSet.<PublicKey>newHashSet(reserveAccounts.size());
      for (final var reserveAccountInfo : reserveAccounts) {
        final var reservePubKey = reserveAccountInfo.pubKey();
        presentReserves.add(reservePubKey);
        final var reserveContext = ReserveContext.createContext(reserveAccountInfo, this.mappingsContextByPriceFeed);
        if (updateIfChanged(reserveContext)) {
          ++numReserveEdits;
        }
      }

      final var fetchReservesSlot = reserveAccounts.isEmpty()
          ? 0
          : reserveAccounts.getFirst().context().slot();

      int accountsDeleted = 0;
      final var accountInfoList = accountInfoListFuture.join().accounts();
      int i = 0;
      for (final var accountInfo : accountInfoList) {
        if (accountInfo == null || accountInfo.data() == null || accountInfo.data().length == 0) {
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
                  "oracle": "%s"%s""",
            e.oracleType().name(),
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
                "twapSource": %s,%s
                "refPrice": %s,
                "generic": "%s"
              }""",
          notYetSupported.oracleType().name(),
          notYetSupported.priceAccount(),
          toJson(notYetSupported.twapSource()),
          toJson(notYetSupported.emaTypes()),
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
    websocket.programSubscribe( // TODO: Add data slice filter to sava websocket.
        kLendProgram, List.of(Reserve.SIZE_FILTER, Reserve.DISCRIMINATOR_FILTER), this
    );
  }
}
