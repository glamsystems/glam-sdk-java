package systems.glam.services.pricing;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.drift.DriftAccounts;
import software.sava.idl.clients.kamino.KaminoAccounts;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import software.sava.services.core.config.ServiceConfigUtil;
import systems.glam.sdk.GlamAccounts;
import systems.glam.services.state.GlobalConfigCache;
import systems.glam.services.fulfillment.SingleAssetFulfillmentServiceEntrypoint;
import systems.glam.services.integrations.IntegLookupTableCache;
import systems.glam.services.integrations.IntegrationServiceContext;
import systems.glam.services.integrations.drift.DriftMarketCache;
import systems.glam.services.pricing.config.PriceVaultsServiceConfig;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static java.lang.System.Logger.Level.ERROR;

final class PriceVaultsServiceEntrypoint {

  private static final System.Logger logger = System.getLogger(SingleAssetFulfillmentServiceEntrypoint.class.getName());

  static void main() {

    try (final var taskExecutor = Executors.newVirtualThreadPerTaskExecutor();
         final var httpClient = HttpClient.newBuilder().executor(taskExecutor).build();
         final var wsHttpClient = HttpClient.newHttpClient()) {
      createService(taskExecutor, httpClient, wsHttpClient);
    } catch (final InterruptedException e) {
      // exit
    } catch (final Throwable e) {
      logger.log(ERROR, "Unexpected service failure.", e);
    }
  }

  private static void createService(final ExecutorService taskExecutor,
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

    final var driftCacheDirectory = serviceContext.accountsCacheDirectory().resolve("drift/");
    final var driftMarketCacheFuture = DriftMarketCache.initCache(
        driftCacheDirectory,
        driftAccounts, rpcCaller,
        accountFetcher
    );

    final var integrationTableKeys = new HashSet<>(driftAccounts.marketLookupTables());
    integrationTableKeys.add(kaminoAccounts.mainMarketLUT());

    final var integrationTablesDirectory = serviceContext.accountsCacheDirectory().resolve("integ/lookup_tables");
    final var integTableCacheFuture = IntegLookupTableCache.initCache(
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

    final var integrationServiceContext = IntegrationServiceContext.createContext(
        serviceContext,
        mintCache,
        globalConfigCache,
        integTableCache,
        accountFetcher,
        driftAccounts,
        driftMarketCache,
        kaminoAccounts,
        null // TODO: kVaults
    );

    final var priceVaultsCache = GlamStateContextCache.loadCache(
        defensivePollingConfig.glamStateAccounts(),
        integrationServiceContext,
        serviceContext.accountsCacheDirectory().resolve("glam/vault_tables")
    );
    webSocketConsumers.add(priceVaultsCache::subscribe);

    final var webSocketManager = delegateServiceConfig.createWebSocketManager(wsHttpClient, List.copyOf(webSocketConsumers));
    webSocketManager.checkConnection();
  }
}
