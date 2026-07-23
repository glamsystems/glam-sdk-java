package systems.glam.services.integrations.kamino;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.kamino.lend.gen.types.Reserve;
import software.sava.idl.clients.kamino.scope.entries.OracleEntry;
import software.sava.idl.clients.kamino.scope.gen.types.Configuration;
import software.sava.idl.clients.kamino.scope.gen.types.OracleMappings;
import software.sava.idl.clients.kamino.scope.gen.types.OracleType;
import software.sava.idl.clients.kamino.vaults.gen.types.VaultState;
import software.sava.rpc.json.http.client.ProgramAccountsRequest;
import software.sava.rpc.json.http.client.SolanaRpcClient;
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
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.WARNING;

final class KaminoCacheImpl implements KaminoCache, AccountConsumer {

  static final System.Logger logger = System.getLogger(KaminoCache.class.getName());
  static final int MIN_CONFIGURATION_LENGTH = Configuration.PADDING_OFFSET;
  static final int MIN_RESERVE_LENGTH = Reserve.PADDING_OFFSET;
  static final int MIN_VAULT_STATE_LENGTH = VaultState.PADDING_2_OFFSET;

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
  private final Path reserveDataFilePath;
  private final ConcurrentMap<PublicKey, ReserveContext> reserveContextMap;
  private final ConcurrentMap<PublicKey, MappingsContext> mappingsContextMap;
  private final ConcurrentMap<PublicKey, ScopeFeedContext> priceFeedContextMap;
  private final ConcurrentMap<PublicKey, KaminoVaultContext> vaultStateContextMap;
  /// Package-private so tests can assert both views were released; a leaked
  /// lock blocks every other caller and no result assertion can see it.
  final ReentrantReadWriteLock lock;
  private final ReentrantReadWriteLock.WriteLock writeLock;
  private final Condition reserveScopeChangeCondition;
  private volatile int numReserveChanges;
  private final ReentrantReadWriteLock.ReadLock readLock;

  private final Map<PublicKey, KaminoListener> scopeListeners;
  private final Map<PublicKey, KaminoListener> reserveListeners;
  private final Map<PublicKey, Map<PublicKey, KaminoListener>> specificReserveListeners;
  private final Map<PublicKey, KaminoListener> vaultListeners;
  private final Map<PublicKey, Map<PublicKey, KaminoListener>> specificVaultListeners;

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
                  final Path reserveDataFilePath,
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
    this.reserveDataFilePath = reserveDataFilePath;
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
    this.lock = lock;
    this.writeLock = lock.writeLock();
    this.readLock = lock.readLock();
    this.reserveScopeChangeCondition = writeLock.newCondition();
    this.scopeListeners = new ConcurrentHashMap<>();
    this.reserveListeners = new ConcurrentHashMap<>();
    this.specificReserveListeners = new ConcurrentHashMap<>();
    this.vaultListeners = new ConcurrentHashMap<>();
    this.specificVaultListeners = new ConcurrentHashMap<>();
  }

  static void writeScopeConfiguration(final Path configurationsPath,
                                      final ScopeFeedContext scopeFeedContext) throws IOException {
    FileUtils.writeCompressedAccountData(
        configurationsPath, scopeFeedContext.configurationKey(), scopeFeedContext.configurationData()
    );
  }

  @Override
  public Path reserveDataFilePath() {
    return reserveDataFilePath;
  }

  @Override
  public Path mappingsPath() {
    return mappingsPath;
  }

  @Override
  public Path configurationsPath() {
    return configurationsPath;
  }

  @Override
  public Collection<ReserveContext> reserveContexts() {
    return reserveContextMap.values();
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
  public Collection<KaminoVaultContext> vaultContexts() {
    return List.copyOf(vaultStateContextMap.values());
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
    for (final var listener : scopeListeners.values()) {
      listener.onScopeAccountDeleted(deletedAccount, scopeFeedContext);
    }
    if (configurationsPath != null) {
      try {
        // persisted compressed: deleting the uncompressed name would let the
        // dropped configuration resurrect from disk on the next start
        Files.deleteIfExists(FileUtils.resolveCompressedAccountPath(configurationsPath, scopeFeedContext.configurationKey()));
      } catch (final IOException e) {
        logger.log(WARNING, "Failed to delete Scope Configuration.", e);
      }
    }
    if (mappingsPath != null) {
      try {
        Files.deleteIfExists(FileUtils.resolveCompressedAccountPath(mappingsPath, scopeFeedContext.oracleMappings()));
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
      for (final var listener : scopeListeners.values()) {
        listener.onNewScopeConfiguration(scopeFeedContext.configurationKey(), scopeFeedContext);
      }
    } else if (!witness.isStaleOrUnchanged(slot, data)) {
      removeConfig(witness);
      final var scopeFeedContext = ScopeFeedContext.createContext(
          slot, data,
          configurationKey
      );
      for (final var listener : scopeListeners.values()) {
        listener.onScopeConfigurationChange(witness, scopeFeedContext);
      }
    }
  }

  private void persistMappings(final MappingsContext mappingContext) {
    if (mappingsPath != null) {
      try {
        FileUtils.writeCompressedAccountData(
            this.mappingsPath, mappingContext.publicKey(), mappingContext.data()
        );
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
          this.numReserveChanges = numChanges;
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
    if (witness == null) { // New Reserve
      writeLock.lock();
      try {
        witness = reserveContextMap.get(key);
        if (witness == reserveContext) {
          final var feedContext = priceFeedContextMap.get(priceFeed);
          if (feedContext == null) {
            return;
          } else {
            feedContext.indexReserveContext(reserveContext);
            notifyNewReserve(reserveContext);
            persistReserve(reserveDataFilePath, reserveContext);
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
              persistReserve(reserveDataFilePath, reserveContext);
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

  private void notifyNewReserve(final ReserveContext reserveContext) {
    for (final var listener : reserveListeners.values()) {
      listener.onNewReserve(reserveContext);
    }
  }

  private void notifyReserveChange(final ReserveContext previous,
                                   final ReserveContext reserveContext,
                                   final Set<ReserveChange> changes) {
    for (final var listener : reserveListeners.values()) {
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

    if (previous == null) {
      final var listeners = specificVaultListeners.get(sharesMint);
      if (listeners != null) {
        for (final var listener : listeners.values()) {
          listener.onNewKaminoVault(kaminoVaultContext);
        }
      }
      for (final var listener : vaultListeners.values()) {
        listener.onNewKaminoVault(kaminoVaultContext);
      }
    } else if (!Arrays.equals(previous.reserves(), kaminoVaultContext.reserves())) {
      final var listeners = specificVaultListeners.get(sharesMint);
      if (listeners != null) {
        for (final var listener : listeners.values()) {
          listener.onKaminoVaultChange(previous, kaminoVaultContext);
        }
      }
      for (final var listener : vaultListeners.values()) {
        listener.onKaminoVaultChange(previous, kaminoVaultContext);
      }
    }
  }

  @Override
  public void subscribeToVaults(final KaminoListener listener) {
    vaultListeners.put(listener.key(), listener);
  }

  @Override
  public void unsubscribeFromVaults(final KaminoListener listener) {
    vaultListeners.remove(listener.key());
  }

  @Override
  public void subscribeToVault(final PublicKey vaultMint, final KaminoListener listener) {
    final var listeners = specificVaultListeners.computeIfAbsent(vaultMint, _ -> new ConcurrentHashMap<>());
    listeners.put(listener.key(), listener);
  }

  @Override
  public void unSubscribeFromVault(final PublicKey vaultMint, final KaminoListener listener) {
    final var listeners = specificVaultListeners.get(vaultMint);
    if (listeners != null) {
      listeners.remove(listener.key());
    }
  }

  @Override
  public void subscribeToScope(final KaminoListener listener) {
    scopeListeners.put(listener.key(), listener);
  }

  @Override
  public void unsubscribeFromScope(final KaminoListener listener) {
    scopeListeners.remove(listener.key());
  }

  @Override
  public void subscribeToReserves(final KaminoListener listener) {
    reserveListeners.put(listener.key(), listener);
  }

  @Override
  public void unsubscribeFromReserves(final KaminoListener listener) {
    reserveListeners.remove(listener.key());
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
    accountFetcher.priorityQueueBatchable(accountsNeeded, this);
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
  public void mutableKeysExceededMaxSize() {

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

        int accountsDeleted = 0;
        final int numAccounts = accountsNeededList.size();
        for (int from = 0, to; ; from = to) {
          to = Math.min(from + SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS, numAccounts);
          final var subList = accountsNeededList.subList(from, to);
          final var accountInfoListFuture = accountFetcher.priorityQueue(subList);

          final var accountMap = accountInfoListFuture.join().accountMap();
          for (final var accountNeeded : subList) {
            final var accountInfo = accountMap.get(accountNeeded);
            if (AccountFetcher.isNull(accountInfo)) {
              ++accountsDeleted;
              this.accountsNeededSet.remove(accountNeeded);
              final var feedContext = priceFeedContextMap.remove(accountNeeded);
              if (feedContext != null) {
                deleteScopeConfiguration(accountNeeded, feedContext);
              } else {
                this.mappingsContextMap.remove(accountNeeded);
                logger.log(WARNING, "Scope OracleMappings account has been deleted " + accountNeeded);
              }
            } else {
              accept(accountInfo);
            }
          }

          if (to == numAccounts) {
            break;
          }
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

        final var reserveAccounts = reserveAccountsFutures.join();
        for (final var accountInfo : reserveAccounts) {
          final var reserveContext = ReserveContext.createContext(accountInfo, mappingsContextMap);
          updateIfChanged(reserveContext);
        }

        writeLock.lock();
        try {
          for (long remaining = pollingDelayNanos; ; ) {
            if (this.numReserveChanges > 0) {
              this.numReserveChanges = 0;
              break;
            }
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

  static void persistReserve(final Path reserveContextsFilePath, final ReserveContext reserveContext) {
    if (reserveContextsFilePath == null) {
      // an RPC-only cache keeps nothing on disk
      return;
    }
    final var marketFilePath = reserveContextsFilePath.resolve(reserveContext.market().toBase58());
    try {
      if (Files.notExists(marketFilePath)) {
        Files.createDirectories(marketFilePath);
      }
      FileUtils.writeCompressedAccountData(
          marketFilePath, reserveContext.pubKey(), reserveContext.data()
      );
    } catch (final IOException e) {
      logger.log(ERROR, "Failed to write Kamino Markets Reserve Scope Price Chains.", e);
    }
  }

  private void notifyMappingsChange(final ScopeFeedContext scopeFeedContext,
                                    final MappingsContext witness,
                                    final MappingsContext mappingContext) {
    for (final var listener : scopeListeners.values()) {
      listener.onMappingChange(scopeFeedContext, witness, mappingContext);
    }
  }
}
