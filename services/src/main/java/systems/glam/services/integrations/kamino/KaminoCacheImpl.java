package systems.glam.services.integrations.kamino;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.kamino.KaminoAccounts;
import software.sava.idl.clients.kamino.lend.gen.types.Reserve;
import software.sava.idl.clients.kamino.lend.gen.types.TokenInfo;
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
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.services.io.FileUtils;
import systems.glam.services.oracles.scope.FeedIndexes;
import systems.glam.services.oracles.scope.MappingsContext;
import systems.glam.services.oracles.scope.ScopeFeedContext;
import systems.glam.services.rpc.AccountConsumer;
import systems.glam.services.rpc.AccountFetcher;

import java.io.IOException;
import java.math.BigInteger;
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

import static java.lang.System.Logger.Level.*;
import static java.nio.file.StandardOpenOption.*;

final class KaminoCacheImpl implements KaminoCache, AccountConsumer {

  static final System.Logger logger = System.getLogger(KaminoCache.class.getName());

  private final RpcCaller rpcCaller;
  private final AccountFetcher accountFetcher;
  private final PublicKey kLendProgram;
  private final PublicKey scopeProgram;
  private final PublicKey kVaultsProgram;
  private final ProgramAccountsRequest<byte[]> reservesRequest;
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

  private final Map<PublicKey, KaminoListener> listeners;
  private final Map<PublicKey, Map<PublicKey, KaminoListener>> specificReserveListeners;
  private final Map<PublicKey, Map<PublicKey, KaminoListener>> vaultListeners;

  KaminoCacheImpl(final RpcCaller rpcCaller,
                  final AccountFetcher accountFetcher,
                  final PublicKey kLendProgram,
                  final PublicKey scopeProgram,
                  final PublicKey kVaultsProgram,
                  final ProgramAccountsRequest<byte[]> reservesRequest,
                  final ProgramAccountsRequest<byte[]> kVaultsRequest,
                  final Duration pollingDelay,
                  final Path configurationsPath,
                  final Path mappingsPath,
                  final Path reserveContextsFilePath,
                  final Map<PublicKey, ScopeFeedContext> feedContextMap,
                  final ConcurrentMap<PublicKey, MappingsContext> mappingsContextMap,
                  final ConcurrentMap<PublicKey, ReserveContext> reserveContextMap,
                  final ConcurrentMap<PublicKey, KaminoVaultContext> vaultStateContextMap) {
    this.rpcCaller = rpcCaller;
    this.accountFetcher = accountFetcher;
    this.kLendProgram = kLendProgram;
    this.scopeProgram = scopeProgram;
    this.kVaultsProgram = kVaultsProgram;
    this.reservesRequest = reservesRequest;
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
    this.listeners = new ConcurrentHashMap<>();
    this.specificReserveListeners = new ConcurrentHashMap<>();
    this.vaultListeners = new ConcurrentHashMap<>();
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
      final var bestFeedIndexes = priceFeedContextMap.values().stream().<FeedIndexes>mapMulti((scopeFeedContext, downstream) -> {
        final var feedIndexes = scopeFeedContext.indexes(mint, oracle, oracleType);
        if (feedIndexes != null) {
          downstream.accept(feedIndexes);
        }
      }).sorted().findFirst().orElse(null);
      if (bestFeedIndexes != null) {
        return bestFeedIndexes;
      } else {
        final short[] indexes = new short[]{-1, -1, -1, -1};
        int i = 0;
        for (final var mappingsContext : mappingsContextMap.values()) {
          final var scopeEntries = mappingsContext.scopeEntries();
          final int numEntries = scopeEntries.numEntries();
          for (int e = 0; e < numEntries; ++e) {
            final var entry = scopeEntries.scopeEntry(e);
            if (entry instanceof OracleEntry oracleEntry) {
              if (oracleEntry.oracleType() == oracleType && oracleEntry.oracle().equals(oracle)) {
                indexes[i] = (short) oracleEntry.index();
                if (++i == indexes.length) {
                  break;
                }
              }
            }
          }
          if (i > 0) {
            final var scopeFeedContext = this.priceFeedContextMap.get(mappingsContext.publicKey());
            return new FeedIndexes(
                scopeFeedContext.readPriceFeed(),
                scopeFeedContext.readOracleMappings(),
                indexes,
                BigInteger.ZERO
            );
          }
        }
        return null;
      }
    } finally {
      readLock.unlock();
    }
  }

  private void deleteScopeConfiguration(final PublicKey deletedAccount, final ScopeFeedContext scopeFeedContext) {
    writeLock.lock();
    try {
      removeConfig(scopeFeedContext);
    } finally {
      writeLock.unlock();
    }
    for (final var listener : listeners.values()) {
      listener.onScopeAccountDeleted(deletedAccount, scopeFeedContext);
    }
    if (configurationsPath != null) {
      try {
        Files.deleteIfExists(FileUtils.resolveAccountPath(configurationsPath, scopeFeedContext.configurationKey()));
      } catch (final IOException e) {
        logger.log(WARNING, "Failed to delete Scope Configuration.", e);
      }
    }
    if (mappingsPath != null) {
      try {
        Files.deleteIfExists(FileUtils.resolveAccountPath(mappingsPath, scopeFeedContext.oracleMappings()));
      } catch (final IOException e) {
        logger.log(WARNING, "Failed to delete Scope Mappings.", e);
      }
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
      for (final var listener : listeners.values()) {
        listener.onNewScopeConfiguration(scopeFeedContext.configurationKey(), scopeFeedContext);
      }
    } else if (!witness.isStaleOrUnchanged(slot, data)) {
      removeConfig(witness);
      final var scopeFeedContext = ScopeFeedContext.createContext(
          slot, data,
          configurationKey
      );
      for (final var listener : listeners.values()) {
        listener.onScopeConfigurationChange(witness, scopeFeedContext);
      }
    }
  }

  private void persistMappings(final MappingsContext mappingContext) {
    if (mappingsPath != null) {
      final var mappingsPath = FileUtils.resolveAccountPath(this.mappingsPath, mappingContext.publicKey());
      try {
        Files.write(mappingsPath, mappingContext.data(), CREATE, TRUNCATE_EXISTING, WRITE);
      } catch (final IOException e) {
        logger.log(WARNING, "Failed to persist mappings.", e);
      }
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
      if (Long.compareUnsigned(witness.slot(), reserveContext.slot()) < 0) {
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
              notifyReserveChange(witness, reserveContext, changes);
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

  private void notifyReserveChange(final ReserveContext previous,
                                   final ReserveContext reserveContext,
                                   final Set<ReserveChange> changes) {
    for (final var listener : listeners.values()) {
      listener.onReserveChange(previous, reserveContext, changes);
    }
    final var listeners = this.specificReserveListeners.get(reserveContext.pubKey());
    if (listeners != null) {
      for (final var listener : listeners.values()) {
        listener.onReserveChange(previous, reserveContext, changes);
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


    final KaminoVaultContext kaminoVaultContext;
    if (previous == null) {
      kaminoVaultContext = KaminoVaultContext.createContext(
          slot, data, accountInfo.pubKey(), sharesMint
      );
    } else {
      kaminoVaultContext = previous.createIfChanged(slot, data);
      if (kaminoVaultContext == previous) {
        return;
      }
    }

    vaultStateContextMap.merge(
        sharesMint,
        kaminoVaultContext,
        (a, b) -> Long.compareUnsigned(a.slot(), b.slot()) >= 0 ? a : b
    );

    if (previous != null && !Arrays.equals(previous.reserves(), kaminoVaultContext.reserves())) {
      final var listeners = vaultListeners.get(sharesMint);
      if (listeners != null) {
        for (final var listener : listeners.values()) {
          listener.onKaminoVaultChange(kaminoVaultContext);
        }
      }
    }
  }

  @Override
  public void subscribeToVault(final PublicKey vaultMint, final KaminoListener listener) {
    final var listeners = vaultListeners.computeIfAbsent(vaultMint, _ -> new ConcurrentHashMap<>());
    listeners.put(listener.key(), listener);
  }

  @Override
  public void unSubscribeFromVault(final PublicKey vaultMint, final KaminoListener listener) {
    final var listeners = vaultListeners.get(vaultMint);
    if (listeners != null) {
      listeners.remove(listener.key());
    }
  }

  @Override
  public void subscribe(final KaminoListener listener) {
    listeners.put(listener.key(), listener);
  }

  @Override
  public void unsubscribe(final KaminoListener listener) {
    listeners.put(listener.key(), listener);
  }

  @Override
  public void subscribeToReserve(final PublicKey reserveKey, final KaminoListener listener) {
    final var listeners = specificReserveListeners.computeIfAbsent(reserveKey, _ -> new ConcurrentHashMap<>());
    listeners.put(listener.key(), listener);
  }

  @Override
  public void unSubscribeToReserve(final PublicKey reserveKey, final KaminoListener listener) {
    final var listeners = specificReserveListeners.get(reserveKey);
    if (listeners != null) {
      listeners.remove(listener.key());
    }
  }

  @Override
  public void refreshVaults(final Set<PublicKey> vaultMints) {
    final var accountsNeeded = new ArrayList<PublicKey>(vaultMints.size());
    for (final var vaultMint : vaultMints) {
      final var vaultContext = vaultStateContextMap.remove(vaultMint);
      if (vaultContext != null) {
        accountsNeeded.add(vaultContext.readVaultState().publicKey());
      }
    }
    accountFetcher.priorityQueue(accountsNeeded, this);
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
        logger.log(WARNING, "Unhandled Kamino Account: " + accountInfo.pubKey());
      }
    } catch (final RuntimeException ex) {
      logger.log(ERROR, "Failed to handle Scope account " + accountInfo.pubKey(), ex);
    }
  }

  @Override
  public void accept(final List<AccountInfo<byte[]>> accounts, final Map<PublicKey, AccountInfo<byte[]>> accountMap) {
    for (final var accountInfo : accounts) {
      if (AccountFetcher.isNull(accountInfo)) {
        continue;
      }
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
      }
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
              deleteScopeConfiguration(key, feedContext);
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


        final var kVaultAccounts = kVaultsFuture.join();
        final var reserveAccountsFutures = rpcCaller.courteousCall(
            rpcClient -> rpcClient.getProgramAccounts(reservesRequest),
            "rpcClient#getKaminoReserves"
        );

        for (final var accountInfo : kVaultAccounts) {
          handleVaultStateChange(accountInfo);
        }

        readLock.lock();
        try {
          if (noReserveChanges()) {
            logger.log(INFO, "No changes to Scope Configurations.");
          }
        } finally {
          readLock.unlock();
        }

        final var reserveAccounts = reserveAccountsFutures.join();
        for (final var accountInfo : reserveAccounts) {
          final var reserveContext = ReserveContext.createContext(accountInfo, mappingsContextMap);
          updateIfChanged(reserveContext);
        }

        readLock.lock();
        try {
          if (noReserveChanges()) {
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

  private boolean noReserveChanges() {
    if (this.asyncReserveUpdates.getAndSet(0) > 0) {
      final var marketsJson = marketsJson(reserveContextMap);
      if (reserveContextsFilePath != null) {
        writeMarketsJSON(reserveContextsFilePath, marketsJson);
      }
      for (final var listener : listeners.values()) {
        listener.onCachedReserveJsonChange(marketsJson);
      }
      return false;
    } else {
      return true;
    }
  }

  private static String marketsJson(final Map<PublicKey, ReserveContext> reserveContextMap) {
    return reserveContextMap.values().stream()
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
  }

  static void writeReserves(final Path reserveContextsFilePath,
                            final Map<PublicKey, ReserveContext> reserveContextMap) {
    final var marketsJson = marketsJson(reserveContextMap);
    writeMarketsJSON(reserveContextsFilePath, marketsJson);
  }

  private static void writeMarketsJSON(final Path reserveContextsFilePath, final String marketsJson) {
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

  private void notifyMappingsChange(final ScopeFeedContext scopeFeedContext,
                                    final MappingsContext witness,
                                    final MappingsContext mappingContext) {
    for (final var listener : listeners.values()) {
      listener.onMappingChange(scopeFeedContext, witness, mappingContext);
    }
  }

  static void analyzeMarkets(final Map<PublicKey, ReserveContext> reserveContextMap) {
    final var byMarket = reserveContextMap.values().stream()
        .collect(Collectors.groupingBy(ReserveContext::market));

    record MarketOracles(PublicKey market, Set<PublicKey> switchboard, Set<PublicKey> pyth, Set<PublicKey> scope) {

      String toJson() {
        return String.format("""
                {
                 "market": "%s",
                 "switchboard": [%s],
                 "pyth": [%s],
                 "scope": [%s]
                }""",
            market.toBase58(),
            switchboard.isEmpty() ? "" : switchboard.stream().map(PublicKey::toBase58).collect(Collectors.joining("\",\"", "\"", "\"")),
            pyth.isEmpty() ? "" : pyth.stream().map(PublicKey::toBase58).collect(Collectors.joining("\",\"", "\"", "\"")),
            scope.isEmpty() ? "" : scope.stream().map(PublicKey::toBase58).collect(Collectors.joining("\",\"", "\"", "\""))
        );
      }
    }

    final class MarketOraclesBuilder {

      Set<PublicKey> switchboard;
      Set<PublicKey> pyth;
      Set<PublicKey> scope;

      int addAccounts(final TokenInfo tokenInfo) {
        int numOracleSources = 0;
        final var priceFeed = tokenInfo.scopeConfiguration().priceFeed();
        if (!priceFeed.equals(PublicKey.NONE) && !priceFeed.equals(KaminoAccounts.NULL_KEY)) {
          if (scope == null) {
            scope = new HashSet<>();
          }
          scope.add(priceFeed);
          ++numOracleSources;
        }
        final var switchboard = tokenInfo.switchboardConfiguration().priceAggregator();
        if (!switchboard.equals(PublicKey.NONE) && !switchboard.equals(KaminoAccounts.NULL_KEY)) {
          if (this.switchboard == null) {
            this.switchboard = new HashSet<>();
          }
          this.switchboard.add(switchboard);
          ++numOracleSources;
        }
        final var pyth = tokenInfo.pythConfiguration().price();
        if (!pyth.equals(PublicKey.NONE) && !pyth.equals(KaminoAccounts.NULL_KEY)) {
          if (this.pyth == null) {
            this.pyth = new HashSet<>();
          }
          this.pyth.add(pyth);
          ++numOracleSources;
        }
        return numOracleSources;
      }

      int numAccounts() {
        int numAccounts = 0;
        if (switchboard != null) {
          numAccounts += switchboard.size();
        }
        if (pyth != null) {
          numAccounts += pyth.size();
        }
        if (scope != null) {
          numAccounts += scope.size();
        }
        return numAccounts;
      }

      MarketOracles build(final PublicKey market) {
        return new MarketOracles(
            market,
            switchboard == null ? Set.of() : Set.copyOf(switchboard),
            pyth == null ? Set.of() : Set.copyOf(pyth),
            scope == null ? Set.of() : Set.copyOf(scope)
        );
      }
    }

    final var marketOracles = HashMap.<PublicKey, MarketOracles>newHashMap(byMarket.size());

    for (final var entry : byMarket.entrySet()) {
      final var market = entry.getKey();
      final var builder = new MarketOraclesBuilder();
      for (final var reserveContext : entry.getValue()) {
        final var tokenInfo = reserveContext.tokenInfo();
        builder.addAccounts(tokenInfo);
      }
      marketOracles.put(market, builder.build(market));
    }

    final var json = marketOracles.values().stream()
        .map(MarketOracles::toJson)
        .map(indented -> indented.indent(2).stripTrailing())
        .collect(Collectors.joining(",\n", "[\n", "\n]"));
    System.out.println(json);
  }
}
