package systems.glam.services.fulfillment;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.util.LamportDecimal;
import software.sava.kms.core.signing.SigningServiceConfig;
import software.sava.rpc.json.PublicKeyEncoding;
import software.sava.rpc.json.http.client.SolanaRpcClient;
import software.sava.services.core.config.Parser;
import software.sava.services.core.config.RemoteResourceConfig;
import software.sava.services.core.config.ServiceConfigUtil;
import software.sava.services.core.net.http.NotifyClient;
import software.sava.services.core.net.http.WebHookClient;
import software.sava.services.core.net.http.WebHookConfig;
import software.sava.services.core.remote.call.Backoff;
import software.sava.services.core.remote.call.ClientCaller;
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
import systems.comodal.jsoniter.JsonIterator;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static java.util.Objects.requireNonNullElse;
import static software.sava.services.solana.config.ChainItemFormatter.parseFormatter;
import static software.sava.services.solana.load_balance.LoadBalanceUtil.createRPCLoadBalancer;
import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

public class BaseDelegateServiceConfig implements DelegateServiceConfig {

  private static final Backoff DEFAULT_NETWORK_BACKOFF = Backoff.fibonacci(1, 21);

  private final PublicKey glamStateKey;
  private final SigningServiceConfig signingServiceConfig;
  private final SolanaAccounts solanaAccounts;
  private final ChainItemFormatter formatter;
  private final NotifyClient notifyClient;
  private final TableCacheConfig tableCacheConfig;
  private final RpcCaller rpcCaller;
  private final LoadBalancer<SolanaRpcClient> sendClients;
  private final LoadBalancer<HeliusFeeProvider> feeProviders;
  private final RemoteResourceConfig websocketConfig;
  private final EpochServiceConfig epochServiceConfig;
  private final TxMonitorConfig txMonitorConfig;
  private final BigDecimal maxLamportPriorityFee;
  private final BigInteger warnFeePayerBalance;
  private final BigInteger minFeePayerBalance;
  private final Duration minCheckStateDelay;
  private final Duration maxCheckStateDelay;
  private final Backoff serviceBackoff;

  public BaseDelegateServiceConfig(final PublicKey glamStateKey,
                                   final SigningServiceConfig signingServiceConfig,
                                   final SolanaAccounts solanaAccounts,
                                   final ChainItemFormatter formatter,
                                   final NotifyClient notifyClient, final TableCacheConfig tableCacheConfig,
                                   final RpcCaller rpcCaller,
                                   final LoadBalancer<SolanaRpcClient> sendClients,
                                   final LoadBalancer<HeliusFeeProvider> feeProviders,
                                   final RemoteResourceConfig websocketConfig,
                                   final EpochServiceConfig epochServiceConfig,
                                   final TxMonitorConfig txMonitorConfig,
                                   final BigDecimal maxLamportPriorityFee,
                                   final BigInteger warnFeePayerBalance,
                                   final BigInteger minFeePayerBalance,
                                   final Duration minCheckStateDelay,
                                   final Duration maxCheckStateDelay,
                                   final Backoff serviceBackoff) {
    this.glamStateKey = glamStateKey;
    this.signingServiceConfig = signingServiceConfig;
    this.solanaAccounts = solanaAccounts;
    this.formatter = formatter;
    this.notifyClient = notifyClient;
    this.tableCacheConfig = tableCacheConfig;
    this.rpcCaller = rpcCaller;
    this.sendClients = sendClients;
    this.feeProviders = feeProviders;
    this.websocketConfig = websocketConfig;
    this.epochServiceConfig = epochServiceConfig;
    this.txMonitorConfig = txMonitorConfig;
    this.maxLamportPriorityFee = maxLamportPriorityFee;
    this.warnFeePayerBalance = warnFeePayerBalance;
    this.minFeePayerBalance = minFeePayerBalance;
    this.minCheckStateDelay = minCheckStateDelay;
    this.maxCheckStateDelay = maxCheckStateDelay;
    this.serviceBackoff = serviceBackoff;
  }

  @Override
  public final PublicKey glamStateKey() {
    return glamStateKey;
  }

  @Override
  public final SigningServiceConfig signingServiceConfig() {
    return signingServiceConfig;
  }

  @Override
  public final SolanaAccounts solanaAccounts() {
    return solanaAccounts;
  }

  @Override
  public final ChainItemFormatter formatter() {
    return formatter;
  }

  @Override
  public final NotifyClient notifyClient() {
    return notifyClient;
  }

  @Override
  public final TableCacheConfig tableCacheConfig() {
    return tableCacheConfig;
  }

  @Override
  public final RpcCaller rpcCaller() {
    return rpcCaller;
  }

  @Override
  public final LoadBalancer<SolanaRpcClient> sendClients() {
    return sendClients;
  }

  @Override
  public final LoadBalancer<HeliusFeeProvider> feeProviders() {
    return feeProviders;
  }

  @Override
  public final RemoteResourceConfig websocketConfig() {
    return websocketConfig;
  }

  @Override
  public final EpochServiceConfig epochServiceConfig() {
    return epochServiceConfig;
  }

  @Override
  public final TxMonitorConfig txMonitorConfig() {
    return txMonitorConfig;
  }

  @Override
  public final BigDecimal maxLamportPriorityFee() {
    return maxLamportPriorityFee;
  }

  @Override
  public final BigInteger warnFeePayerBalance() {
    return warnFeePayerBalance;
  }

  @Override
  public final BigInteger minFeePayerBalance() {
    return minFeePayerBalance;
  }

  @Override
  public final Duration minCheckStateDelay() {
    return minCheckStateDelay;
  }

  @Override
  public final Duration maxCheckStateDelay() {
    return maxCheckStateDelay;
  }

  @Override
  public final Backoff serviceBackoff() {
    return serviceBackoff;
  }

  static final class ConfigParser implements Parser<DelegateServiceConfig> {

    private final ExecutorService taskExecutor;
    private final HttpClient httpClient;

    private PublicKey glamStateKey;
    private SigningServiceConfig signingServiceConfig;
    private ChainItemFormatter formatter;
    private TableCacheConfig tableCacheConfig;
    private List<WebHookConfig> webHookConfigs;
    private CallWeights callWeights;
    private Backoff defaultRPCBackoff = DEFAULT_NETWORK_BACKOFF;
    private LoadBalancer<SolanaRpcClient> rpcClients;
    private LoadBalancer<SolanaRpcClient> sendClients;
    private HeliusConfig heliusConfig;
    private RemoteResourceConfig websocketConfig;
    private EpochServiceConfig epochServiceConfig;
    private TxMonitorConfig txMonitorConfig;
    private BigDecimal maxSOLPriorityFee;
    private BigDecimal warnFeePayerBalance;
    private BigDecimal minFeePayerBalance;
    private Duration minCheckStateDelay;
    private Duration maxCheckStateDelay;
    private Backoff serviceBackoff;

    ConfigParser(final ExecutorService taskExecutor, final HttpClient httpClient) {
      this.taskExecutor = taskExecutor;
      this.httpClient = httpClient;
    }

    private NotifyClient createNotifyClient() {
      final var webHookClients = webHookConfigs == null ? List.<ClientCaller<WebHookClient>>of() : webHookConfigs.stream()
          .map(webHookConfig -> webHookConfig.createCaller(httpClient))
          .toList();
      return NotifyClient.createClient(
          taskExecutor,
          webHookClients,
          CallContext.createContext(1, 0, 8, true, 5, false)
      );
    }

    private LoadBalancer<HeliusFeeProvider> createFeeProviderLoadBalancer() {
      final var heliusClient = heliusConfig.createClient(httpClient);
      final var balancedItem = BalancedItem.createItem(
          new HeliusFeeProvider(heliusClient),
          heliusConfig.capacityMonitor(),
          requireNonNullElse(heliusConfig.backoff(), DEFAULT_NETWORK_BACKOFF)
      );
      return LoadBalancer.createBalancer(balancedItem);
    }

    @Override
    public DelegateServiceConfig createConfig() {
      final var notifyClient = createNotifyClient();
      final var heliusLoadBalancer = createFeeProviderLoadBalancer();

      return new BaseDelegateServiceConfig(
          glamStateKey,
          signingServiceConfig,
          SolanaAccounts.MAIN_NET,
          formatter,
          notifyClient,
          tableCacheConfig == null ? TableCacheConfig.createDefault() : tableCacheConfig,
          new RpcCaller(taskExecutor, rpcClients, callWeights),
          requireNonNullElse(sendClients, rpcClients),
          heliusLoadBalancer,
          websocketConfig,
          epochServiceConfig,
          txMonitorConfig,
          LamportDecimal.fromBigDecimal(maxSOLPriorityFee == null ? new BigDecimal("0.00042") : maxSOLPriorityFee),
          LamportDecimal.fromBigDecimal(warnFeePayerBalance == null ? new BigDecimal("0.05") : warnFeePayerBalance).toBigInteger(),
          LamportDecimal.fromBigDecimal(minFeePayerBalance == null ? new BigDecimal("0.01") : minFeePayerBalance).toBigInteger(),
          minCheckStateDelay == null ? Duration.ofSeconds(15) : minCheckStateDelay,
          maxCheckStateDelay == null ? Duration.ofMinutes(5) : maxCheckStateDelay,
          requireNonNullElse(serviceBackoff, DEFAULT_NETWORK_BACKOFF)
      );
    }

    @Override
    public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
      if (fieldEquals("glamStateKey", buf, offset, len)) {
        glamStateKey = PublicKeyEncoding.parseBase58Encoded(ji);
      } else if (fieldEquals("signingService", buf, offset, len)) {
        signingServiceConfig = SigningServiceConfig.parseConfig(taskExecutor, DEFAULT_NETWORK_BACKOFF, ji);
      } else if (fieldEquals("formatter", buf, offset, len)) {
        formatter = parseFormatter(ji);
      } else if (fieldEquals("notificationHooks", buf, offset, len)) {
        this.webHookConfigs = WebHookConfig.parseConfigs(
            ji,
            null,
            CapacityConfig.createSimpleConfig(
                java.time.Duration.ofSeconds(13),
                2,
                java.time.Duration.ofSeconds(1)
            ),
            DEFAULT_NETWORK_BACKOFF
        );
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
        heliusConfig = HeliusConfig.parseConfig(
            ji,
            CapacityConfig.createSimpleConfig(
                Duration.ofSeconds(5),
                3,
                Duration.ofSeconds(1)
            ),
            DEFAULT_NETWORK_BACKOFF
        );
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
