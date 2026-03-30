package systems.glam.services.fulfillment.config;

import org.junit.jupiter.api.Test;
import systems.comodal.jsoniter.JsonIterator;

import java.net.http.HttpClient;
import java.util.Properties;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

final class FulfillmentServiceConfigTests {

  private static final String RPC_ENDPOINT = "https://api.mainnet-beta.solana.com";
  private static final String WS_ENDPOINT = "wss://api.mainnet-beta.solana.com";

  private static FulfillmentServiceConfig parseJson(final String json) {
    try (final var httpClient = HttpClient.newHttpClient()) {
      final var parser = new FulfillmentServiceConfig.Parser(
          Executors.newVirtualThreadPerTaskExecutor(), httpClient
      );
      final var ji = JsonIterator.parse(json);
      ji.testObject(parser);
      return parser.createFulfillmentConfig();
    }
  }

  private static FulfillmentServiceConfig parseProperties(final Properties properties) {
    return parseProperties("", properties);
  }

  private static FulfillmentServiceConfig parseProperties(final String prefix, final Properties properties) {
    try (final var httpClient = HttpClient.newHttpClient()) {
      final var parser = new FulfillmentServiceConfig.Parser(
          Executors.newVirtualThreadPerTaskExecutor(), httpClient
      );
      parser.parseProperties(prefix, properties);
      return parser.createFulfillmentConfig();
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

    assertTrue(config.softRedeem());
    assertNotNull(config.delegateServiceConfig());
  }

  @Test
  void testPropertiesDefaults() {
    final var properties = minimalRpcProperties("");
    final var config = parseProperties(properties);

    assertTrue(config.softRedeem());
    assertNotNull(config.delegateServiceConfig());
  }

  @Test
  void testJsonSoftRedeemFalse() {
    final var json = """
        {
          %s,
          "softRedeem": false
        }
        """.formatted(minimalRpcJson());
    final var config = parseJson(json);

    assertFalse(config.softRedeem());
  }

  @Test
  void testPropertiesSoftRedeemFalse() {
    final var properties = minimalRpcProperties("");
    properties.setProperty("softRedeem", "false");
    final var config = parseProperties(properties);

    assertFalse(config.softRedeem());
  }

  @Test
  void testJsonSoftRedeemTrue() {
    final var json = """
        {
          %s,
          "softRedeem": true
        }
        """.formatted(minimalRpcJson());
    final var config = parseJson(json);

    assertTrue(config.softRedeem());
  }

  @Test
  void testPropertiesSoftRedeemTrue() {
    final var properties = minimalRpcProperties("");
    properties.setProperty("softRedeem", "true");
    final var config = parseProperties(properties);

    assertTrue(config.softRedeem());
  }

  @Test
  void testPropertiesWithPrefix() {
    final var properties = minimalRpcProperties("svc.");
    properties.setProperty("svc.softRedeem", "false");
    final var config = parseProperties("svc", properties);

    assertFalse(config.softRedeem());
    assertNotNull(config.delegateServiceConfig());
  }

  @Test
  void testJsonAndPropertiesProduceSameConfig() {
    final var json = """
        {
          %s,
          "softRedeem": false
        }
        """.formatted(minimalRpcJson());

    final var properties = minimalRpcProperties("");
    properties.setProperty("softRedeem", "false");

    final var jsonConfig = parseJson(json);
    final var propsConfig = parseProperties(properties);

    assertEquals(jsonConfig.softRedeem(), propsConfig.softRedeem());
    assertEquals(jsonConfig.delegateServiceConfig().glamStateKey(), propsConfig.delegateServiceConfig().glamStateKey());
  }
}
