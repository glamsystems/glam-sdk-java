package systems.glam.services.config;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.tx.Transaction;
import software.sava.core.util.LamportDecimal;
import software.sava.kms.core.signing.SigningService;
import software.sava.kms.core.signing.SigningServiceConfig;
import software.sava.rpc.json.PublicKeyEncoding;
import software.sava.rpc.json.http.client.SolanaRpcClient;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import software.sava.services.core.config.RemoteResourceConfig;
import software.sava.services.core.config.ServiceConfigUtil;
import software.sava.services.core.net.http.NotifyClient;
import software.sava.services.core.net.http.WebHookConfig;
import software.sava.services.core.remote.call.Backoff;
import software.sava.services.core.remote.call.BackoffConfig;
import software.sava.services.core.remote.load_balance.BalancedItem;
import software.sava.services.core.remote.load_balance.LoadBalancer;
import software.sava.services.core.remote.load_balance.LoadBalancerConfig;
import software.sava.services.core.request_capacity.CapacityConfig;
import software.sava.services.core.request_capacity.context.CallContext;
import software.sava.services.solana.alt.LookupTableCache;
import software.sava.services.solana.alt.TableCacheConfig;
import software.sava.services.solana.config.ChainItemFormatter;
import software.sava.services.solana.config.HeliusConfig;
import software.sava.services.solana.epoch.EpochInfoService;
import software.sava.services.solana.epoch.EpochServiceConfig;
import software.sava.services.solana.remote.call.CallWeights;
import software.sava.services.solana.remote.call.RpcCaller;
import software.sava.services.solana.transactions.*;
import software.sava.services.solana.websocket.WebSocketManager;
import systems.comodal.jsoniter.FieldBufferPredicate;
import systems.comodal.jsoniter.JsonIterator;
import systems.glam.sdk.GlamAccounts;
import systems.glam.services.ServiceContext;
import systems.glam.services.ServiceContextImpl;
import systems.glam.services.db.DatasourceConfig;
import systems.glam.services.db.sql.SqlDataSource;
import systems.glam.services.execution.ExecutionServiceContext;
import systems.glam.services.execution.InstructionProcessor;
import systems.glam.services.mints.MintCache;
import systems.glam.services.rpc.AccountFetcher;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNullElse;
import static software.sava.services.solana.config.ChainItemFormatter.parseFormatter;
import static software.sava.services.solana.load_balance.LoadBalanceUtil.createRPCLoadBalancer;
import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

public record BaseDelegateServiceConfig(PublicKey glamStateKey,
                                        SigningServiceConfig signingServiceConfig,
                                        SolanaAccounts solanaAccounts,
                                        ChainItemFormatter formatter,
                                        NotifyClient notifyClient,
                                        Path cacheDirectory,
                                        TableCacheConfig tableCacheConfig,
                                        RpcCaller rpcCaller,
                                        LoadBalancer<SolanaRpcClient> sendClients,
                                        LoadBalancer<HeliusFeeProvider> feeProviders,
                                        RemoteResourceConfig websocketConfig,
                                        EpochServiceConfig epochServiceConfig,
                                        TxMonitorConfig txMonitorConfig,
                                        AccountFetcherConfig accountFetcherConfig,
                                        DefensivePollingConfig defensivePollingConfig,
                                        BigDecimal maxLamportPriorityFee,
                                        BigInteger warnFeePayerBalance, BigInteger minFeePayerBalance,
                                        Duration minCheckStateDelay, Duration maxCheckStateDelay,
                                        Backoff serviceBackoff,
                                        double defaultCuBudgetMultiplier,
                                        int maxTransactionRetries,
                                        DatasourceConfig datasourceConfig) implements DelegateServiceConfig {

  private static final Backoff DEFAULT_NETWORK_BACKOFF = Backoff.fibonacci(1, 21);

  @Override
  public WebSocketManager createWebSocketManager(final HttpClient wsHttpClient,
                                                 final Collection<Consumer<SolanaRpcWebsocket>> webSocketConsumers) {
    return WebSocketManager.createManager(
        wsHttpClient,
        websocketConfig.endpoint(),
        websocketConfig.backoff(),
        websocket -> {
          for (final var webSocketConsumer : webSocketConsumers) {
            webSocketConsumer.accept(websocket);
          }
        }
    );
  }

  @Override
  public LookupTableCache createLookupTableCache(final ExecutorService taskExecutor) {
    return LookupTableCache.createCache(
        taskExecutor,
        tableCacheConfig.initialCapacity(),
        rpcCaller.rpcClients()
    );
  }

  @Override
  public TransactionProcessor createTransactionProcessor(final ExecutorService taskExecutor,
                                                         final SigningService signingService,
                                                         final LookupTableCache tableCache,
                                                         final PublicKey serviceKey,
                                                         final WebSocketManager webSocketManager) {
    return TransactionProcessor.createProcessor(
        taskExecutor,
        signingService,
        tableCache,
        serviceKey,
        solanaAccounts,
        formatter,
        rpcCaller.rpcClients(),
        sendClients,
        feeProviders,
        rpcCaller.callWeights(),
        webSocketManager
    );
  }

  @Override
  public TxMonitorService createTxMonitorService(final EpochInfoService epochInfoService,
                                                 final WebSocketManager webSocketManager,
                                                 final TransactionProcessor transactionProcessor) {
    return TxMonitorService.createService(
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
  }

  @Override
  public EpochInfoService createEpochInfoService() {
    return EpochInfoService.createService(epochServiceConfig, rpcCaller);
  }

  @Override
  public InstructionProcessor createInstructionProcessor(final TransactionProcessor transactionProcessor,
                                                         final InstructionService instructionService) {
    return InstructionProcessor.createProcessor(
        transactionProcessor,
        instructionService,
        maxLamportPriorityFee,
        notifyClient,
        defaultCuBudgetMultiplier,
        maxTransactionRetries
    );
  }

  @Override
  public ServiceContext createServiceContext(final ExecutorService taskExecutor,
                                             final PublicKey serviceKey,
                                             final GlamAccounts glamAccounts) {
    final var primaryDatasource = datasourceConfig == null
        ? null
        : SqlDataSource.createDataSource("price_vault_service", datasourceConfig);

    return new ServiceContextImpl(
        serviceKey,
        warnFeePayerBalance, minFeePayerBalance,
        cacheDirectory,
        minCheckStateDelay, maxCheckStateDelay,
        taskExecutor,
        serviceBackoff,
        solanaAccounts, glamAccounts,
        notifyClient,
        rpcCaller,
        primaryDatasource
    );
  }

  @Override
  public MintCache createMintCache() {
    return MintCache.createCache(solanaAccounts, cacheDirectory.resolve("mints.bin"));
  }

  @Override
  public ExecutionServiceContext createExecutionServiceContext(final ServiceContext serviceContext,
                                                               final EpochInfoService epochInfoService,
                                                               final InstructionProcessor instructionProcessor) {
    return ExecutionServiceContext.createContext(
        serviceContext,
        epochInfoService,
        instructionProcessor,
        instructions -> Transaction.createTx(serviceContext.serviceKey(), instructions)
    );
  }

  @Override
  public AccountFetcher createAccountFetcher(final Set<PublicKey> alwaysFetch) {
    return AccountFetcher.createFetcher(
        accountFetcherConfig.fetchDelay(),
        accountFetcherConfig.reactive(),
        rpcCaller,
        alwaysFetch
    );
  }

  public static class ConfigParser implements FieldBufferPredicate {

    private final ExecutorService taskExecutor;
    private final HttpClient httpClient;

    private PublicKey glamStateKey;
    private SigningServiceConfig signingServiceConfig;
    private ChainItemFormatter formatter;
    private NotifyClient notifyClient;
    private Path cacheDirectory;
    private TableCacheConfig tableCacheConfig;
    private CallWeights callWeights;
    private Backoff defaultRPCBackoff = DEFAULT_NETWORK_BACKOFF;
    private LoadBalancer<SolanaRpcClient> rpcClients;
    private LoadBalancer<SolanaRpcClient> sendClients;
    private LoadBalancer<HeliusFeeProvider> feeProviders;
    private RemoteResourceConfig websocketConfig;
    private EpochServiceConfig epochServiceConfig;
    private TxMonitorConfig txMonitorConfig;
    private AccountFetcherConfig accountFetcherConfig;
    private DefensivePollingConfig defensivePollingConfig;
    private BigDecimal maxSOLPriorityFee;
    private BigDecimal warnFeePayerBalance;
    private BigDecimal minFeePayerBalance;
    private Duration minCheckStateDelay;
    private Duration maxCheckStateDelay;
    private Backoff serviceBackoff;
    private double defaultCuBudgetMultiplier = 1.13;
    private int maxTransactionRetries = 3;
    private DatasourceConfig datasourceConfig;

    protected ConfigParser(final ExecutorService taskExecutor, final HttpClient httpClient) {
      this.taskExecutor = taskExecutor;
      this.httpClient = httpClient;
    }

    private NotifyClient createNotifyClient(final List<WebHookConfig> webHookConfigs) {
      final var webHookClients = webHookConfigs.stream()
          .map(webHookConfig -> webHookConfig.createCaller(httpClient))
          .toList();
      return NotifyClient.createClient(
          taskExecutor,
          webHookClients,
          CallContext.createContext(1, 0, 8, true, 5, false)
      );
    }

    private void setDefaults() {
      if (notifyClient == null) {
        final var noop = List.<CompletableFuture<String>>of();
        notifyClient = _ -> noop;
      }
      if (sendClients == null) {
        sendClients = rpcClients;
      }
      if (tableCacheConfig == null) {
        tableCacheConfig = TableCacheConfig.createDefault();
      }
      if (maxSOLPriorityFee == null) {
        maxSOLPriorityFee = new BigDecimal("0.00042");
      }
      if (warnFeePayerBalance == null) {
        warnFeePayerBalance = new BigDecimal("0.05");
      }
      if (minFeePayerBalance == null) {
        minFeePayerBalance = new BigDecimal("0.01");
      }
      if (minCheckStateDelay == null) {
        minCheckStateDelay = Duration.ofSeconds(15);
      }
      if (maxCheckStateDelay == null) {
        maxCheckStateDelay = Duration.ofMinutes(5);
      }
      if (serviceBackoff == null) {
        serviceBackoff = DEFAULT_NETWORK_BACKOFF;
      }
      if (accountFetcherConfig == null) {
        accountFetcherConfig = AccountFetcherConfig.createDefault();
      }
      if (defensivePollingConfig == null) {
        defensivePollingConfig = DefensivePollingConfig.createDefaultConfig();
      }
    }

    protected final DelegateServiceConfig createBaseConfig() {
      setDefaults();
      return new BaseDelegateServiceConfig(
          Objects.requireNonNullElse(glamStateKey, PublicKey.NONE),
          signingServiceConfig,
          SolanaAccounts.MAIN_NET,
          formatter,
          notifyClient,
          cacheDirectory,
          tableCacheConfig,
          new RpcCaller(taskExecutor, rpcClients, callWeights),
          sendClients,
          feeProviders,
          websocketConfig,
          epochServiceConfig,
          txMonitorConfig,
          accountFetcherConfig,
          defensivePollingConfig,
          LamportDecimal.fromBigDecimal(maxSOLPriorityFee),
          LamportDecimal.fromBigDecimal(warnFeePayerBalance).toBigInteger(),
          LamportDecimal.fromBigDecimal(minFeePayerBalance).toBigInteger(),
          minCheckStateDelay, maxCheckStateDelay,
          serviceBackoff,
          defaultCuBudgetMultiplier,
          maxTransactionRetries,
          datasourceConfig
      );
    }

    @Override
    public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
      if (fieldEquals("glamStateKey", buf, offset, len)) {
        glamStateKey = PublicKeyEncoding.parseBase58Encoded(ji);
      } else if (fieldEquals("signingService", buf, offset, len)) {
        signingServiceConfig = SigningServiceConfig.parseConfig(taskExecutor, DEFAULT_NETWORK_BACKOFF, ji);
      } else if (fieldEquals("serviceBackoff", buf, offset, len)) {
        serviceBackoff = BackoffConfig.parseConfig(ji).createBackoff();
      } else if (fieldEquals("formatter", buf, offset, len)) {
        formatter = parseFormatter(ji);
      } else if (fieldEquals("notificationHooks", buf, offset, len)) {
        final var webHookConfigs = WebHookConfig.parseConfigs(
            ji,
            null,
            CapacityConfig.createSimpleConfig(
                Duration.ofSeconds(13),
                2,
                Duration.ofSeconds(1)
            ),
            DEFAULT_NETWORK_BACKOFF
        );
        this.notifyClient = createNotifyClient(webHookConfigs);
      } else if (fieldEquals("cacheDirectory", buf, offset, len)) {
        cacheDirectory = Path.of(ji.readString());
      } else if (fieldEquals("tableCache", buf, offset, len)) {
        tableCacheConfig = TableCacheConfig.parse(ji);
      } else if (fieldEquals("rpcCallWeights", buf, offset, len)) {
        callWeights = CallWeights.parse(ji);
      } else if (fieldEquals("rpc", buf, offset, len)) {
        final var loadBalancerConfig = LoadBalancerConfig.parse(
            ji,
            CapacityConfig.createSimpleConfig(
                Duration.ofSeconds(13),
                10,
                Duration.ofSeconds(1)
            ),
            DEFAULT_NETWORK_BACKOFF
        );
        defaultRPCBackoff = loadBalancerConfig.defaultBackoff();
        rpcClients = createRPCLoadBalancer(loadBalancerConfig, httpClient);
      } else if (fieldEquals("sendRPC", buf, offset, len)) {
        final var loadBalancerConfig = LoadBalancerConfig.parse(
            ji,
            CapacityConfig.createSimpleConfig(
                Duration.ofSeconds(5),
                1,
                Duration.ofSeconds(1)
            ),
            defaultRPCBackoff
        );
        sendClients = createRPCLoadBalancer(loadBalancerConfig, httpClient);
      } else if (fieldEquals("websocket", buf, offset, len)) {
        websocketConfig = RemoteResourceConfig.parseConfig(ji, null, DEFAULT_NETWORK_BACKOFF);
      } else if (fieldEquals("helius", buf, offset, len)) {
        final var heliusConfig = HeliusConfig.parseConfig(
            ji,
            CapacityConfig.createSimpleConfig(
                Duration.ofSeconds(5),
                3,
                Duration.ofSeconds(1)
            ),
            DEFAULT_NETWORK_BACKOFF
        );
        final var heliusClient = heliusConfig.createClient(httpClient);
        final var balancedItem = BalancedItem.createItem(
            new HeliusFeeProvider(heliusClient),
            heliusConfig.capacityMonitor(),
            requireNonNullElse(heliusConfig.backoff(), DEFAULT_NETWORK_BACKOFF)
        );
        this.feeProviders = LoadBalancer.createBalancer(balancedItem);
      } else if (fieldEquals("epochService", buf, offset, len)) {
        epochServiceConfig = EpochServiceConfig.parseConfig(ji);
      } else if (fieldEquals("txMonitor", buf, offset, len)) {
        txMonitorConfig = TxMonitorConfig.parseConfig(ji);
      } else if (fieldEquals("accountFetcher", buf, offset, len)) {
        accountFetcherConfig = AccountFetcherConfig.parseConfig(ji);
      } else if (fieldEquals("defensivePolling", buf, offset, len)) {
        defensivePollingConfig = DefensivePollingConfig.parseConfig(ji);
      } else if (fieldEquals("maxSOLPriorityFee", buf, offset, len)) {
        maxSOLPriorityFee = ji.readBigDecimalDropZeroes();
      } else if (fieldEquals("warnFeePayerBalance", buf, offset, len)) {
        warnFeePayerBalance = ji.readBigDecimalDropZeroes();
      } else if (fieldEquals("minFeePayerBalance", buf, offset, len)) {
        minFeePayerBalance = ji.readBigDecimalDropZeroes();
      } else if (fieldEquals("minCheckStateDelay", buf, offset, len)) {
        minCheckStateDelay = ServiceConfigUtil.parseDuration(ji);
      } else if (fieldEquals("maxCheckStateDelay", buf, offset, len)) {
        maxCheckStateDelay = ServiceConfigUtil.parseDuration(ji);
      } else if (fieldEquals("defaultCuBudgetMultiplier", buf, offset, len)) {
        defaultCuBudgetMultiplier = ji.readDouble();
      } else if (fieldEquals("maxTransactionRetries", buf, offset, len)) {
        maxTransactionRetries = ji.readInt();
      } else if (fieldEquals("datasource", buf, offset, len)) {
        datasourceConfig = DatasourceConfig.parseConfig(ji);
      } else {
        throw new IllegalStateException("Unknown service config field " + new String(buf, offset, len));
      }
      return true;
    }
  }
}
