package systems.glam.services.fulfillment;

import software.sava.core.accounts.PublicKey;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import software.sava.services.solana.alt.LookupTableCache;
import software.sava.services.solana.epoch.EpochInfoService;
import software.sava.services.solana.transactions.InstructionService;
import software.sava.services.solana.transactions.TransactionProcessor;
import software.sava.services.solana.transactions.TxMonitorService;
import software.sava.services.solana.websocket.WebSocketManager;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.sdk.GlamVaultAccounts;
import systems.glam.sdk.idl.programs.glam.mint.gen.GlamMintConstants;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.IntegrationAcl;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.ProtocolPermissions;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateAccount;
import systems.glam.services.execution.InstructionProcessor;
import systems.glam.services.tokens.MintContext;

import java.net.http.HttpClient;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.System.Logger.Level.*;

public record SingleAssetFulfillmentServiceEntrypoint(ExecutorService executorService,
                                                      WebSocketManager webSocketManager,
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
         final var serviceExecutor = Executors.newFixedThreadPool(1);
         final var httpClient = HttpClient.newBuilder().executor(taskExecutor).build();
         final var wsHttpClient = HttpClient.newHttpClient()) {
      final var service = createService(
          taskExecutor, serviceExecutor,
          httpClient, wsHttpClient
      );
      if (service != null) {
        service.run();
      }
    } catch (final InterruptedException e) {
      // exit
    }
  }


  private static SingleAssetFulfillmentServiceEntrypoint createService(final ExecutorService taskExecutor,
                                                                       final ExecutorService serviceExecutor,
                                                                       final HttpClient httpClient,
                                                                       final HttpClient wsHttpClient) throws InterruptedException {
    final var serviceConfig = DelegateServiceConfig.loadConfig(SingleAssetFulfillmentServiceEntrypoint.class, taskExecutor, httpClient);

    final var signingService = serviceConfig.signingServiceConfig().signingService();
    final var serviceKeyFuture = signingService.publicKeyWithRetries();

    final var initAccountsNeeded = HashSet.<PublicKey>newHashSet(8);

    final var stateAccountKey = serviceConfig.glamStateKey();

    final var formatter = serviceConfig.formatter();

    final var serviceKey = serviceKeyFuture.join();
    final var vaultAccounts = GlamVaultAccounts.createAccounts(serviceKey, stateAccountKey);

    logger.log(INFO, String.format("""
                Initializing services for:
                 - State: %s
                 - Vault: %s
                 - Fee Payer: %s
                 - Max Priority Fee: %s SOL
                 - Check state at most every %s and at least every %s seconds.
                """,
            formatter.formatAddress(stateAccountKey),
            formatter.formatAddress(vaultAccounts.vaultPublicKey()),
            formatter.formatAddress(vaultAccounts.feePayer()),
            serviceConfig.maxLamportPriorityFee().toPlainString(),
            serviceConfig.minCheckStateDelay().toString().substring(2),
            serviceConfig.maxCheckStateDelay().toString().substring(2)
        )
    );

    final var rpcCaller = serviceConfig.rpcCaller();

    logger.log(INFO, "Starting epoch info service.");
    final var epochInfoService = EpochInfoService.createService(
        serviceConfig.epochServiceConfig(),
        rpcCaller
    );
    serviceExecutor.execute(epochInfoService);


    initAccountsNeeded.add(stateAccountKey);
    final var mintKey = vaultAccounts.mintPDA().publicKey();
    initAccountsNeeded.add(mintKey);

    final var accountsNeededList = List.copyOf(initAccountsNeeded);
    final var accountsNeededFuture = rpcCaller.courteousCall(
        rpcClient -> rpcClient.getAccounts(accountsNeededList),
        "rpcClient::getPositionRelatedAccounts"
    );

    final var glamAccounts = vaultAccounts.glamAccounts();
    final var requiredIntegrations = Map.of(glamAccounts.mintIntegrationProgram(), GlamMintConstants.PROTO_MINT);
    final var requiredPermissions = Map.of(
        glamAccounts.mintIntegrationProgram(), new ProtocolPermissions(GlamMintConstants.PROTO_MINT, GlamMintConstants.PROTO_MINT_PERM_FULFILL)
    );

    final var accountsNeededMap = HashMap.<PublicKey, AccountInfo<byte[]>>newHashMap(accountsNeededList.size());
    for (final var accountInfo : accountsNeededFuture.join()) {
      if (accountInfo != null) {
        accountsNeededMap.put(accountInfo.pubKey(), accountInfo);
      }
    }

    final var glamAccountInfo = accountsNeededMap.get(stateAccountKey);
    final var glamStateAccount = StateAccount.read(glamAccountInfo);
    if (!validateDelegatePermissions(requiredIntegrations, requiredPermissions, serviceKey, glamStateAccount)) {
      return null;
    }

    final var baseAssetMint = glamStateAccount.baseAssetMint();
    final var baseAssetAccountFuture = rpcCaller.courteousCall(
        rpcClient -> rpcClient.getAccountInfo(baseAssetMint),
        "rpcClient::getBaseAssetAccount"
    );

    final var solanaAccounts = serviceConfig.solanaAccounts();
    final var glamAccountClient = GlamAccountClient.createClient(solanaAccounts, vaultAccounts);
    final var vaultMintContext = MintContext.createContext(glamAccountClient, accountsNeededMap.get(mintKey));

    final var websocketConfig = serviceConfig.websocketConfig();
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

    final var tableCacheConfig = serviceConfig.tableCacheConfig();
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
        serviceConfig.sendClients(),
        serviceConfig.feeProviders(),
        rpcCaller.callWeights(),
        webSocketManager
    );

    final var txMonitorConfig = serviceConfig.txMonitorConfig();
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
        serviceConfig.maxLamportPriorityFee(),
        serviceConfig.notifyClient(),
        1.13,
        8
    );

    final var baseAssetMintContext = MintContext.createContext(glamAccountClient, baseAssetAccountFuture.join());

    final var fulfillmentService = FulfillmentService.createSingleAssetService(
        glamStateAccount,
        vaultMintContext,
        baseAssetMintContext,
        rpcCaller,
        instructionProcessor,
        glamAccountClient,
        serviceConfig.warnFeePayerBalance(), serviceConfig.minFeePayerBalance(),
        serviceConfig.minCheckStateDelay(), serviceConfig.maxCheckStateDelay(),
        serviceConfig.serviceBackoff()
    );

    webSocketConsumers.add(fulfillmentService::subscribe);
    fulfillmentService.subscribe(webSocketManager.webSocket());

    return new SingleAssetFulfillmentServiceEntrypoint(serviceExecutor, webSocketManager, fulfillmentService);
  }

  private static boolean validateDelegatePermissions(final Map<PublicKey, Integer> requiredIntegrations,
                                                     final Map<PublicKey, ProtocolPermissions> requiredPermissions,
                                                     final PublicKey delegateKey,
                                                     final StateAccount glamStateAccount) {
    if (glamStateAccount == null) {
      logger.log(ERROR, "Glam account does not exist, exiting.");
      return false;
    }

    final var integrationMap = Arrays.stream(glamStateAccount.integrationAcls())
        .collect(Collectors.toUnmodifiableMap(IntegrationAcl::integrationProgram, Function.identity()));

    for (final var entry : requiredIntegrations.entrySet()) {
      final int integrationMask = entry.getValue();
      if (integrationMap.get(entry.getKey()) instanceof IntegrationAcl acl) {
        if ((acl.protocolsBitmask() & integrationMask) != integrationMask) {
          logger.log(ERROR, String.format("Missing integrations for program %s.", entry.getKey()));
          return false;
        }
      } else {
        logger.log(ERROR, "Missing the following extension program: " + entry.getKey());
        return false;
      }
    }

    final var delegateAcls = Arrays.stream(glamStateAccount.delegateAcls())
        .filter(acl -> acl.pubkey().equals(delegateKey))
        .findFirst().orElse(null);
    if (delegateAcls == null) {
      logger.log(ERROR, String.format("Service key %s does not have any permissions.", delegateKey));
      return false;
    }

    final var integrationPermissions = delegateAcls.integrationPermissions();
    final var integrationPermissionMap = HashMap.<PublicKey, Map<Integer, Long>>newHashMap(integrationPermissions.length);
    for (final var permission : integrationPermissions) {
      final var protocolPermissions = Arrays.stream(permission.protocolPermissions())
          .collect(Collectors.toUnmodifiableMap(ProtocolPermissions::protocolBitflag, ProtocolPermissions::permissionsBitmask));
      integrationPermissionMap.put(permission.integrationProgram(), protocolPermissions);
    }
    for (final var entry : requiredPermissions.entrySet()) {
      final var delegateIntegrationPermissions = integrationPermissionMap.get(entry.getKey());
      final var requiredPermissionsBitmask = entry.getValue().permissionsBitmask();
      if (delegateIntegrationPermissions == null
          || (delegateIntegrationPermissions.get(entry.getValue().protocolBitflag()) & requiredPermissionsBitmask) != requiredPermissionsBitmask) {
        logger.log(ERROR, String.format("Service key %s does not have the required permissions for integration program %s.", delegateKey, entry.getKey()));
        return false;
      }
    }

    return true;
  }

  @Override
  public void run() {
    try {
      executorService.execute(fulfillmentService);
      for (; ; ) {
        webSocketManager.checkConnection();
        Thread.sleep(3_000);
      }
    } catch (final InterruptedException e) {
      // exit.
    } finally {
      webSocketManager.close();
    }
  }
}
