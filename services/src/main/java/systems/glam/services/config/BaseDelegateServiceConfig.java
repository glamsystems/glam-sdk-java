package systems.glam.services.config;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.util.LamportDecimal;
import software.sava.kms.core.signing.SigningServiceConfig;
import software.sava.rpc.json.PublicKeyEncoding;
import software.sava.rpc.json.http.client.SolanaRpcClient;
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
import software.sava.services.solana.alt.TableCacheConfig;
import software.sava.services.solana.config.ChainItemFormatter;
import software.sava.services.solana.config.HeliusConfig;
import software.sava.services.solana.epoch.EpochServiceConfig;
import software.sava.services.solana.remote.call.CallWeights;
import software.sava.services.solana.remote.call.RpcCaller;
import software.sava.services.solana.transactions.HeliusFeeProvider;
import software.sava.services.solana.transactions.TxMonitorConfig;
import systems.comodal.jsoniter.FieldBufferPredicate;
import systems.comodal.jsoniter.JsonIterator;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static java.util.Objects.requireNonNullElse;
import static software.sava.services.solana.config.ChainItemFormatter.parseFormatter;
import static software.sava.services.solana.load_balance.LoadBalanceUtil.createRPCLoadBalancer;
import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

public record BaseDelegateServiceConfig(PublicKey glamStateKey,
                                        SigningServiceConfig signingServiceConfig,
                                        SolanaAccounts solanaAccounts,
                                        ChainItemFormatter formatter,
                                        NotifyClient notifyClient,
                                        Path glamStateAccountCacheDirectory,
                                        TableCacheConfig tableCacheConfig,
                                        RpcCaller rpcCaller,
                                        LoadBalancer<SolanaRpcClient> sendClients,
                                        LoadBalancer<HeliusFeeProvider> feeProviders,
                                        RemoteResourceConfig websocketConfig,
                                        EpochServiceConfig epochServiceConfig,
                                        TxMonitorConfig txMonitorConfig,
                                        BigDecimal maxLamportPriorityFee,
                                        BigInteger warnFeePayerBalance, BigInteger minFeePayerBalance,
                                        Duration minCheckStateDelay, Duration maxCheckStateDelay,
                                        Backoff serviceBackoff) implements DelegateServiceConfig {

  private static final Backoff DEFAULT_NETWORK_BACKOFF = Backoff.fibonacci(1, 21);

  public static class ConfigParser implements FieldBufferPredicate {

    private final ExecutorService taskExecutor;
    private final HttpClient httpClient;

    private PublicKey glamStateKey;
    private SigningServiceConfig signingServiceConfig;
    private ChainItemFormatter formatter;
    private NotifyClient notifyClient;
    private Path glamStateAccountCacheDirectory;
    private TableCacheConfig tableCacheConfig;
    private CallWeights callWeights;
    private Backoff defaultRPCBackoff = DEFAULT_NETWORK_BACKOFF;
    private LoadBalancer<SolanaRpcClient> rpcClients;
    private LoadBalancer<SolanaRpcClient> sendClients;
    private LoadBalancer<HeliusFeeProvider> feeProviders;
    private RemoteResourceConfig websocketConfig;
    private EpochServiceConfig epochServiceConfig;
    private TxMonitorConfig txMonitorConfig;
    private BigDecimal maxSOLPriorityFee;
    private BigDecimal warnFeePayerBalance;
    private BigDecimal minFeePayerBalance;
    private Duration minCheckStateDelay;
    private Duration maxCheckStateDelay;
    private Backoff serviceBackoff;

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
    }

    protected final DelegateServiceConfig createBaseConfig() {
      setDefaults();
      return new BaseDelegateServiceConfig(
          glamStateKey,
          signingServiceConfig,
          SolanaAccounts.MAIN_NET,
          formatter,
          notifyClient,
          glamStateAccountCacheDirectory,
          tableCacheConfig,
          new RpcCaller(taskExecutor, rpcClients, callWeights),
          sendClients,
          feeProviders,
          websocketConfig,
          epochServiceConfig,
          txMonitorConfig,
          LamportDecimal.fromBigDecimal(maxSOLPriorityFee),
          LamportDecimal.fromBigDecimal(warnFeePayerBalance).toBigInteger(),
          LamportDecimal.fromBigDecimal(minFeePayerBalance).toBigInteger(),
          minCheckStateDelay, maxCheckStateDelay,
          serviceBackoff
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
      } else if (fieldEquals("glamStateAccountCacheDirectory", buf, offset, len)) {
        glamStateAccountCacheDirectory = Path.of(ji.readString());
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
      } else {
        throw new IllegalStateException("Unknown service config field " + new String(buf, offset, len));
      }
      return true;
    }
  }
}
