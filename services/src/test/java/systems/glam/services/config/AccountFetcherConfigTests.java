package systems.glam.services.config;

import org.junit.jupiter.api.Test;
import systems.comodal.jsoniter.JsonIterator;

import java.time.Duration;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class AccountFetcherConfigTests {

  @Test
  void testDefaultConfig() {
    final var config = AccountFetcherConfig.createDefault();
    assertEquals(Duration.ofSeconds(5), config.fetchDelay());
    assertFalse(config.reactive());
  }

  @Test
  void testParseJsonConfig() {
    final String json = """
        {
          "fetchDelay": "PT10S",
          "reactive": true
        }
        """;
    final var config = AccountFetcherConfig.parseConfig(JsonIterator.parse(json));
    assertEquals(Duration.ofSeconds(10), config.fetchDelay());
    assertTrue(config.reactive());
  }

  @Test
  void testParsePartialJsonConfig() {
    final String json = """
        {
          "fetchDelay": "PT30S"
        }
        """;
    final var config = AccountFetcherConfig.parseConfig(JsonIterator.parse(json));
    assertEquals(Duration.ofSeconds(30), config.fetchDelay());
    assertFalse(config.reactive());
  }

  @Test
  void testParseEmptyJsonConfig() {
    final String json = "{}";
    final var config = AccountFetcherConfig.parseConfig(JsonIterator.parse(json));
    assertEquals(Duration.ofSeconds(5), config.fetchDelay());
    assertFalse(config.reactive());
  }

  @Test
  void testParsePropertiesConfig() {
    final var properties = new Properties();
    properties.setProperty("fetchDelay", "PT10S");
    properties.setProperty("reactive", "true");
    final var config = AccountFetcherConfig.parseConfig(properties);
    assertEquals(Duration.ofSeconds(10), config.fetchDelay());
    assertTrue(config.reactive());
  }

  @Test
  void testParsePropertiesWithPrefixConfig() {
    final var properties = new Properties();
    properties.setProperty("account.fetchDelay", "PT15S");
    properties.setProperty("account.reactive", "true");
    final var config = AccountFetcherConfig.parseConfig("account", properties);
    assertEquals(Duration.ofSeconds(15), config.fetchDelay());
    assertTrue(config.reactive());
  }

  @Test
  void testParsePartialPropertiesConfig() {
    final var properties = new Properties();
    properties.setProperty("fetchDelay", "PT30S");
    final var config = AccountFetcherConfig.parseConfig(properties);
    assertEquals(Duration.ofSeconds(30), config.fetchDelay());
    assertFalse(config.reactive());
  }

  @Test
  void testParseEmptyPropertiesConfig() {
    final var properties = new Properties();
    final var config = AccountFetcherConfig.parseConfig(properties);
    assertEquals(Duration.ofSeconds(5), config.fetchDelay());
    assertFalse(config.reactive());
  }
}
