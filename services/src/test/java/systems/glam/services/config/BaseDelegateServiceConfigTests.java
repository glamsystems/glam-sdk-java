package systems.glam.services.config;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.util.LamportDecimal;
import systems.comodal.jsoniter.JsonIterator;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

final class BaseDelegateServiceConfigTests {

  private static final String RPC_ENDPOINT = "https://api.mainnet-beta.solana.com";
  private static final String WS_ENDPOINT = "wss://api.mainnet-beta.solana.com";

  private static DelegateServiceConfig parseJson(final String json) {
    try (final var httpClient = HttpClient.newHttpClient()) {
      final var parser = new BaseDelegateServiceConfig.ConfigParser(
          Executors.newVirtualThreadPerTaskExecutor(), httpClient
      );
      final var ji = JsonIterator.parse(json);
      ji.testObject(parser);
      return parser.createBaseConfig();
    }
  }

  private static DelegateServiceConfig parseProperties(final Properties properties) {
    return parseProperties("", properties);
  }

  private static DelegateServiceConfig parseProperties(final String prefix, final Properties properties) {
    try (final var httpClient = HttpClient.newHttpClient()) {
      final var parser = new BaseDelegateServiceConfig.ConfigParser(
          Executors.newVirtualThreadPerTaskExecutor(), httpClient
      );
      parser.parseProperties(prefix, properties);
      return parser.createBaseConfig();
    }
  }

  private static Properties minimalRpcProperties(final String prefix) {
    final var properties = new Properties();
    properties.setProperty(prefix + "rpc.endpoints.0.url", RPC_ENDPOINT);
    properties.setProperty(prefix + "websocket.endpoint", WS_ENDPOINT);
    return properties;
  }

  private static String minimalRpcJson() {
    return """
        "rpc": {
          "endpoints": [
            {"url": "%s"}
          ]
        },
        "websocket": {
          "endpoint": "%s"
        }
        """.formatted(RPC_ENDPOINT, WS_ENDPOINT);
  }

  @Test
  void testJsonDefaults() {
    final var json = """
        {
          %s
        }
        """.formatted(minimalRpcJson());
    final var config = parseJson(json);

    assertEquals(PublicKey.NONE, config.glamStateKey());
    assertNull(config.signingServiceConfig());
    assertEquals(LamportDecimal.fromBigDecimal(new BigDecimal("0.00042")), config.maxLamportPriorityFee());
    assertEquals(LamportDecimal.fromBigDecimal(new BigDecimal("0.05")).toBigInteger(), config.warnFeePayerBalance());
    assertEquals(LamportDecimal.fromBigDecimal(new BigDecimal("0.01")).toBigInteger(), config.minFeePayerBalance());
    assertEquals(Duration.ofSeconds(15), config.minCheckStateDelay());
    assertEquals(Duration.ofMinutes(5), config.maxCheckStateDelay());
    assertEquals(1.13, config.defaultCuBudgetMultiplier(), 0.001);
    assertEquals(3, config.maxTransactionRetries());
    assertEquals(List.of(), config.hikariPropertiesFiles());
    assertNull(config.epochServiceConfig());
    assertNull(config.txMonitorConfig());
    assertNotNull(config.accountFetcherConfig());
    assertNotNull(config.defensivePollingConfig());
  }

  @Test
  void testPropertiesDefaults() {
    final var properties = minimalRpcProperties("");
    final var config = parseProperties(properties);

    assertEquals(PublicKey.NONE, config.glamStateKey());
    assertNull(config.signingServiceConfig());
    assertEquals(LamportDecimal.fromBigDecimal(new BigDecimal("0.00042")), config.maxLamportPriorityFee());
    assertEquals(LamportDecimal.fromBigDecimal(new BigDecimal("0.05")).toBigInteger(), config.warnFeePayerBalance());
    assertEquals(LamportDecimal.fromBigDecimal(new BigDecimal("0.01")).toBigInteger(), config.minFeePayerBalance());
    assertEquals(Duration.ofSeconds(15), config.minCheckStateDelay());
    assertEquals(Duration.ofMinutes(5), config.maxCheckStateDelay());
    assertEquals(1.13, config.defaultCuBudgetMultiplier(), 0.001);
    assertEquals(3, config.maxTransactionRetries());
    assertEquals(List.of(), config.hikariPropertiesFiles());
    assertNull(config.epochServiceConfig());
    assertNull(config.txMonitorConfig());
    assertNotNull(config.accountFetcherConfig());
    assertNotNull(config.defensivePollingConfig());
  }

  @Test
  void testJsonScalarFields() {
    final var glamKey = "11111111111111111111111111111111";
    final var json = """
        {
          %s,
          "glamStateKey": "%s",
          "cacheDirectory": "/tmp/test-cache",
          "maxSOLPriorityFee": 0.001,
          "warnFeePayerBalance": 0.1,
          "minFeePayerBalance": 0.02,
          "minCheckStateDelay": "PT30S",
          "maxCheckStateDelay": "PT10M",
          "defaultCuBudgetMultiplier": 1.5,
          "maxTransactionRetries": 5,
          "hikariPropertiesFiles": ["db1.properties", "db2.properties"]
        }
        """.formatted(minimalRpcJson(), glamKey);
    final var config = parseJson(json);

    assertEquals(PublicKey.fromBase58Encoded(glamKey), config.glamStateKey());
    assertEquals(Path.of("/tmp/test-cache"), config.cacheDirectory());
    assertEquals(LamportDecimal.fromBigDecimal(new BigDecimal("0.001")), config.maxLamportPriorityFee());
    assertEquals(LamportDecimal.fromBigDecimal(new BigDecimal("0.1")).toBigInteger(), config.warnFeePayerBalance());
    assertEquals(LamportDecimal.fromBigDecimal(new BigDecimal("0.02")).toBigInteger(), config.minFeePayerBalance());
    assertEquals(Duration.ofSeconds(30), config.minCheckStateDelay());
    assertEquals(Duration.ofMinutes(10), config.maxCheckStateDelay());
    assertEquals(1.5, config.defaultCuBudgetMultiplier(), 0.001);
    assertEquals(5, config.maxTransactionRetries());
    assertEquals(List.of("db1.properties", "db2.properties"), config.hikariPropertiesFiles());
  }

  @Test
  void testPropertiesScalarFields() {
    final var glamKey = "11111111111111111111111111111111";
    final var properties = minimalRpcProperties("");
    properties.setProperty("glamStateKey", glamKey);
    properties.setProperty("cacheDirectory", "/tmp/test-cache");
    properties.setProperty("maxSOLPriorityFee", "0.001");
    properties.setProperty("warnFeePayerBalance", "0.1");
    properties.setProperty("minFeePayerBalance", "0.02");
    properties.setProperty("minCheckStateDelay", "PT30S");
    properties.setProperty("maxCheckStateDelay", "PT10M");
    properties.setProperty("defaultCuBudgetMultiplier", "1.5");
    properties.setProperty("maxTransactionRetries", "5");
    properties.setProperty("hikariPropertiesFiles", "db1.properties,db2.properties");

    final var config = parseProperties(properties);

    assertEquals(PublicKey.fromBase58Encoded(glamKey), config.glamStateKey());
    assertEquals(Path.of("/tmp/test-cache"), config.cacheDirectory());
    assertEquals(LamportDecimal.fromBigDecimal(new BigDecimal("0.001")), config.maxLamportPriorityFee());
    assertEquals(LamportDecimal.fromBigDecimal(new BigDecimal("0.1")).toBigInteger(), config.warnFeePayerBalance());
    assertEquals(LamportDecimal.fromBigDecimal(new BigDecimal("0.02")).toBigInteger(), config.minFeePayerBalance());
    assertEquals(Duration.ofSeconds(30), config.minCheckStateDelay());
    assertEquals(Duration.ofMinutes(10), config.maxCheckStateDelay());
    assertEquals(1.5, config.defaultCuBudgetMultiplier(), 0.001);
    assertEquals(5, config.maxTransactionRetries());
    assertEquals(List.of("db1.properties", "db2.properties"), config.hikariPropertiesFiles());
  }

  @Test
  void testPropertiesWithPrefix() {
    final var glamKey = "11111111111111111111111111111111";
    final var properties = minimalRpcProperties("svc.");
    properties.setProperty("svc.glamStateKey", glamKey);
    properties.setProperty("svc.cacheDirectory", "/tmp/prefix-cache");
    properties.setProperty("svc.maxSOLPriorityFee", "0.002");
    properties.setProperty("svc.defaultCuBudgetMultiplier", "2.0");
    properties.setProperty("svc.maxTransactionRetries", "7");

    final var config = parseProperties("svc", properties);

    assertEquals(PublicKey.fromBase58Encoded(glamKey), config.glamStateKey());
    assertEquals(Path.of("/tmp/prefix-cache"), config.cacheDirectory());
    assertEquals(LamportDecimal.fromBigDecimal(new BigDecimal("0.002")), config.maxLamportPriorityFee());
    assertEquals(2.0, config.defaultCuBudgetMultiplier(), 0.001);
    assertEquals(7, config.maxTransactionRetries());
  }

  @Test
  void testJsonEpochServiceConfig() {
    final var json = """
        {
          %s,
          "epochService": {
            "defaultMillisPerSlot": 500,
            "minMillisPerSlot": 380,
            "maxMillisPerSlot": 600,
            "slotSampleWindow": "PT30M",
            "fetchSlotSamplesDelay": "PT10M",
            "fetchEpochInfoAfterEndDelay": "PT2S"
          }
        }
        """.formatted(minimalRpcJson());
    final var config = parseJson(json);
    final var epoch = config.epochServiceConfig();

    assertEquals(500, epoch.defaultMillisPerSlot());
    assertEquals(380, epoch.minMillisPerSlot());
    assertEquals(600, epoch.maxMillisPerSlot());
    assertEquals(Duration.ofMinutes(30), epoch.slotSampleWindow());
    assertEquals(Duration.ofMinutes(10), epoch.fetchSlotSamplesDelay());
    assertEquals(Duration.ofSeconds(2), epoch.fetchEpochInfoAfterEndDelay());
  }

  @Test
  void testPropertiesEpochServiceConfig() {
    final var properties = minimalRpcProperties("");
    properties.setProperty("epochService.defaultMillisPerSlot", "500");
    properties.setProperty("epochService.minMillisPerSlot", "380");
    properties.setProperty("epochService.maxMillisPerSlot", "600");
    properties.setProperty("epochService.slotSampleWindow", "PT30M");
    properties.setProperty("epochService.fetchSlotSamplesDelay", "PT10M");
    properties.setProperty("epochService.fetchEpochInfoAfterEndDelay", "PT2S");

    final var config = parseProperties(properties);
    final var epoch = config.epochServiceConfig();

    assertEquals(500, epoch.defaultMillisPerSlot());
    assertEquals(380, epoch.minMillisPerSlot());
    assertEquals(600, epoch.maxMillisPerSlot());
    assertEquals(Duration.ofMinutes(30), epoch.slotSampleWindow());
    assertEquals(Duration.ofMinutes(10), epoch.fetchSlotSamplesDelay());
    assertEquals(Duration.ofSeconds(2), epoch.fetchEpochInfoAfterEndDelay());
  }

  @Test
  void testJsonTxMonitorConfig() {
    final var json = """
        {
          %s,
          "txMonitor": {
            "minSleepBetweenSigStatusPolling": "PT3S",
            "webSocketConfirmationTimeout": "PT45S",
            "retrySendDelay": "PT5S",
            "minBlocksRemainingToResend": 10
          }
        }
        """.formatted(minimalRpcJson());
    final var config = parseJson(json);
    final var txMonitor = config.txMonitorConfig();

    assertEquals(Duration.ofSeconds(3), txMonitor.minSleepBetweenSigStatusPolling());
    assertEquals(Duration.ofSeconds(45), txMonitor.webSocketConfirmationTimeout());
    assertEquals(Duration.ofSeconds(5), txMonitor.retrySendDelay());
    assertEquals(10, txMonitor.minBlocksRemainingToResend());
  }

  @Test
  void testPropertiesTxMonitorConfig() {
    final var properties = minimalRpcProperties("");
    properties.setProperty("txMonitor.minSleepBetweenSigStatusPolling", "PT3S");
    properties.setProperty("txMonitor.webSocketConfirmationTimeout", "PT45S");
    properties.setProperty("txMonitor.retrySendDelay", "PT5S");
    properties.setProperty("txMonitor.minBlocksRemainingToResend", "10");

    final var config = parseProperties(properties);
    final var txMonitor = config.txMonitorConfig();

    assertEquals(Duration.ofSeconds(3), txMonitor.minSleepBetweenSigStatusPolling());
    assertEquals(Duration.ofSeconds(45), txMonitor.webSocketConfirmationTimeout());
    assertEquals(Duration.ofSeconds(5), txMonitor.retrySendDelay());
    assertEquals(10, txMonitor.minBlocksRemainingToResend());
  }

  @Test
  void testJsonAccountFetcherConfig() {
    final var json = """
        {
          %s,
          "accountFetcher": {
            "fetchDelay": "PT10S",
            "reactive": true
          }
        }
        """.formatted(minimalRpcJson());
    final var config = parseJson(json);
    final var fetcher = config.accountFetcherConfig();

    assertEquals(Duration.ofSeconds(10), fetcher.fetchDelay());
    assertTrue(fetcher.reactive());
  }

  @Test
  void testPropertiesAccountFetcherConfig() {
    final var properties = minimalRpcProperties("");
    properties.setProperty("accountFetcher.fetchDelay", "PT10S");
    properties.setProperty("accountFetcher.reactive", "true");

    final var config = parseProperties(properties);
    final var fetcher = config.accountFetcherConfig();

    assertEquals(Duration.ofSeconds(10), fetcher.fetchDelay());
    assertTrue(fetcher.reactive());
  }

  @Test
  void testJsonDefensivePollingConfig() {
    final var json = """
        {
          %s,
          "defensivePolling": {
            "globalConfig": "PT2M",
            "glamStateAccounts": "PT4H",
            "integTables": "PT2H",
            "stakePools": "PT6H",
            "kaminoScope": "PT3H"
          }
        }
        """.formatted(minimalRpcJson());
    final var config = parseJson(json);
    final var polling = config.defensivePollingConfig();

    assertEquals(Duration.ofMinutes(2), polling.globalConfig());
    assertEquals(Duration.ofHours(4), polling.glamStateAccounts());
    assertEquals(Duration.ofHours(2), polling.integTables());
    assertEquals(Duration.ofHours(6), polling.stakePools());
    assertEquals(Duration.ofHours(3), polling.kaminoScope());
  }

  @Test
  void testPropertiesDefensivePollingConfig() {
    final var properties = minimalRpcProperties("");
    properties.setProperty("defensivePolling.globalConfig", "PT2M");
    properties.setProperty("defensivePolling.glamStateAccounts", "PT4H");
    properties.setProperty("defensivePolling.integTables", "PT2H");
    properties.setProperty("defensivePolling.stakePools", "PT6H");
    properties.setProperty("defensivePolling.kaminoScope", "PT3H");

    final var config = parseProperties(properties);
    final var polling = config.defensivePollingConfig();

    assertEquals(Duration.ofMinutes(2), polling.globalConfig());
    assertEquals(Duration.ofHours(4), polling.glamStateAccounts());
    assertEquals(Duration.ofHours(2), polling.integTables());
    assertEquals(Duration.ofHours(6), polling.stakePools());
    assertEquals(Duration.ofHours(3), polling.kaminoScope());
  }

  @Test
  void testJsonAndPropertiesProduceSameConfig() {
    final var glamKey = "11111111111111111111111111111111";
    final var json = """
        {
          %s,
          "glamStateKey": "%s",
          "cacheDirectory": "/tmp/test-cache",
          "maxSOLPriorityFee": 0.001,
          "warnFeePayerBalance": 0.1,
          "minFeePayerBalance": 0.02,
          "minCheckStateDelay": "PT30S",
          "maxCheckStateDelay": "PT10M",
          "defaultCuBudgetMultiplier": 1.5,
          "maxTransactionRetries": 5,
          "epochService": {
            "defaultMillisPerSlot": 500,
            "slotSampleWindow": "PT30M",
            "fetchSlotSamplesDelay": "PT10M",
            "fetchEpochInfoAfterEndDelay": "PT2S"
          },
          "txMonitor": {
            "minSleepBetweenSigStatusPolling": "PT3S",
            "webSocketConfirmationTimeout": "PT45S",
            "retrySendDelay": "PT5S",
            "minBlocksRemainingToResend": 10
          },
          "accountFetcher": {
            "fetchDelay": "PT10S",
            "reactive": true
          },
          "defensivePolling": {
            "globalConfig": "PT2M",
            "glamStateAccounts": "PT4H"
          }
        }
        """.formatted(minimalRpcJson(), glamKey);

    final var properties = minimalRpcProperties("");
    properties.setProperty("glamStateKey", glamKey);
    properties.setProperty("cacheDirectory", "/tmp/test-cache");
    properties.setProperty("maxSOLPriorityFee", "0.001");
    properties.setProperty("warnFeePayerBalance", "0.1");
    properties.setProperty("minFeePayerBalance", "0.02");
    properties.setProperty("minCheckStateDelay", "PT30S");
    properties.setProperty("maxCheckStateDelay", "PT10M");
    properties.setProperty("defaultCuBudgetMultiplier", "1.5");
    properties.setProperty("maxTransactionRetries", "5");
    properties.setProperty("epochService.defaultMillisPerSlot", "500");
    properties.setProperty("epochService.slotSampleWindow", "PT30M");
    properties.setProperty("epochService.fetchSlotSamplesDelay", "PT10M");
    properties.setProperty("epochService.fetchEpochInfoAfterEndDelay", "PT2S");
    properties.setProperty("txMonitor.minSleepBetweenSigStatusPolling", "PT3S");
    properties.setProperty("txMonitor.webSocketConfirmationTimeout", "PT45S");
    properties.setProperty("txMonitor.retrySendDelay", "PT5S");
    properties.setProperty("txMonitor.minBlocksRemainingToResend", "10");
    properties.setProperty("accountFetcher.fetchDelay", "PT10S");
    properties.setProperty("accountFetcher.reactive", "true");
    properties.setProperty("defensivePolling.globalConfig", "PT2M");
    properties.setProperty("defensivePolling.glamStateAccounts", "PT4H");

    final var jsonConfig = parseJson(json);
    final var propsConfig = parseProperties(properties);

    assertEquals(jsonConfig.glamStateKey(), propsConfig.glamStateKey());
    assertEquals(jsonConfig.cacheDirectory(), propsConfig.cacheDirectory());
    assertEquals(jsonConfig.maxLamportPriorityFee(), propsConfig.maxLamportPriorityFee());
    assertEquals(jsonConfig.warnFeePayerBalance(), propsConfig.warnFeePayerBalance());
    assertEquals(jsonConfig.minFeePayerBalance(), propsConfig.minFeePayerBalance());
    assertEquals(jsonConfig.minCheckStateDelay(), propsConfig.minCheckStateDelay());
    assertEquals(jsonConfig.maxCheckStateDelay(), propsConfig.maxCheckStateDelay());
    assertEquals(jsonConfig.defaultCuBudgetMultiplier(), propsConfig.defaultCuBudgetMultiplier(), 0.001);
    assertEquals(jsonConfig.maxTransactionRetries(), propsConfig.maxTransactionRetries());

    final var jsonEpoch = jsonConfig.epochServiceConfig();
    final var propsEpoch = propsConfig.epochServiceConfig();
    assertEquals(jsonEpoch.defaultMillisPerSlot(), propsEpoch.defaultMillisPerSlot());
    assertEquals(jsonEpoch.slotSampleWindow(), propsEpoch.slotSampleWindow());
    assertEquals(jsonEpoch.fetchSlotSamplesDelay(), propsEpoch.fetchSlotSamplesDelay());
    assertEquals(jsonEpoch.fetchEpochInfoAfterEndDelay(), propsEpoch.fetchEpochInfoAfterEndDelay());

    final var jsonTx = jsonConfig.txMonitorConfig();
    final var propsTx = propsConfig.txMonitorConfig();
    assertEquals(jsonTx.minSleepBetweenSigStatusPolling(), propsTx.minSleepBetweenSigStatusPolling());
    assertEquals(jsonTx.webSocketConfirmationTimeout(), propsTx.webSocketConfirmationTimeout());
    assertEquals(jsonTx.retrySendDelay(), propsTx.retrySendDelay());
    assertEquals(jsonTx.minBlocksRemainingToResend(), propsTx.minBlocksRemainingToResend());

    final var jsonFetcher = jsonConfig.accountFetcherConfig();
    final var propsFetcher = propsConfig.accountFetcherConfig();
    assertEquals(jsonFetcher.fetchDelay(), propsFetcher.fetchDelay());
    assertEquals(jsonFetcher.reactive(), propsFetcher.reactive());

    final var jsonPolling = jsonConfig.defensivePollingConfig();
    final var propsPolling = propsConfig.defensivePollingConfig();
    assertEquals(jsonPolling.globalConfig(), propsPolling.globalConfig());
    assertEquals(jsonPolling.glamStateAccounts(), propsPolling.glamStateAccounts());
  }
}
