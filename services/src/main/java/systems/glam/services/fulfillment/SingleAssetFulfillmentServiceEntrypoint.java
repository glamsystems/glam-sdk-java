package systems.glam.services.fulfillment;

import software.sava.core.accounts.PublicKey;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import software.sava.services.core.config.ServiceConfigUtil;
import software.sava.services.core.net.http.NotifyClient;
import software.sava.services.solana.epoch.EpochInfoService;
import software.sava.services.solana.transactions.InstructionService;
import software.sava.services.solana.transactions.TxMonitorService;
import software.sava.services.solana.websocket.WebSocketManager;
import systems.glam.sdk.*;
import systems.glam.sdk.idl.programs.glam.mint.gen.GlamMintConstants;
import systems.glam.services.execution.Pages;
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
      // Every end of the service exits from inside this block, before any close runs: a close
      // waits on what its resource still holds, the executor on every task it runs (a courteous
      // call retrying an unreachable endpoint never ends) and the websocket client on the socket
      // createService opened, so an exit placed after the closes might never be reached. An
      // interrupt during startup returns instead, quietly, through the closes; a resource
      // initializer that throws is caught below, after the closes of what was built.
      try {
        final var service = createService(taskExecutor, httpClient, wsHttpClient);
        if (service != null) {
          service.run();
        }
      } catch (final InterruptedException e) {
        // a shutdown: quiet
        return;
      } catch (final Throwable e) {
        logger.log(ERROR, "Unexpected service failure.", e);
      }
      // run() returns only once the service is over, createService answers null only after
      // logging at ERROR and paging why it refused to start (no state account, or a delegate
      // without the fulfill permission), and any other failure is logged just above: every one
      // of them exits non-zero, for a supervisor to restart the process, until the account
      // exists, the permission is granted or the failure is fixed.
      System.exit(1);
    } catch (final Throwable e) {
      // A resource initializer threw, before anything was started (HttpClient.Builder.build wraps
      // a TLS misconfiguration as an UncheckedIOException): the closes of what was built ran at
      // once, since nothing held them, and the failure is recorded and exits like any other.
      // Nothing else arrives here: every path through the block above exits or returns.
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
    final var notifyClient = delegateServiceConfig.notifyClient();
    final var glamAccountInfo = accountsNeededMap.get(stateAccountKey);
    final StateAccountClient stateAccountClient;
    try {
      stateAccountClient = glamAccountClient.createStateAccountClient(glamAccountInfo);
    } catch (final RuntimeException notAStateAccount) {
      // an account that exists at the configured key but is not a GLAM state account (the vault
      // PDA, a mint, a token account): a misconfiguration only a human fixes, so paged like the
      // gate's refusals below rather than left to main's "Unexpected service failure." record
      refuse(String.format("The account at %s is not a Glam state account: %s", stateAccountKey, Pages.summarize(notAStateAccount)), notifyClient);
      return null;
    }
    if (!validateDelegatePermissions(requiredPermissions, serviceKey, stateAccountClient, notifyClient)) {
      return null;
    }
    final var mintAccountInfo = accountsNeededMap.get(mintKey);
    if (mintAccountInfo == null) {
      refuse(String.format("Glam state %s has no share mint at %s, exiting.", stateAccountKey, mintKey), notifyClient);
      return null;
    }

    final var baseAssetMint = stateAccountClient.baseAssetMint();
    final var baseAssetAccountFuture = rpcCaller.courteousCall(
        rpcClient -> rpcClient.getAccountInfo(baseAssetMint),
        "rpcClient::getBaseAssetAccount"
    );

    final var vaultMintContext = MintContext.createContext(solanaAccounts, mintAccountInfo);

    // The manager creates no websocket before its first checkConnection() or webSocket() call,
    // which is made below, once the fulfillment service is registered here: every websocket the
    // manager creates, the first included, is then handed to the service before it connects.
    final var webSocketConsumers = new ArrayList<Consumer<SolanaRpcWebsocket>>();
    final var webSocketManager = delegateServiceConfig.createWebSocketManager(wsHttpClient, webSocketConsumers);

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
    // The first connection attempt, which nothing here waits on. The service's two subscriptions
    // wait for it and are sent when it opens, as one message at the default batch cap. A websocket
    // that cannot be created, or a connection that fails, is the manager's to log and retry, from
    // its own wake and from run()'s checks, and the service runs on its RPC fetches meanwhile: an
    // account update only ends the wait between two of them early, and a transaction whose
    // websocket confirmation is missed is settled by the monitor's polling.
    webSocketManager.checkConnection();

    return new SingleAssetFulfillmentServiceEntrypoint(webSocketManager, epochInfoService, txMonitorService, fulfillmentService);
  }

  // package-private so tests can drive the gate directly (same precedent as the package-private
  // locks), with the notify client its refusals page through; createService has no seam for a
  // stub client and hands in the configuration's
  static boolean validateDelegatePermissions(final Map<PublicKey, ProtocolPermissions> requiredPermissions,
                                             final PublicKey delegateKey,
                                             final StateAccountClient stateAccountClient,
                                             final NotifyClient notifyClient) {
    if (stateAccountClient == null) {
      refuse("Glam account does not exist, exiting.", notifyClient);
      return false;
    } else if (!stateAccountClient.delegateHasPermissions(delegateKey, requiredPermissions)) {
      refuse(String.format("Service key %s does not have the required permissions.", delegateKey), notifyClient);
      return false;
    } else {
      return true;
    }
  }

  /// Logs `refusal` at ERROR, posts it as a page through `notifyClient` and awaits the page for
  /// [Pages#PAGE_DELIVERY_SECONDS], under this module's one paging policy ([Pages]): main exits
  /// the process as soon as createService returns null, and a supervisor that restarts it on
  /// failure reruns this bootstrap, at its own pace, for as long as the misconfiguration lasts, so
  /// the one end only a human can fix is the one that must not go unpaged.
  private static void refuse(final String refusal, final NotifyClient notifyClient) {
    logger.log(ERROR, refusal);
    Pages.awaitDelivery(refusal, Pages.post(notifyClient, refusal, logger, refusal), Pages.PAGE_DELIVERY_SECONDS, SECONDS, logger);
  }

  /// Runs the services until the fulfillment worker ends, then interrupts the others and returns,
  /// without waiting for them to end, for `main` to end the process: nothing restarts the worker,
  /// nor a transaction monitor that has stopped. An interrupt ends it too, quietly.
  @Override
  public void run() {
    // the epoch service, the transaction monitor's two loops (signature polling and expiry),
    // and the fulfillment service; without the monitor's threads an await that misses the
    // websocket confirmation falls through to a poll nobody serves
    final var executorService = Executors.newFixedThreadPool(4);
    try {
      executorService.execute(epochInfoService);
      txMonitorService.run(executorService);
      final var fulfillment = executorService.submit(fulfillmentService);
      // Returns as soon as the worker ends, without waiting for a transaction it left pending:
      // one may still land (a publication verdict whose mayStillLand() is true, a stopped
      // monitor's among them) while the restarted process rebuilds and sends fulfill again, and
      // the on-chain fulfill instruction tolerates being executed twice.
      for (; ; ) {
        webSocketManager.checkConnection();
        try {
          // paces the connection checks, and ends the pause as soon as the worker ends
          fulfillment.get(3, SECONDS);
        } catch (final TimeoutException running) {
          continue;
        } catch (final ExecutionException failed) {
          // Rare for the shipped worker: BaseFulfillmentService.run, which is final, catches
          // whatever ends its loop, logs it, pages it unless it was a shutdown, and returns, so only what escapes that
          // handler's own body arrives here, an Error thrown inside it (an OutOfMemoryError in the
          // concatenation of its log record, say). Kept for any other FulfillmentService, whose
          // run() may throw.
          final var cause = failed.getCause();
          logger.log(ERROR, "The fulfillment service failed, exiting for a supervisor to restart the process: " + cause, cause);
          return;
        }
        logger.log(ERROR, "The fulfillment service has ended, exiting for a supervisor to restart the process; the worker's own record names what ended it.");
        return;
      }
    } catch (final InterruptedException e) {
      // a shutdown: quiet
    } finally {
      // Interrupts the epoch service's and the monitor's loops, which end only on an interrupt,
      // and the worker's after an interrupt of this thread, then returns without awaiting any of
      // them: main exits the process as soon as this returns, so nothing a loop still does
      // matters, and a wait here would only hold the exit for it.
      executorService.shutdownNow();
      webSocketManager.close();
    }
  }
}
