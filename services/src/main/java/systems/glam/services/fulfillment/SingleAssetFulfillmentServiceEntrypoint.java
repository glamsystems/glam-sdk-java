package systems.glam.services.fulfillment;

import software.sava.core.accounts.PublicKey;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import software.sava.services.core.config.ServiceConfigUtil;
import software.sava.services.solana.alt.LookupTableCache;
import software.sava.services.solana.epoch.EpochInfoService;
import software.sava.services.solana.transactions.InstructionService;
import software.sava.services.solana.transactions.TransactionProcessor;
import software.sava.services.solana.transactions.TxMonitorService;
import software.sava.services.solana.websocket.WebSocketManager;
import systems.glam.sdk.*;
import systems.glam.sdk.idl.programs.glam.mint.gen.GlamMintConstants;
import systems.glam.services.execution.InstructionProcessor;
import systems.glam.services.fulfillment.config.FulfillmentServiceConfig;
import systems.glam.services.tokens.MintContext;

import java.net.http.HttpClient;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static java.lang.System.Logger.Level.*;

public record SingleAssetFulfillmentServiceEntrypoint(WebSocketManager webSocketManager,
                                                      EpochInfoService epochInfoService,
                                                      FulfillmentService fulfillmentService) implements Runnable {

  private static final System.Logger logger = System.getLogger(SingleAssetFulfillmentServiceEntrypoint.class.getName());
  public static final boolean DRY_RUN;

  static {
    final var module = SingleAssetFulfillmentServiceEntrypoint.class.getModule();
    final var propertyKey = (module == null
        ? SingleAssetFulfillmentServiceEntrypoint.class.getPackageName()
        : module.getName()) + ".dry_run";
    DRY_RUN = Boolean.parseBoolean(System.getProperty(propertyKey, "false"));
  }

  static void main() {
    if (DRY_RUN) {
      logger.log(WARNING, "DRY RUN ENABLED");
    }
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

  private static SingleAssetFulfillmentServiceEntrypoint createService(final ExecutorService taskExecutor,
                                                                       final HttpClient httpClient,
                                                                       final HttpClient wsHttpClient) throws InterruptedException {
    final var configPath = ServiceConfigUtil.configFilePath(SingleAssetFulfillmentServiceEntrypoint.class);
    final var serviceConfig = FulfillmentServiceConfig.loadConfig(configPath, taskExecutor, httpClient);
    final var delegateServiceConfig = serviceConfig.delegateServiceConfig();

    final var signingService = delegateServiceConfig.signingServiceConfig().signingService();
    final var serviceKeyFuture = signingService.publicKeyWithRetries();

    final var glamAccounts = GlamAccounts.MAIN_NET_STAGING;

    final var initAccountsNeeded = HashSet.<PublicKey>newHashSet(8);

    final var stateAccountKey = delegateServiceConfig.glamStateKey();

    final var formatter = delegateServiceConfig.formatter();

    final var serviceKey = serviceKeyFuture.join();
    final var vaultAccounts = GlamVaultAccounts.createAccounts(glamAccounts, serviceKey, stateAccountKey);

    logger.log(INFO, String.format("""
                Initializing services for:
                 - State: %s
                 - Vault: %s
                 - Fee Payer: %s
                 - Max Priority Fee: %s lamports
                 - Check state at most every %s and at least every %s.
                """,
            formatter.formatAddress(stateAccountKey),
            formatter.formatAddress(vaultAccounts.vaultPublicKey()),
            formatter.formatAddress(vaultAccounts.feePayer()),
            delegateServiceConfig.maxLamportPriorityFee().toPlainString(),
            delegateServiceConfig.minCheckStateDelay().toString().substring(2),
            delegateServiceConfig.maxCheckStateDelay().toString().substring(2)
        )
    );

    final var rpcCaller = delegateServiceConfig.rpcCaller();

    initAccountsNeeded.add(stateAccountKey);
    final var mintKey = vaultAccounts.mintPDA().publicKey();
    initAccountsNeeded.add(mintKey);

    final var accountsNeededList = List.copyOf(initAccountsNeeded);
    final var accountsNeededFuture = rpcCaller.courteousCall(
        rpcClient -> rpcClient.getAccounts(accountsNeededList),
        "rpcClient::getPositionRelatedAccounts"
    );

    final var requiredPermissions = Map.of(
        glamAccounts.mintIntegrationProgram(), Protocol.MINT.permissions(GlamMintConstants.PROTO_MINT_PERM_FULFILL)
    );

    final var accountsNeededMap = HashMap.<PublicKey, AccountInfo<byte[]>>newHashMap(accountsNeededList.size());
    for (final var accountInfo : accountsNeededFuture.join()) {
      if (accountInfo != null) {
        accountsNeededMap.put(accountInfo.pubKey(), accountInfo);
      }
    }

    final var solanaAccounts = delegateServiceConfig.solanaAccounts();
    final var glamAccountClient = GlamAccountClient.createClient(solanaAccounts, vaultAccounts);
    final var glamAccountInfo = accountsNeededMap.get(stateAccountKey);
    final var stateAccountClient = glamAccountClient.createStateAccountClient(glamAccountInfo);
    if (!validateDelegatePermissions(requiredPermissions, serviceKey, stateAccountClient)) {
      return null;
    }

    final var baseAssetMint = stateAccountClient.baseAssetMint();
    final var baseAssetAccountFuture = rpcCaller.courteousCall(
        rpcClient -> rpcClient.getAccountInfo(baseAssetMint),
        "rpcClient::getBaseAssetAccount"
    );

    final var vaultMintContext = MintContext.createContext(glamAccountClient, accountsNeededMap.get(mintKey));

    final var websocketConfig = delegateServiceConfig.websocketConfig();
    final var webSocketConsumers = new ArrayList<Consumer<SolanaRpcWebsocket>>();
    final var webSocketManager = WebSocketManager.createManager(
        wsHttpClient,
        websocketConfig.endpoint(),
        websocketConfig.backoff(),
        websocket -> {
          for (final var webSocketConsumer : webSocketConsumers) {
            webSocketConsumer.accept(websocket);
          }
        }
    );
    webSocketManager.checkConnection();

    final var tableCacheConfig = delegateServiceConfig.tableCacheConfig();
    final var tableCache = LookupTableCache.createCache(
        taskExecutor,
        tableCacheConfig.initialCapacity(),
        rpcCaller.rpcClients()
    );

    final var transactionProcessor = TransactionProcessor.createProcessor(
        taskExecutor,
        signingService,
        tableCache,
        serviceKey,
        solanaAccounts,
        formatter,
        rpcCaller.rpcClients(),
        delegateServiceConfig.sendClients(),
        delegateServiceConfig.feeProviders(),
        rpcCaller.callWeights(),
        webSocketManager
    );

    logger.log(INFO, "Starting epoch info service.");
    final var epochInfoService = EpochInfoService.createService(delegateServiceConfig.epochServiceConfig(), rpcCaller);

    final var txMonitorConfig = delegateServiceConfig.txMonitorConfig();
    final var txMonitorService = TxMonitorService.createService(
        formatter,
        rpcCaller,
        epochInfoService,
        webSocketManager,
        txMonitorConfig.minSleepBetweenSigStatusPolling(),
        txMonitorConfig.webSocketConfirmationTimeout(),
        transactionProcessor,
        txMonitorConfig.retrySendDelay(),
        txMonitorConfig.minBlocksRemainingToResend()
    );

    final var splClient = glamAccountClient.splClient();
    final var instructionService = InstructionService.createService(
        rpcCaller,
        transactionProcessor,
        splClient,
        epochInfoService,
        txMonitorService
    );

    final var instructionProcessor = InstructionProcessor.createProcessor(
        transactionProcessor,
        instructionService,
        delegateServiceConfig.maxLamportPriorityFee(),
        delegateServiceConfig.notifyClient(),
        1.13,
        8
    );

    final var baseAssetMintContext = MintContext.createContext(glamAccountClient, baseAssetAccountFuture.join());

    final var fulfillmentService = FulfillmentService.createSingleAssetService(
        epochInfoService,
        serviceConfig.softRedeem(),
        stateAccountClient,
        vaultMintContext,
        baseAssetMintContext,
        rpcCaller,
        instructionProcessor,
        delegateServiceConfig.warnFeePayerBalance(), delegateServiceConfig.minFeePayerBalance(),
        delegateServiceConfig.minCheckStateDelay(), delegateServiceConfig.maxCheckStateDelay(),
        delegateServiceConfig.serviceBackoff()
    );

    webSocketConsumers.add(fulfillmentService::subscribe);
    fulfillmentService.subscribe(webSocketManager.webSocket());

    return new SingleAssetFulfillmentServiceEntrypoint(webSocketManager, epochInfoService, fulfillmentService);
  }

  private static boolean validateDelegatePermissions(final Map<PublicKey, ProtocolPermissions> requiredPermissions,
                                                     final PublicKey delegateKey,
                                                     final StateAccountClient stateAccountClient) {
    if (stateAccountClient == null) {
      logger.log(ERROR, "Glam account does not exist, exiting.");
      return false;
    } else if (!stateAccountClient.delegateHasPermissions(delegateKey, requiredPermissions)) {
      logger.log(ERROR, String.format("Service key %s does not have the required permissions.", delegateKey));
      return false;
    } else {
      return true;
    }
  }

  @Override
  public void run() {
    try (final var executorService = Executors.newFixedThreadPool(2)) {
      executorService.execute(epochInfoService);
      executorService.execute(fulfillmentService);
      for (; ; ) {
        webSocketManager.checkConnection();
        //noinspection BusyWait
        Thread.sleep(3_000);
      }
    } catch (final InterruptedException e) {
      // exit.
    } finally {
      webSocketManager.close();
    }
  }
}
