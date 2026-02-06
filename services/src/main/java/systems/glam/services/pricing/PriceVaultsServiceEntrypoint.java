package systems.glam.services.pricing;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.drift.DriftAccounts;
import software.sava.idl.clients.kamino.KaminoAccounts;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import software.sava.services.core.config.ServiceConfigUtil;
import software.sava.services.solana.websocket.WebSocketManager;
import software.sava.solana.programs.stakepool.StakePoolAccounts;
import systems.glam.sdk.GlamAccounts;
import systems.glam.services.fulfillment.SingleAssetFulfillmentServiceEntrypoint;
import systems.glam.services.integrations.IntegLookupTableCache;
import systems.glam.services.integrations.IntegrationServiceContext;
import systems.glam.services.integrations.drift.DriftMarketCache;
import systems.glam.services.mints.StakePoolCache;
import systems.glam.services.oracles.scope.KaminoCache;
import systems.glam.services.pricing.config.PriceVaultsServiceConfig;
import systems.glam.services.rpc.AccountFetcher;
import systems.glam.services.state.GlobalConfigCache;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static java.lang.System.Logger.Level.ERROR;

public record PriceVaultsServiceEntrypoint(AccountFetcher accountFetcher,
                                           GlobalConfigCache globalConfigCache,
                                           KaminoCache kaminoCache,
                                           IntegLookupTableCache integTableCache,
                                           GlamStateContextCache glamStateContextCache,
                                           StakePoolCache stakePoolCache,
                                           GlamVaultExecutor vaultExecutor,
                                           WebSocketManager webSocketManager) implements Runnable {

  private static final System.Logger logger = System.getLogger(SingleAssetFulfillmentServiceEntrypoint.class.getName());

  static void main() {
    try (final var taskExecutor = Executors.newVirtualThreadPerTaskExecutor();
         final var httpClient = HttpClient.newBuilder().executor(taskExecutor).build();
         final var wsHttpClient = HttpClient.newHttpClient()) {
      final var service = createService(taskExecutor, httpClient, wsHttpClient);
      if (service != null) {
        service.run();
      }
    } catch (final InterruptedException e) {
      // exit
    } catch (final Throwable e) {
      logger.log(ERROR, "Unexpected service failure.", e);
    }
  }

  private static PriceVaultsServiceEntrypoint createService(final ExecutorService taskExecutor,
                                                            final HttpClient httpClient,
                                                            final HttpClient wsHttpClient) throws InterruptedException {
    final var configPath = ServiceConfigUtil.configFilePath(PriceVaultsServiceEntrypoint.class);
    final var serviceConfig = PriceVaultsServiceConfig.loadConfig(configPath, taskExecutor, httpClient);
    final var delegateServiceConfig = serviceConfig.delegateServiceConfig();

    final var serviceKeyFuture = delegateServiceConfig.signingServiceConfig().signingService().publicKeyWithRetries();

    final var alwaysFetch = new HashSet<PublicKey>();

    final var solanaAccounts = delegateServiceConfig.solanaAccounts();
    final var glamAccounts = GlamAccounts.MAIN_NET;
    final var driftAccounts = DriftAccounts.MAIN_NET;
    final var kaminoAccounts = KaminoAccounts.MAIN_NET;
    final var accountFetcher = delegateServiceConfig.createAccountFetcher(alwaysFetch);

    final var serviceKey = serviceKeyFuture.join();
    final var serviceContext = delegateServiceConfig.createServiceContext(
        taskExecutor,
        serviceKey,
        glamAccounts
    );

    final var mintCache = delegateServiceConfig.createMintCache();

    final var defensivePollingConfig = delegateServiceConfig.defensivePollingConfig();
    final var rpcCaller = delegateServiceConfig.rpcCaller();

    final var accountCacheDirectory = serviceContext.accountsCacheDirectory();
    final var stakePoolCacheFuture = StakePoolCache.initCache(
        taskExecutor,
        accountCacheDirectory.resolve("stake_pools"),
        StakePoolAccounts.MAIN_NET,
        defensivePollingConfig.stakePools(),
        rpcCaller
    );

    final var globalConfigKey = glamAccounts.globalConfigPDA().publicKey();
    final var globalConfigCacheFuture = GlobalConfigCache.initCache(
        serviceContext.globalConfigCacheFile(),
        glamAccounts.configProgram(),
        globalConfigKey,
        solanaAccounts,
        mintCache,
        rpcCaller,
        accountFetcher,
        defensivePollingConfig.globalConfig()
    );

    final var kaminoCacheDirectory = accountCacheDirectory.resolve("kamino/");
    final var kaminoCacheFuture = KaminoCache.initService(
        kaminoCacheDirectory,
        rpcCaller,
        accountFetcher,
        delegateServiceConfig.notifyClient(),
        kaminoAccounts,
        defensivePollingConfig.glamStateAccounts()
    );

    final var driftCacheDirectory = accountCacheDirectory.resolve("drift/");
    final var driftMarketCacheFuture = DriftMarketCache.initCache(
        driftCacheDirectory,
        driftAccounts, rpcCaller,
        accountFetcher
    );

    final var integrationTableKeys = new HashSet<>(driftAccounts.marketLookupTables());
    integrationTableKeys.add(kaminoAccounts.mainMarketLUT());

    final var integrationTablesDirectory = accountCacheDirectory.resolve("integ/lookup_tables");
    final var integTableCacheFuture = IntegLookupTableCache.initCache(
        defensivePollingConfig.integTables(),
        integrationTablesDirectory,
        integrationTableKeys,
        rpcCaller,
        accountFetcher
    );

    final var webSocketConsumers = new ArrayList<Consumer<SolanaRpcWebsocket>>();

    final var globalConfigCache = globalConfigCacheFuture.join();
    webSocketConsumers.add(globalConfigCache::subscribe);

    final var driftMarketCache = driftMarketCacheFuture.join();

    final var integTableCache = integTableCacheFuture.join();

    final var stakePoolCache = stakePoolCacheFuture.join();
    webSocketConsumers.add(stakePoolCache::subscribe);

    final var kaminoCache = kaminoCacheFuture.join();
    webSocketConsumers.add(kaminoCache::subscribe);

    final var integrationServiceContext = IntegrationServiceContext.createContext(
        serviceContext,
        mintCache,
        stakePoolCache,
        globalConfigCache,
        kaminoCache,
        integTableCache,
        accountFetcher,
        driftAccounts,
        driftMarketCache,
        kaminoAccounts,
        null // TODO: kVaults
    );

    final var glamStateContextCache = GlamStateContextCache.loadCache(
        defensivePollingConfig.glamStateAccounts(),
        integrationServiceContext,
        accountCacheDirectory.resolve("glam/vault_tables")
    );
    webSocketConsumers.add(glamStateContextCache::subscribe);

    final var webSocketManager = delegateServiceConfig.createWebSocketManager(wsHttpClient, List.copyOf(webSocketConsumers));
    webSocketManager.checkConnection();

    final var vaultExecutor = GlamVaultExecutor.createExecutor(rpcCaller, glamStateContextCache);

    return new PriceVaultsServiceEntrypoint(
        accountFetcher,
        globalConfigCache,
        kaminoCache,
        integTableCache, glamStateContextCache, stakePoolCache,
        vaultExecutor,
        webSocketManager
    );
  }

  @Override
  public void run() {
    try (final var executorService = Executors.newFixedThreadPool(7)) {
      executorService.execute(accountFetcher);
      executorService.execute(globalConfigCache);
      executorService.execute(kaminoCache);
      executorService.execute(integTableCache);
      executorService.execute(glamStateContextCache);
      executorService.execute(stakePoolCache);
      executorService.execute(vaultExecutor);
      for (; ; ) {
        webSocketManager.checkConnection();
        //noinspection BusyWait
        Thread.sleep(3_000);
        // TODO: Check if all services are healthy, if not exit.
      }
    } catch (final InterruptedException e) {
      // exit.
    } finally {
      webSocketManager.close();
    }
  }
}
