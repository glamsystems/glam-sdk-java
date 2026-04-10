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
import systems.glam.services.oracles.scope.parsers.KaminoReserveContextsParser;
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

import static java.nio.file.StandardOpenOption.*;
import static software.sava.core.accounts.PublicKey.PUBLIC_KEY_LENGTH;
import static systems.glam.services.integrations.kamino.KaminoCacheImpl.writeReserves;
import static systems.glam.services.integrations.kamino.KaminoCacheImpl.writeScopeConfiguration;

public interface KaminoCache extends ScopeAggregateIndexes, Runnable, Consumer<AccountInfo<byte[]>> {

  static CompletableFuture<KaminoCache> initService(final RpcCaller rpcCaller,
                                                    final AccountFetcher accountFetcher,
                                                    final KaminoAccounts kaminoAccounts,
                                                    final Duration pollingDelay) {
    return CompletableFuture.supplyAsync(() -> {
      final var kLendProgram = kaminoAccounts.kLendProgram();
      final var scopeProgram = kaminoAccounts.scopePricesProgram();
      final var kVaultsProgram = kaminoAccounts.kVaultsProgram();

      // Note: New Configurations will be discovered indirectly via Kamino Lending Reserves.
      final var configAccountsRequest = ProgramAccountsRequest.build()
          .filters(List.of(Configuration.SIZE_FILTER, Configuration.DISCRIMINATOR_FILTER))
          .programId(scopeProgram)
          .dataSliceLength(0, Configuration.PADDING_OFFSET)
          .createRequest();
      final var scopeConfigurationsFuture = rpcCaller.courteousCall(
          rpcClient -> rpcClient.getProgramAccounts(configAccountsRequest),
          "Scope Configuration accounts"
      );

      final var KVaultsRequest = ProgramAccountsRequest.build()
          .filters(List.of(VaultState.SIZE_FILTER, VaultState.DISCRIMINATOR_FILTER))
          .programId(kVaultsProgram)
          .dataSliceLength(0, VaultState.VAULT_FARM_OFFSET)
          .createRequest();
      final var vaultStateAccountsFuture = rpcCaller.courteousCall(
          rpcClient -> rpcClient.getProgramAccounts(KVaultsRequest),
          "rpcClient#getKaminoVaultAccounts"
      );

      final var reserveAccountsRequest = ProgramAccountsRequest.build()
          .filters(List.of(Reserve.SIZE_FILTER, Reserve.DISCRIMINATOR_FILTER))
          .programId(kLendProgram)
          .dataSliceLength(0, Reserve.CONFIG_PADDING_OFFSET)
          .createRequest();

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
          if (data.length != Configuration.PADDING_OFFSET || !Configuration.DISCRIMINATOR.equals(data, 0)) {
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
        final var reservesJsonFilePath = kaminoAccountsPath.resolve("reserves.json");
        final var scopeAccountsPath = kaminoAccountsPath.resolve("scope");
        final var configurationsPath = scopeAccountsPath.resolve("configurations");
        final var mappingsPath = scopeAccountsPath.resolve("mappings");
        final var kLendProgram = kaminoAccounts.kLendProgram();
        final var scopeProgram = kaminoAccounts.scopePricesProgram();
        final var kVaultsProgram = kaminoAccounts.kVaultsProgram();

        final var KVaultsRequest = ProgramAccountsRequest.build()
            .filters(List.of(VaultState.SIZE_FILTER, VaultState.DISCRIMINATOR_FILTER))
            .programId(kVaultsProgram)
            .dataSliceLength(0, VaultState.VAULT_FARM_OFFSET)
            .createRequest();
        final var vaultStateAccountsFuture = rpcCaller.courteousCall(
            rpcClient -> rpcClient.getProgramAccounts(KVaultsRequest),
            "rpcClient#getKaminoVaultAccounts"
        );

        final var reserveAccountsRequest = ProgramAccountsRequest.build()
            .filters(List.of(Reserve.SIZE_FILTER, Reserve.DISCRIMINATOR_FILTER))
            .programId(kLendProgram)
            .dataSliceLength(0, Reserve.CONFIG_PADDING_OFFSET)
            .createRequest();

        final ConcurrentMap<PublicKey, ReserveContext> reserveContextMap;
        final List<AccountInfo<byte[]>> reserveAccounts;
        final Set<PublicKey> priceFeedsNeeded;
        if (Files.exists(reservesJsonFilePath)) {
          reserveContextMap = new ConcurrentHashMap<>(512);
          reserveAccounts = List.of();
          priceFeedsNeeded = Set.of();
        } else {
          reserveAccounts = rpcCaller.courteousGet(
              rpcClient -> rpcClient.getProgramAccounts(reserveAccountsRequest),
              "rpcClient#getKaminoReserves"
          );
          reserveContextMap = new ConcurrentHashMap<>(Integer.highestOneBit(reserveAccounts.size()) << 1);

          final int priceFeedKeyFromOffset = Reserve.CONFIG_OFFSET + ReserveConfig.TOKEN_INFO_OFFSET + TokenInfo.SCOPE_CONFIGURATION_OFFSET;
          final int priceFeedKeyToOffset = priceFeedKeyFromOffset + PUBLIC_KEY_LENGTH;
          final byte[] nullKeyBytes = PublicKey.NONE.toByteArray();
          final byte[] nilKeyBytes = KaminoAccounts.NULL_KEY.toByteArray();

          priceFeedsNeeded = new HashSet<>();
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
            } else {
              final var priceFeedKey = PublicKey.readPubKey(data, priceFeedKeyFromOffset);
              priceFeedsNeeded.add(priceFeedKey);
            }
          }
        }

        final var feedContextMap = KaminoCache.loadFeedContexts(configurationsPath);
        // Note: New Configurations will be discovered indirectly via Kamino Lending Reserves.
        final CompletableFuture<List<AccountInfo<byte[]>>> scopeConfigurationsFuture;
        if (feedContextMap.keySet().containsAll(priceFeedsNeeded)) {
          scopeConfigurationsFuture = null;
        } else {
          final var configAccountsRequest = ProgramAccountsRequest.build()
              .filters(List.of(Configuration.SIZE_FILTER, Configuration.DISCRIMINATOR_FILTER))
              .programId(scopeProgram)
              .dataSliceLength(0, Configuration.PADDING_OFFSET)
              .createRequest();
          scopeConfigurationsFuture = rpcCaller.courteousCall(
              rpcClient -> rpcClient.getProgramAccounts(configAccountsRequest),
              "Scope Configuration accounts"
          );
        }

        final var mappingsContextMap = loadMappings(mappingsPath, feedContextMap);

        if (scopeConfigurationsFuture != null) {
          final var accounts = scopeConfigurationsFuture.join();
          for (final var accountInfo : accounts) {
            if (accountInfo != null) {
              final byte[] data = accountInfo.data();
              if (data.length != Configuration.PADDING_OFFSET || !Configuration.DISCRIMINATOR.equals(data, 0)) {
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
            final var path = FileUtils.resolveAccountPath(mappingsPath, mappingsKey);
            Files.write(path, mappingsContext.data(), CREATE, TRUNCATE_EXISTING, WRITE);
          }
        }

        if (reserveAccounts.isEmpty()) {
          loadReserves(reservesJsonFilePath, mappingsContextMap, reserveContextMap);
        } else {
          for (final var reserveAccountInfo : reserveAccounts) {
            if (!reserveContextMap.containsKey(reserveAccountInfo.pubKey())) {
              final var reserveContext = ReserveContext.createContext(reserveAccountInfo, mappingsContextMap);
              reserveContextMap.put(reserveContext.pubKey(), reserveContext);
            }
          }
          writeReserves(reservesJsonFilePath, reserveContextMap);
        }

        final var vaultStateAccounts = vaultStateAccountsFuture.join();
        final var vaultStateMap = new ConcurrentHashMap<PublicKey, KaminoVaultContext>(Integer.highestOneBit(vaultStateAccounts.size()) << 1);
        for (final var accountInfo : vaultStateAccounts) {
          final var vaultStateContext = KaminoVaultContext.createContext(accountInfo);
          vaultStateMap.put(vaultStateContext.sharesMint(), vaultStateContext);
        }

//        analyzeMarkets(reserveContextMap);

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
            reservesJsonFilePath,
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

  private static void loadReserves(final Path reservesJsonFilePath,
                                   final Map<PublicKey, MappingsContext> mappingsContextByPriceFeed,
                                   final Map<PublicKey, ReserveContext> reserveContexts) throws IOException {
    final byte[] data = Files.readAllBytes(reservesJsonFilePath);
    KaminoReserveContextsParser.parseReserves(data, mappingsContextByPriceFeed, reserveContexts);
  }

  private static Map<PublicKey, ScopeFeedContext> loadFeedContexts(final Path configurationsPath) throws IOException {
    final var feedContextMap = new HashMap<PublicKey, ScopeFeedContext>();
    if (Files.notExists(configurationsPath)) {
      Files.createDirectories(configurationsPath);
    } else {
      try (final var paths = Files.list(configurationsPath)) {
        paths.forEach(path -> {
          final var accountData = FileUtils.readAccountData(path);
          if (accountData.isAccount(Configuration.DISCRIMINATOR, Configuration.PADDING_OFFSET)) {
            final var feedContext = accountData.read(ScopeFeedContext::createContext);
            feedContextMap.put(feedContext.configurationKey(), feedContext);
            feedContextMap.put(feedContext.oracleMappings(), feedContext);
            feedContextMap.put(feedContext.priceFeed(), feedContext);
          }
        });
      }
    }
    return feedContextMap;
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
          if (accountData.isAccount(OracleMappings.DISCRIMINATOR, OracleMappings.BYTES)) {
            final var mappings = accountData.read(OracleMappings::read);
            final var scopeEntries = ScopeReader.parseEntries(0, mappings);
            final var feedContext = feedContextMap.get(mappings._address());
            final var mappingsKey = mappings._address();
            final var mappingsContext = new MappingsContext(mappingsKey, accountData.data(), scopeEntries);
            mappingsContextByPriceFeed.put(mappingsKey, mappingsContext);
            mappingsContextByPriceFeed.put(feedContext.priceFeed(), mappingsContext);
          }
        });
      }
    }
    return mappingsContextByPriceFeed;
  }

  Path reservesJsonFilePath();

  Path mappingsPath();

  Path configurationsPath();

  ReserveContext reserveContext(final PublicKey pubKey);

  ReserveContext acceptReserve(final AccountInfo<byte[]> accountInfo);

  KaminoVaultContext vaultForShareMint(final PublicKey sharesMint);

  Collection<KaminoVaultContext> vaultContexts();

  void subscribe(final SolanaRpcWebsocket websocket);

  void subscribe(final KaminoListener listener);

  void unsubscribe(final KaminoListener listener);

  void subscribeToReserve(final PublicKey reserveKey, final KaminoListener listener);

  void unSubscribeToReserve(final PublicKey reserveKey, final KaminoListener listener);

  void subscribeToVault(final PublicKey vaultMint, final KaminoListener listener);

  void unSubscribeFromVault(final PublicKey vaultMint, final KaminoListener listener);

  void refreshVaults(final Set<PublicKey> vaultMints);
}
