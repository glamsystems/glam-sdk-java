package systems.glam.services.oracles.scope;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.kamino.KaminoAccounts;
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
import software.sava.services.core.remote.load_balance.LoadBalancer;
import software.sava.services.core.remote.load_balance.LoadBalancerConfig;
import software.sava.services.core.request_capacity.CapacityConfig;
import software.sava.services.core.request_capacity.context.CallContext;
import software.sava.services.solana.remote.call.CallWeights;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.comodal.jsoniter.JsonIterator;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import static software.sava.services.solana.load_balance.LoadBalanceUtil.createRPCLoadBalancer;
import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

record ScopeMonitorConfig(Duration pollingDelay,
                          Path configurationsPath,
                          Path mappingsPath,
                          Path reserveContextsFilePath,
                          NotifyClient notifyClient,
                          RpcCaller rpcCaller,
                          CallWeights rpcCallWeights,
                          CallContext getProgramAccountsCallContext,
                          RemoteResourceConfig websocketConfig,
                          KaminoAccounts kaminoAccounts,
                          Set<PublicKey> scopeConfigurationKeys) {

  private static final Backoff DEFAULT_NETWORK_BACKOFF = Backoff.fibonacci(1, 21);

  static ScopeMonitorConfig loadConfig(final ExecutorService taskExecutor, final HttpClient rpcHttpClient) {
    final var propertyValue = System.getProperty("systems.glam.services.oracles.scope.config");
    return ServiceConfigUtil.loadConfig(
        Path.of(propertyValue),
        new ConfigParser(taskExecutor, rpcHttpClient)
    );
  }

  private static final class ConfigParser implements Parser<ScopeMonitorConfig> {

    private final ExecutorService taskExecutor;
    private final HttpClient httpClient;

    private final Set<PublicKey> configurationKeys = new LinkedHashSet<>();
    private Duration pollingDelay;
    private Path configurationsPath;
    private Path mappingsPath;
    private Path reserveContextsFilePath;
    private CallWeights rpcCallWeights;
    private LoadBalancer<SolanaRpcClient> rpcClients;
    private RemoteResourceConfig websocketConfig;
    private List<WebHookConfig> webHookConfigs;

    private ConfigParser(final ExecutorService taskExecutor, final HttpClient httpClient) {
      this.taskExecutor = taskExecutor;
      this.httpClient = httpClient;
    }

    @Override
    public ScopeMonitorConfig createConfig() {
      final var webHookClients = webHookConfigs == null ? List.<ClientCaller<WebHookClient>>of() : webHookConfigs.stream()
          .map(webHookConfig -> webHookConfig.createCaller(httpClient))
          .toList();
      final var notifyClient = NotifyClient.createClient(
          taskExecutor,
          webHookClients,
          CallContext.createContext(1, 0, 8, true, 5, false)
      );

      if (rpcCallWeights == null) {
        rpcCallWeights = new CallWeights(2, 5, 10);
      }
      final var getProgramAccountsCallContext = CallContext.createContext(
          rpcCallWeights.getProgramAccounts(),
          rpcCallWeights.getProgramAccounts() >> 1,
          rpcClients.size(),
          false,
          2,
          false
      );

      return new ScopeMonitorConfig(
          pollingDelay == null ? Duration.ofMinutes(15) : pollingDelay,
          Objects.requireNonNull(configurationsPath),
          Objects.requireNonNull(mappingsPath),
          Objects.requireNonNull(reserveContextsFilePath),
          notifyClient,
          new RpcCaller(taskExecutor, rpcClients, rpcCallWeights),
          rpcCallWeights,
          getProgramAccountsCallContext,
          Objects.requireNonNull(websocketConfig),
          KaminoAccounts.MAIN_NET,
          configurationKeys == null ? Set.of() : Set.copyOf(configurationKeys)
      );
    }

    @Override
    public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
      if (JsonIterator.fieldEquals("scopeConfigurations", buf, offset, len)) {
        while (ji.readArray()) {
          configurationKeys.add(PublicKeyEncoding.parseBase58Encoded(ji));
        }
      } else if (JsonIterator.fieldEquals("configurationsPath", buf, offset, len)) {
        configurationsPath = Path.of(ji.readString());
      } else if (JsonIterator.fieldEquals("mappingsPath", buf, offset, len)) {
        mappingsPath = Path.of(ji.readString());
      } else if (JsonIterator.fieldEquals("reserveContextsFilePath", buf, offset, len)) {
        reserveContextsFilePath = Path.of(ji.readString());
      } else if (JsonIterator.fieldEquals("rpcCallWeights", buf, offset, len)) {
        rpcCallWeights = CallWeights.parse(ji);
      } else if (fieldEquals("websocket", buf, offset, len)) {
        websocketConfig = RemoteResourceConfig.parseConfig(ji, null, DEFAULT_NETWORK_BACKOFF);
      } else if (fieldEquals("notificationHooks", buf, offset, len)) {
        this.webHookConfigs = WebHookConfig.parseConfigs(
            ji,
            null,
            CapacityConfig.createSimpleConfig(
                Duration.ofSeconds(13),
                2,
                Duration.ofSeconds(1)
            ),
            DEFAULT_NETWORK_BACKOFF
        );
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
        rpcClients = createRPCLoadBalancer(loadBalancerConfig, httpClient);
      } else if (JsonIterator.fieldEquals("pollingDelay", buf, offset, len)) {
        pollingDelay = ServiceConfigUtil.parseDuration(ji);
      } else {
        throw new IllegalStateException("Unknown ScopeMonitorConfig field: " + new String(buf, offset, len));
      }
      return true;
    }
  }
}
