package systems.glam.services.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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
import systems.glam.services.execution.ExecutionServiceContext;
import systems.glam.services.execution.InstructionProcessor;
import systems.glam.services.mints.MintCache;
import systems.glam.services.rpc.AccountFetcher;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNullElse;
import static software.sava.services.core.config.PropertiesParser.getProperty;
import static software.sava.services.core.config.PropertiesParser.propertyPrefix;
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
                                        SequencedCollection<String> hikariPropertiesFiles)
    implements DelegateServiceConfig {

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
    final DataSource dataSource;
    if (hikariPropertiesFiles == null || hikariPropertiesFiles.isEmpty()) {
      dataSource = null;
    } else {
      final var mergedProperties = ConfigUtils.joinPropertyFiles(hikariPropertiesFiles);
      final var hikariConfig = new HikariConfig(mergedProperties);
      dataSource = new HikariDataSource(hikariConfig);
    }
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
        dataSource
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
    private List<String> hikariPropertiesFiles;

    protected ConfigParser(final ExecutorService taskExecutor, final HttpClient httpClient) {
      this.taskExecutor = taskExecutor;
      this.httpClient = httpClient;
    }

    protected void parseProperties(final String prefix, final Properties properties) {
      final var p = propertyPrefix(prefix);

      final var glamStateKeyStr = getProperty(properties, p, "glamStateKey");
      if (glamStateKeyStr != null) {
        this.glamStateKey = PublicKey.fromBase58Encoded(glamStateKeyStr);
      }

      final var signingServicePrefix = p + "signingService.";
      if (properties.stringPropertyNames().stream().anyMatch(k -> k.startsWith(signingServicePrefix))) {
        this.signingServiceConfig = SigningServiceConfig.parseConfig(
            taskExecutor, signingServicePrefix, DEFAULT_NETWORK_BACKOFF, properties
        );
      }

      final var serviceBackoffPrefix = p + "serviceBackoff.";
      if (properties.stringPropertyNames().stream().anyMatch(k -> k.startsWith(serviceBackoffPrefix))) {
        final var backoffConfig = BackoffConfig.parse(serviceBackoffPrefix, properties);
        this.serviceBackoff = backoffConfig.createBackoff();
      }

      final var formatterPrefix = p + "formatter.";
      if (properties.stringPropertyNames().stream().anyMatch(k -> k.startsWith(formatterPrefix))) {
        this.formatter = ChainItemFormatter.parseConfig(formatterPrefix, properties);
      }

      final var notificationHooksPrefix = p + "notificationHooks.";
      if (properties.stringPropertyNames().stream().anyMatch(k -> k.startsWith(notificationHooksPrefix))) {
        final var webHookConfigs = WebHookConfig.parseConfigs(
            notificationHooksPrefix,
            properties,
            null,
            CapacityConfig.createSimpleConfig(
                Duration.ofSeconds(13),
                2,
                Duration.ofSeconds(1)
            ),
            DEFAULT_NETWORK_BACKOFF
        );
        this.notifyClient = createNotifyClient(webHookConfigs);
      }

      final var cacheDirectoryStr = getProperty(properties, p, "cacheDirectory");
      if (cacheDirectoryStr != null) {
        this.cacheDirectory = Path.of(cacheDirectoryStr);
      }

      final var tableCachePrefix = p + "tableCache.";
      if (properties.stringPropertyNames().stream().anyMatch(k -> k.startsWith(tableCachePrefix))) {
        this.tableCacheConfig = TableCacheConfig.parseConfig(tableCachePrefix, properties);
      }

      final var rpcCallWeightsPrefix = p + "rpcCallWeights.";
      if (properties.stringPropertyNames().stream().anyMatch(k -> k.startsWith(rpcCallWeightsPrefix))) {
        this.callWeights = CallWeights.parseConfig(rpcCallWeightsPrefix, properties);
      }

      final var rpcPrefix = p + "rpc.";
      if (properties.stringPropertyNames().stream().anyMatch(k -> k.startsWith(rpcPrefix))) {
        final var loadBalancerConfig = LoadBalancerConfig.parse(
            rpcPrefix,
            properties,
            CapacityConfig.createSimpleConfig(
                Duration.ofSeconds(13),
                10,
                Duration.ofSeconds(1)
            ),
            DEFAULT_NETWORK_BACKOFF
        );
        this.defaultRPCBackoff = loadBalancerConfig.defaultBackoff();
        this.rpcClients = createRPCLoadBalancer(loadBalancerConfig, httpClient);
      }

      final var sendRPCPrefix = p + "sendRPC.";
      if (properties.stringPropertyNames().stream().anyMatch(k -> k.startsWith(sendRPCPrefix))) {
        final var loadBalancerConfig = LoadBalancerConfig.parse(
            sendRPCPrefix,
            properties,
            CapacityConfig.createSimpleConfig(
                Duration.ofSeconds(5),
                1,
                Duration.ofSeconds(1)
            ),
            defaultRPCBackoff
        );
        this.sendClients = createRPCLoadBalancer(loadBalancerConfig, httpClient);
      }

      final var websocketPrefix = p + "websocket.";
      if (properties.stringPropertyNames().stream().anyMatch(k -> k.startsWith(websocketPrefix))) {
        this.websocketConfig = RemoteResourceConfig.parseConfig(websocketPrefix, properties, null, DEFAULT_NETWORK_BACKOFF);
      }

      final var epochServicePrefix = p + "epochService.";
      if (properties.stringPropertyNames().stream().anyMatch(k -> k.startsWith(epochServicePrefix))) {
        this.epochServiceConfig = EpochServiceConfig.parseConfig(epochServicePrefix, properties);
      }

      final var txMonitorPrefix = p + "txMonitor.";
      if (properties.stringPropertyNames().stream().anyMatch(k -> k.startsWith(txMonitorPrefix))) {
        this.txMonitorConfig = TxMonitorConfig.parseConfig(txMonitorPrefix, properties);
      }

      final var accountFetcherPrefix = p + "accountFetcher.";
      if (properties.stringPropertyNames().stream().anyMatch(k -> k.startsWith(accountFetcherPrefix))) {
        this.accountFetcherConfig = AccountFetcherConfig.parseConfig(accountFetcherPrefix, properties);
      }

      final var defensivePollingPrefix = p + "defensivePolling.";
      if (properties.stringPropertyNames().stream().anyMatch(k -> k.startsWith(defensivePollingPrefix))) {
        this.defensivePollingConfig = DefensivePollingConfig.parseConfig(defensivePollingPrefix, properties);
      }

      final var heliusPrefix = p + "helius.";
      if (properties.stringPropertyNames().stream().anyMatch(k -> k.startsWith(heliusPrefix))) {
        final var heliusConfig = HeliusConfig.parse(heliusPrefix, properties);
        final var heliusClient = heliusConfig.createClient(httpClient);
        final var balancedItem = BalancedItem.createItem(
            new HeliusFeeProvider(heliusClient),
            heliusConfig.capacityMonitor(),
            requireNonNullElse(heliusConfig.backoff(), DEFAULT_NETWORK_BACKOFF)
        );
        this.feeProviders = LoadBalancer.createBalancer(balancedItem);
      }

      final var maxSOLPriorityFeeStr = getProperty(properties, p, "maxSOLPriorityFee");
      if (maxSOLPriorityFeeStr != null) {
        this.maxSOLPriorityFee = new BigDecimal(maxSOLPriorityFeeStr);
      }

      final var warnFeePayerBalanceStr = getProperty(properties, p, "warnFeePayerBalance");
      if (warnFeePayerBalanceStr != null) {
        this.warnFeePayerBalance = new BigDecimal(warnFeePayerBalanceStr);
      }

      final var minFeePayerBalanceStr = getProperty(properties, p, "minFeePayerBalance");
      if (minFeePayerBalanceStr != null) {
        this.minFeePayerBalance = new BigDecimal(minFeePayerBalanceStr);
      }

      final var minCheckStateDelayStr = getProperty(properties, p, "minCheckStateDelay");
      if (minCheckStateDelayStr != null) {
        this.minCheckStateDelay = ServiceConfigUtil.parseDuration(minCheckStateDelayStr);
      }

      final var maxCheckStateDelayStr = getProperty(properties, p, "maxCheckStateDelay");
      if (maxCheckStateDelayStr != null) {
        this.maxCheckStateDelay = ServiceConfigUtil.parseDuration(maxCheckStateDelayStr);
      }

      final var defaultCuBudgetMultiplierStr = getProperty(properties, p, "defaultCuBudgetMultiplier");
      if (defaultCuBudgetMultiplierStr != null) {
        this.defaultCuBudgetMultiplier = Double.parseDouble(defaultCuBudgetMultiplierStr);
      }

      final var maxTransactionRetriesStr = getProperty(properties, p, "maxTransactionRetries");
      if (maxTransactionRetriesStr != null) {
        this.maxTransactionRetries = Integer.parseInt(maxTransactionRetriesStr);
      }

      final var hikariPropertiesFilesStr = getProperty(properties, p, "hikariPropertiesFiles");
      if (hikariPropertiesFilesStr != null) {
        this.hikariPropertiesFiles = List.of(hikariPropertiesFilesStr.split(","));
      }
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
          hikariPropertiesFiles == null ? List.of() : hikariPropertiesFiles
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
      } else if (fieldEquals("hikariPropertiesFiles", buf, offset, len)) {
        final var files = new ArrayList<String>();
        while (ji.readArray()) {
          files.add(ji.readString());
        }
        hikariPropertiesFiles = List.copyOf(files);
      } else {
        throw new IllegalStateException("Unknown service config field " + new String(buf, offset, len));
      }
      return true;
    }
  }
}
