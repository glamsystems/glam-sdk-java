package systems.glam.services.pricing;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.drift.DriftAccounts;
import software.sava.idl.clients.kamino.KaminoAccounts;
import software.sava.services.core.config.ServiceConfigUtil;
import systems.glam.sdk.GlamAccounts;
import systems.glam.services.GlobalConfigCache;
import systems.glam.services.fulfillment.SingleAssetFulfillmentServiceEntrypoint;
import systems.glam.services.integrations.IntegrationServiceContext;
import systems.glam.services.io.FileUtils;
import systems.glam.services.pricing.config.PriceVaultsServiceConfig;

import java.net.http.HttpClient;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    final var rpcCaller = delegateServiceConfig.rpcCaller();

    final var serviceKeyFuture = delegateServiceConfig.signingServiceConfig().signingService().publicKeyWithRetries();

    final var alwaysFetch = new HashSet<PublicKey>();

    final var glamAccounts = GlamAccounts.MAIN_NET_STAGING;
    final var driftAccounts = DriftAccounts.MAIN_NET;
    final var kaminoAccounts = KaminoAccounts.MAIN_NET;
    final var accountFetcher = delegateServiceConfig.createAccountFetcher(alwaysFetch);

    final var serviceKey = serviceKeyFuture.join();
    final var serviceContext = delegateServiceConfig.createServiceContext(
        taskExecutor,
        serviceKey,
        glamAccounts
    );

    final var defensivePollingConfig = delegateServiceConfig.defensivePollingConfig();

    final var globalConfigKey = glamAccounts.globalConfigPDA().publicKey();
    final var globalConfigCache = GlobalConfigCache.initCache(
        FileUtils.resolveAccountPath(serviceContext.accountsCacheDirectory().resolve("glam/global/"), globalConfigKey),
        glamAccounts.configProgram(),
        globalConfigKey,
        rpcCaller, accountFetcher,
        defensivePollingConfig.globalConfig()
    );

    final var mintCache = delegateServiceConfig.createMintCache();

    final var integrationServiceContext = IntegrationServiceContext.createContext(
        serviceContext,
        null,
        mintCache,
        null,
        accountFetcher,
        driftAccounts,
        null,
        kaminoAccounts,
        null // TODO
    );

//    final var driftCacheDirectory =
//    final var driftMarketCache = DriftMarketCache.initCache(glamAccounts, delegateServiceConfig.rpcCaller());

  }
}
