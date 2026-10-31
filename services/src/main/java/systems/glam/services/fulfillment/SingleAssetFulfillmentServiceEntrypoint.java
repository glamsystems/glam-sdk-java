package systems.glam.services.fulfillment;

import software.sava.core.accounts.PublicKey;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import software.sava.services.core.config.ServiceConfigUtil;
import software.sava.services.solana.epoch.EpochInfoService;
import software.sava.services.solana.transactions.InstructionService;
import software.sava.services.solana.transactions.TxMonitorService;
import software.sava.services.solana.websocket.WebSocketManager;
import systems.glam.sdk.*;
import systems.glam.sdk.idl.programs.glam.mint.gen.GlamMintConstants;
import systems.glam.services.fulfillment.config.FulfillmentServiceConfig;
import systems.glam.services.mints.MintContext;

import java.net.http.HttpClient;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static java.lang.System.Logger.Level.*;
import static java.util.concurrent.TimeUnit.SECONDS;

public record SingleAssetFulfillmentServiceEntrypoint(WebSocketManager webSocketManager,
                                                      EpochInfoService epochInfoService,
                                                      TxMonitorService txMonitorService,
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
        // run() returns only once the service is over: exit non-zero for a supervisor to restart
        // the process. Exiting here skips the closes, which wait on every task the executor holds,
        // and a courteous call retrying an unreachable endpoint never ends.
        System.exit(1);
      }
    } catch (final InterruptedException e) {
      // exit
    } catch (final Throwable e) {
      logger.log(ERROR, "Unexpected service failure.", e);
      System.exit(1);
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

    final var vaultMintContext = MintContext.createContext(solanaAccounts, accountsNeededMap.get(mintKey));

    final var webSocketConsumers = new ArrayList<Consumer<SolanaRpcWebsocket>>();
    final var webSocketManager = delegateServiceConfig.createWebSocketManager(wsHttpClient, webSocketConsumers);
    webSocketManager.checkConnection();

    final var transactionProcessor = delegateServiceConfig.createTransactionProcessor(
        taskExecutor, signingService, serviceKey, webSocketManager
    );

    final var epochInfoService = delegateServiceConfig.createEpochInfoService();

    final var txMonitorService = delegateServiceConfig.createTxMonitorService(
        epochInfoService, webSocketManager, transactionProcessor
    );

    final var splClient = glamAccountClient.splClient();
    final var instructionService = InstructionService.createService(
        rpcCaller,
        transactionProcessor,
        splClient,
        epochInfoService,
        txMonitorService
    );

    final var instructionProcessor = delegateServiceConfig.createInstructionProcessor(
        transactionProcessor, instructionService, glamAccounts
    );

    final var baseAssetMintContext = MintContext.createContext(solanaAccounts, baseAssetAccountFuture.join());

    final var serviceContext = delegateServiceConfig.createServiceContext(
        taskExecutor,
        serviceKey,
        glamAccounts,
        glamAccounts
    );

    final var executionServiceContext = delegateServiceConfig.createExecutionServiceContext(
        serviceContext, epochInfoService, instructionProcessor
    );

    final var fulfillmentService = FulfillmentService.createSingleAssetService(
        executionServiceContext,
        serviceConfig.softRedeem(),
        stateAccountClient,
        vaultMintContext,
        baseAssetMintContext
    );

    webSocketConsumers.add(fulfillmentService::subscribe);
    fulfillmentService.subscribe(webSocketManager.webSocket());

    return new SingleAssetFulfillmentServiceEntrypoint(webSocketManager, epochInfoService, txMonitorService, fulfillmentService);
  }

  // package-private so tests can drive the gate directly (same precedent as
  // the package-private locks); createService has no seam for a stub client
  static boolean validateDelegatePermissions(final Map<PublicKey, ProtocolPermissions> requiredPermissions,
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

  /// Runs the services until the fulfillment worker ends, then stops the others and returns, for
  /// `main` to end the process: nothing restarts the worker, nor a transaction monitor that has
  /// stopped. An interrupt ends it too, quietly.
  @Override
  public void run() {
    // the epoch service, the transaction monitor's two loops (signature polling and expiry),
    // and the fulfillment service; without the monitor's threads an await that misses the
    // websocket confirmation falls through to a poll nobody serves
    try (final var executorService = Executors.newFixedThreadPool(4)) {
      try {
        executorService.execute(epochInfoService);
        txMonitorService.run(executorService);
        final var fulfillment = executorService.submit(fulfillmentService);
        // Returns as soon as the worker ends, without waiting for a transaction it left pending:
        // one may still land (TxMonitorStoppedException) while the restarted process rebuilds and
        // sends fulfill again, and the on-chain fulfill instruction tolerates being executed twice.
        for (; ; ) {
          webSocketManager.checkConnection();
          try {
            // paces the connection checks, and ends the pause as soon as the worker ends
            fulfillment.get(3, SECONDS);
          } catch (final TimeoutException running) {
            continue;
          } catch (final ExecutionException failed) {
            final var cause = failed.getCause();
            logger.log(ERROR, "The fulfillment service failed, exiting for a supervisor to restart the process: " + cause, cause);
            return;
          }
          logger.log(ERROR, "The fulfillment service has ended, exiting for a supervisor to restart the process.");
          return;
        }
      } finally {
        // The epoch service's and the monitor's loops end only on an interrupt, and after an
        // interrupt of this thread so does the worker's. The pool's close interrupts nothing
        // unless its own wait is interrupted: the InterruptedException out of get has already
        // consumed this one, so without this the close would wait on them for good.
        executorService.shutdownNow();
      }
    } catch (final InterruptedException e) {
      // exit.
    } finally {
      webSocketManager.close();
    }
  }
}
