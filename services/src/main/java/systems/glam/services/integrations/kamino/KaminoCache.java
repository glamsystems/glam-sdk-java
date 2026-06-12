package systems.glam.services.integrations.kamino;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.kamino.KaminoAccounts;
import software.sava.idl.clients.kamino.lend.gen.types.Reserve;
import software.sava.idl.clients.kamino.lend.gen.types.ReserveConfig;
import software.sava.idl.clients.kamino.lend.gen.types.TokenInfo;
import software.sava.idl.clients.kamino.scope.entries.ScopeReader;
import software.sava.idl.clients.kamino.scope.gen.types.Configuration;
import software.sava.idl.clients.kamino.scope.gen.types.OracleMappings;
import software.sava.idl.clients.kamino.vaults.gen.types.VaultState;
import software.sava.rpc.json.http.client.ProgramAccountsRequest;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.services.io.FileUtils;
import systems.glam.services.oracles.scope.MappingsContext;
import systems.glam.services.oracles.scope.ScopeFeedContext;
import systems.glam.services.rpc.AccountFetcher;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

import static software.sava.core.accounts.PublicKey.PUBLIC_KEY_LENGTH;
import static systems.glam.services.integrations.kamino.KaminoCacheImpl.*;

public interface KaminoCache extends ScopeAggregateIndexes, Runnable, Consumer<AccountInfo<byte[]>> {

  private static ProgramAccountsRequest<byte[]> scopeConfigurationAccountsRequest(final KaminoAccounts kaminoAccounts) {
    return ProgramAccountsRequest.build()
        .filters(List.of(Configuration.SIZE_FILTER, Configuration.DISCRIMINATOR_FILTER))
        .programId(kaminoAccounts.scopePricesProgram())
        .dataSliceLength(0, MIN_CONFIGURATION_LENGTH)
        .createRequest();
  }

  private static ProgramAccountsRequest<byte[]> reserveAccountsRequest(final KaminoAccounts kaminoAccounts) {
    return ProgramAccountsRequest.build()
        .filters(List.of(Reserve.SIZE_FILTER, Reserve.DISCRIMINATOR_FILTER))
        .programId(kaminoAccounts.kLendProgram())
        .dataSliceLength(0, MIN_RESERVE_LENGTH)
        .createRequest();
  }

  private static ProgramAccountsRequest<byte[]> kVaultAccountsRequest(final KaminoAccounts kaminoAccounts) {
    return ProgramAccountsRequest.build()
        .filters(List.of(VaultState.SIZE_FILTER, VaultState.DISCRIMINATOR_FILTER))
        .programId(kaminoAccounts.kVaultsProgram())
        .dataSliceLength(0, MIN_VAULT_STATE_LENGTH)
        .createRequest();
  }

  static CompletableFuture<KaminoCache> initService(final RpcCaller rpcCaller,
                                                    final AccountFetcher accountFetcher,
                                                    final KaminoAccounts kaminoAccounts,
                                                    final Duration pollingDelay) {
    return CompletableFuture.supplyAsync(() -> {
      final var kLendProgram = kaminoAccounts.kLendProgram();
      final var scopeProgram = kaminoAccounts.scopePricesProgram();
      final var kVaultsProgram = kaminoAccounts.kVaultsProgram();

      // Note: New Configurations will be discovered indirectly via Kamino Lending Reserves.
      final var configAccountsRequest = scopeConfigurationAccountsRequest(kaminoAccounts);
      final var scopeConfigurationsFuture = rpcCaller.courteousCall(
          rpcClient -> rpcClient.getProgramAccounts(configAccountsRequest),
          "Scope Configuration accounts"
      );

      final var KVaultsRequest = kVaultAccountsRequest(kaminoAccounts);
      final var vaultStateAccountsFuture = rpcCaller.courteousCall(
          rpcClient -> rpcClient.getProgramAccounts(KVaultsRequest),
          "rpcClient#getKaminoVaultAccounts"
      );

      final var reserveAccountsRequest = reserveAccountsRequest(kaminoAccounts);
      final var reserveAccountsFuture = rpcCaller.courteousCall(
          rpcClient -> rpcClient.getProgramAccounts(reserveAccountsRequest),
          "rpcClient#getKaminoReserves"
      );

      final var configAccounts = scopeConfigurationsFuture.join();
      final var mappingAccountKeys = HashSet.<PublicKey>newHashSet(configAccounts.size());
      final var feedContextMap = HashMap.<PublicKey, ScopeFeedContext>newHashMap(configAccounts.size() * 3);

      for (final var accountInfo : configAccounts) {
        if (accountInfo != null) {
          final byte[] data = accountInfo.data();
          if (data.length != MIN_CONFIGURATION_LENGTH || !Configuration.DISCRIMINATOR.equals(data, 0)) {
            throw new IllegalStateException(String.format(
                "%s is not a valid Scope Configuration account.", accountInfo.pubKey()
            ));
          }
          final var feedContext = ScopeFeedContext.createContext(accountInfo);
          feedContextMap.put(feedContext.configurationKey(), feedContext);
          final var oracleMappings = feedContext.oracleMappings();
          mappingAccountKeys.add(oracleMappings);
          feedContextMap.put(oracleMappings, feedContext);
          feedContextMap.put(feedContext.priceFeed(), feedContext);
        }
      }

      final var reserveAccounts = reserveAccountsFuture.join();
      final var reserveContextMap = new ConcurrentHashMap<PublicKey, ReserveContext>(Integer.highestOneBit(reserveAccounts.size()) << 1);

      final int priceFeedKeyFromOffset = Reserve.CONFIG_OFFSET + ReserveConfig.TOKEN_INFO_OFFSET + TokenInfo.SCOPE_CONFIGURATION_OFFSET;
      final int priceFeedKeyToOffset = priceFeedKeyFromOffset + PUBLIC_KEY_LENGTH;
      final byte[] nullKeyBytes = PublicKey.NONE.toByteArray();
      final byte[] nilKeyBytes = KaminoAccounts.NULL_KEY.toByteArray();

      final var noMappings = Map.<PublicKey, MappingsContext>of();
      for (final var reserveAccountInfo : reserveAccounts) {
        final byte[] data = reserveAccountInfo.data();
        if (Arrays.equals(
            data, priceFeedKeyFromOffset, priceFeedKeyToOffset,
            nullKeyBytes, 0, nullKeyBytes.length
        ) || Arrays.equals(
            data, priceFeedKeyFromOffset, priceFeedKeyToOffset,
            nilKeyBytes, 0, nilKeyBytes.length
        )) {
          final var reserveContext = ReserveContext.createContext(reserveAccountInfo, noMappings);
          reserveContextMap.put(reserveContext.pubKey(), reserveContext);
        }
      }

      final var mappingAccountList = List.copyOf(mappingAccountKeys);
      final var mappingsFuture = rpcCaller.courteousCall(
          rpcClient -> rpcClient.getAccounts(mappingAccountList),
          "Oracle Mappings accounts"
      );

      final var mappingsContextMap = new ConcurrentHashMap<PublicKey, MappingsContext>(mappingAccountList.size() << 1);
      for (final var accountInfo : mappingsFuture.join()) {
        if (accountInfo == null) {
          throw new IllegalStateException("Oracle Mappings account not found.");
        }
        final byte[] data = accountInfo.data();
        if (!OracleMappings.DISCRIMINATOR.equals(data, 0) || data.length != OracleMappings.BYTES) {
          throw new IllegalStateException(String.format(
              "%s is not a valid Scope OracleMappings account.", accountInfo.pubKey()
          ));
        }
        final var mappingsKey = accountInfo.pubKey();
        final var priceFeedContext = feedContextMap.get(mappingsKey);
        final var mappingsContext = MappingsContext.createContext(accountInfo);
        mappingsContextMap.put(priceFeedContext.priceFeed(), mappingsContext);
        mappingsContextMap.put(mappingsKey, mappingsContext);
      }

      final var vaultStateAccounts = vaultStateAccountsFuture.join();
      final var vaultStateMap = new ConcurrentHashMap<PublicKey, KaminoVaultContext>(Integer.highestOneBit(vaultStateAccounts.size()) << 1);
      for (final var accountInfo : vaultStateAccounts) {
        final byte[] data = accountInfo.data();
        if (data.length != MIN_VAULT_STATE_LENGTH || !VaultState.DISCRIMINATOR.equals(data, 0)) {
          throw new IllegalStateException(String.format(
              "%s is not a valid Kamino Vault account.", accountInfo.pubKey()
          ));
        }
        final var vaultStateContext = KaminoVaultContext.createContext(accountInfo);
        vaultStateMap.put(vaultStateContext.sharesMint(), vaultStateContext);
      }

      final var cache = new KaminoCacheImpl(
          rpcCaller,
          accountFetcher,
          kLendProgram,
          scopeProgram,
          kVaultsProgram,
          reserveAccountsRequest,
          KVaultsRequest,
          pollingDelay,
          null,
          null,
          null,
          feedContextMap,
          mappingsContextMap,
          reserveContextMap,
          vaultStateMap
      );
      accountFetcher.listenToAll(cache);
      return cache;
    });
  }

  static CompletableFuture<KaminoCache> initService(final Path kaminoAccountsPath,
                                                    final RpcCaller rpcCaller,
                                                    final AccountFetcher accountFetcher,
                                                    final KaminoAccounts kaminoAccounts,
                                                    final Duration pollingDelay) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        final var reserveDataFilePath = kaminoAccountsPath.resolve("reserves");
        final var scopeAccountsPath = kaminoAccountsPath.resolve("scope");
        final var configurationsPath = scopeAccountsPath.resolve("configurations");
        final var mappingsPath = scopeAccountsPath.resolve("mappings");
        final var kLendProgram = kaminoAccounts.kLendProgram();
        final var scopeProgram = kaminoAccounts.scopePricesProgram();
        final var kVaultsProgram = kaminoAccounts.kVaultsProgram();

        final var KVaultsRequest = kVaultAccountsRequest(kaminoAccounts);
        final var vaultStateAccountsFuture = rpcCaller.courteousCall(
            rpcClient -> rpcClient.getProgramAccounts(KVaultsRequest),
            "rpcClient#getKaminoVaultAccounts"
        );

        final var reserveAccountsRequest = reserveAccountsRequest(kaminoAccounts);

        final int priceFeedKeyFromOffset = Reserve.CONFIG_OFFSET + ReserveConfig.TOKEN_INFO_OFFSET + TokenInfo.SCOPE_CONFIGURATION_OFFSET;
        final int priceFeedKeyToOffset = priceFeedKeyFromOffset + PUBLIC_KEY_LENGTH;
        final byte[] nullKeyBytes = PublicKey.NONE.toByteArray();
        final byte[] nilKeyBytes = KaminoAccounts.NULL_KEY.toByteArray();

        final ConcurrentMap<PublicKey, ReserveContext> reserveContextMap;
        final List<AccountInfo<byte[]>> reserveAccounts;
        final Set<PublicKey> priceFeedsNeeded;
        if (Files.exists(reserveDataFilePath)) {
          reserveContextMap = new ConcurrentHashMap<>(512);
          reserveAccounts = List.of();
          priceFeedsNeeded = Set.of();
        } else {
          reserveAccounts = rpcCaller.courteousGet(
              rpcClient -> rpcClient.getProgramAccounts(reserveAccountsRequest),
              "rpcClient#getKaminoReserves"
          );
          reserveContextMap = new ConcurrentHashMap<>(Integer.highestOneBit(reserveAccounts.size()) << 1);

          final var noMappings = Map.<PublicKey, MappingsContext>of();
          priceFeedsNeeded = HashSet.newHashSet(8);

          for (final var reserveAccountInfo : reserveAccounts) {
            final byte[] data = reserveAccountInfo.data();
            if (Arrays.equals(
                data, priceFeedKeyFromOffset, priceFeedKeyToOffset,
                nullKeyBytes, 0, nullKeyBytes.length
            ) || Arrays.equals(
                data, priceFeedKeyFromOffset, priceFeedKeyToOffset,
                nilKeyBytes, 0, nilKeyBytes.length
            )) {
              final var reserveContext = ReserveContext.createContext(reserveAccountInfo, noMappings);
              reserveContextMap.put(reserveContext.pubKey(), reserveContext);
            } else {
              final var priceFeedKey = PublicKey.readPubKey(data, priceFeedKeyFromOffset);
              priceFeedsNeeded.add(priceFeedKey);
            }
          }
        }

        final var feedContextMap = KaminoCache.loadFeedContexts(configurationsPath);
        // Note: New Configurations will be discovered indirectly via Kamino Lending Reserves.
        final CompletableFuture<List<AccountInfo<byte[]>>> scopeConfigurationsFuture;
        if (feedContextMap.keySet().containsAll(priceFeedsNeeded) && !feedContextMap.isEmpty()) {
          scopeConfigurationsFuture = null;
        } else {
          final var configAccountsRequest = scopeConfigurationAccountsRequest(kaminoAccounts);
          scopeConfigurationsFuture = rpcCaller.courteousCall(
              rpcClient -> rpcClient.getProgramAccounts(configAccountsRequest),
              "Scope Configuration accounts"
          );
        }

        if (scopeConfigurationsFuture != null) {
          final var configurationAccounts = scopeConfigurationsFuture.join();
          for (final var accountInfo : configurationAccounts) {
            if (accountInfo != null) {
              final byte[] data = accountInfo.data();
              if (data.length != MIN_CONFIGURATION_LENGTH || !Configuration.DISCRIMINATOR.equals(data, 0)) {
                throw new IllegalStateException(String.format(
                    "%s is not a valid Scope Configuration account.", accountInfo.pubKey()
                ));
              }
              final var feedContext = ScopeFeedContext.createContext(accountInfo);
              feedContextMap.put(feedContext.configurationKey(), feedContext);
              feedContextMap.put(feedContext.oracleMappings(), feedContext);
              feedContextMap.put(feedContext.priceFeed(), feedContext);
              writeScopeConfiguration(configurationsPath, feedContext);
            }
          }
        }

        final var mappingsContextMap = loadMappings(mappingsPath, feedContextMap);

        final var missingMappings = feedContextMap.values().stream().<PublicKey>mapMulti((configuration, downstream) -> {
          if (!mappingsContextMap.containsKey(configuration.priceFeed())) {
            downstream.accept(configuration.oracleMappings());
          }
        }).distinct().toList();

        if (!missingMappings.isEmpty()) {
          final var mappingsFuture = rpcCaller.courteousCall(
              rpcClient -> rpcClient.getAccounts(missingMappings),
              "Oracle Mappings accounts"
          );

          for (final var accountInfo : mappingsFuture.join()) {
            if (accountInfo == null) {
              throw new IllegalStateException("Oracle Mappings account not found.");
            }
            final byte[] data = accountInfo.data();
            if (!OracleMappings.DISCRIMINATOR.equals(data, 0) || data.length != OracleMappings.BYTES) {
              throw new IllegalStateException(String.format(
                  "%s is not a valid Scope OracleMappings account.", accountInfo.pubKey()
              ));
            }
            final var mappingsKey = accountInfo.pubKey();
            final var priceFeedContext = feedContextMap.get(mappingsKey);
            final var mappingsContext = MappingsContext.createContext(accountInfo);
            mappingsContextMap.put(priceFeedContext.priceFeed(), mappingsContext);
            mappingsContextMap.put(mappingsKey, mappingsContext);
            FileUtils.writeCompressedAccountData(mappingsPath, mappingsKey, mappingsContext.data());
          }
        }

        if (reserveAccounts.isEmpty()) {
          loadReserves(reserveDataFilePath, mappingsContextMap, reserveContextMap);
        } else {
          for (final var reserveAccountInfo : reserveAccounts) {
            if (!reserveContextMap.containsKey(reserveAccountInfo.pubKey())) {
              final var reserveContext = ReserveContext.createContext(reserveAccountInfo, mappingsContextMap);
              reserveContextMap.put(reserveContext.pubKey(), reserveContext);
              KaminoCacheImpl.persistReserve(reserveDataFilePath, reserveContext);
            }
          }
        }

        final var vaultStateAccounts = vaultStateAccountsFuture.join();
        final var vaultStateMap = new ConcurrentHashMap<PublicKey, KaminoVaultContext>(Integer.highestOneBit(vaultStateAccounts.size()) << 1);
        for (final var accountInfo : vaultStateAccounts) {
          final byte[] data = accountInfo.data();
          if (data.length != MIN_VAULT_STATE_LENGTH || !VaultState.DISCRIMINATOR.equals(data, 0)) {
            throw new IllegalStateException(String.format(
                "%s is not a valid Kamino Vault account.", accountInfo.pubKey()
            ));
          }
          final var vaultStateContext = KaminoVaultContext.createContext(accountInfo);
          vaultStateMap.put(vaultStateContext.sharesMint(), vaultStateContext);
        }

        final var cache = new KaminoCacheImpl(
            rpcCaller,
            accountFetcher,
            kLendProgram,
            scopeProgram,
            kVaultsProgram,
            reserveAccountsRequest,
            KVaultsRequest,
            pollingDelay,
            configurationsPath,
            mappingsPath,
            reserveDataFilePath,
            feedContextMap,
            mappingsContextMap,
            reserveContextMap,
            vaultStateMap
        );
        accountFetcher.listenToAll(cache);
        return cache;
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    });
  }

  private static Map<PublicKey, ScopeFeedContext> loadFeedContexts(final Path configurationsPath) throws IOException {
    final var feedContextMap = new HashMap<PublicKey, ScopeFeedContext>();
    if (Files.notExists(configurationsPath)) {
      Files.createDirectories(configurationsPath);
    } else {
      try (final var paths = Files.list(configurationsPath)) {
        paths.forEach(path -> {
          final var accountData = FileUtils.readAccountData(path);
          if (accountData.isAccountAtLeast(Configuration.DISCRIMINATOR, MIN_CONFIGURATION_LENGTH)) {
            final var feedContext = accountData.read(ScopeFeedContext::createContext);
            feedContextMap.put(feedContext.configurationKey(), feedContext);
            feedContextMap.put(feedContext.oracleMappings(), feedContext);
            feedContextMap.put(feedContext.priceFeed(), feedContext);
            FileUtils.compressIfNeeded(configurationsPath, path, accountData);
          } else {
            try {
              Files.delete(path);
            } catch (final IOException e) {
              throw new UncheckedIOException(e);
            }
          }
        });
      }
    }
    return feedContextMap;
  }

  private static void loadReserves(final Path reserveDataFilePath,
                                   final Map<PublicKey, MappingsContext> mappingsContextMap,
                                   final ConcurrentMap<PublicKey, ReserveContext> reserveContextMap) throws IOException {
    if (Files.notExists(reserveDataFilePath)) {
      Files.createDirectories(reserveDataFilePath);
      return;
    }
    try (final var marketDirs = Files.list(reserveDataFilePath)) {
      marketDirs.forEach(marketDir -> {
        if (!Files.isDirectory(marketDir)) {
          return;
        }
        try (final var reserveFiles = Files.list(marketDir)) {
          reserveFiles.parallel().forEach(reserveFile -> {
            final var accountData = FileUtils.readAccountData(reserveFile);
            if (accountData.isAccountAtLeast(Reserve.DISCRIMINATOR, MIN_RESERVE_LENGTH)) {
              final var reserveContext = ReserveContext.createContext(
                  accountData.pubKey(), accountData.data(), mappingsContextMap
              );
              reserveContextMap.put(reserveContext.pubKey(), reserveContext);
              FileUtils.compressIfNeeded(marketDir, reserveFile, accountData);
            } else {
              try {
                Files.delete(reserveFile);
              } catch (final IOException e) {
                throw new UncheckedIOException(e);
              }
            }
          });
        } catch (final IOException e) {
          throw new UncheckedIOException(e);
        }
      });
    }
  }

  private static ConcurrentMap<PublicKey, MappingsContext> loadMappings(final Path mappingsPath,
                                                                        final Map<PublicKey, ScopeFeedContext> feedContextMap) throws IOException {
    final var mappingsContextByPriceFeed = new ConcurrentHashMap<PublicKey, MappingsContext>();
    if (Files.notExists(mappingsPath)) {
      Files.createDirectories(mappingsPath);
    } else {
      try (final var paths = Files.list(mappingsPath)) {
        paths.forEach(path -> {
          final var accountData = FileUtils.readAccountData(path);
          if (accountData.isAccountExact(OracleMappings.DISCRIMINATOR, OracleMappings.BYTES)) {
            final var mappings = accountData.read(OracleMappings::read);
            final var scopeEntries = ScopeReader.parseEntries(0, mappings);
            final var feedContext = feedContextMap.get(mappings._address());
            final var mappingsKey = mappings._address();
            final var mappingsContext = new MappingsContext(mappingsKey, accountData.data(), scopeEntries);
            mappingsContextByPriceFeed.put(mappingsKey, mappingsContext);
            mappingsContextByPriceFeed.put(feedContext.priceFeed(), mappingsContext);
            FileUtils.compressIfNeeded(mappingsPath, path, accountData);
          } else {
            try {
              Files.delete(path);
            } catch (final IOException e) {
              throw new UncheckedIOException(e);
            }
          }
        });
      }
    }
    return mappingsContextByPriceFeed;
  }

  Path reserveDataFilePath();

  Path mappingsPath();

  Path configurationsPath();

  Collection<ReserveContext> reserveContexts();

  ReserveContext reserveContext(final PublicKey pubKey);

  ReserveContext acceptReserve(final AccountInfo<byte[]> accountInfo);

  KaminoVaultContext vaultForShareMint(final PublicKey sharesMint);

  Collection<KaminoVaultContext> vaultContexts();

  void subscribe(final SolanaRpcWebsocket websocket);

  default void subscribeToAll(final KaminoListener listener) {
    subscribeToScope(listener);
    subscribeToReserves(listener);
    subscribeToVaults(listener);
  }

  default void unsubscribeFromAll(final KaminoListener listener) {
    unsubscribeFromScope(listener);
    unsubscribeFromReserves(listener);
    unsubscribeFromVaults(listener);
  }

  void subscribeToScope(final KaminoListener listener);

  void unsubscribeFromScope(final KaminoListener listener);

  void subscribeToReserves(final KaminoListener listener);

  void unsubscribeFromReserves(final KaminoListener listener);

  void subscribeToReserve(final PublicKey reserveKey, final KaminoListener listener);

  void unSubscribeToReserve(final PublicKey reserveKey, final KaminoListener listener);

  void subscribeToVaults(final KaminoListener listener);

  void unsubscribeFromVaults(final KaminoListener listener);

  void subscribeToVault(final PublicKey vaultMint, final KaminoListener listener);

  void unSubscribeFromVault(final PublicKey vaultMint, final KaminoListener listener);

  void refreshVaults(final Set<PublicKey> vaultMints);
}
