package systems.glam.services.config;

import org.junit.jupiter.api.Test;
import systems.comodal.jsoniter.JsonIterator;

import java.time.Duration;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DefensivePollingConfigTests {

  @Test
  void testDefaultConfig() {
    final var config = DefensivePollingConfig.createDefaultConfig();
    assertEquals(Duration.ofMinutes(1), config.globalConfig());
    assertEquals(Duration.ofHours(8), config.glamStateAccounts());
    assertEquals(Duration.ofHours(4), config.integTables());
    assertEquals(Duration.ofHours(12), config.stakePools());
    assertEquals(Duration.ofHours(8), config.kaminoScope());
  }

  @Test
  void testParseConfig() {
    final String json = """
        {
          "globalConfig": "PT1H",
          "glamStateAccounts": "PT2H",
          "integTables": "PT3H",
          "stakePools": "PT4H",
          "kaminoScope": "PT5M"
        }
        """;
    final var config = DefensivePollingConfig.parseConfig(JsonIterator.parse(json));
    assertEquals(Duration.ofHours(1), config.globalConfig());
    assertEquals(Duration.ofHours(2), config.glamStateAccounts());
    assertEquals(Duration.ofHours(3), config.integTables());
    assertEquals(Duration.ofHours(4), config.stakePools());
    assertEquals(Duration.ofMinutes(5), config.kaminoScope());
  }

  @Test
  void testParsePartialConfig() {
    final String json = """
        {
          "stakePools": "PT5H"
        }
        """;
    final var config = DefensivePollingConfig.parseConfig(JsonIterator.parse(json));
    assertEquals(Duration.ofMinutes(1), config.globalConfig());
    assertEquals(Duration.ofHours(8), config.glamStateAccounts());
    assertEquals(Duration.ofHours(4), config.integTables());
    assertEquals(Duration.ofHours(5), config.stakePools());
    assertEquals(Duration.ofHours(8), config.kaminoScope());
  }

  @Test
  void testParseJsonAllFields() {
    final var json = """
        {
          "globalConfig": "PT1H",
          "glamStateAccounts": "PT2H",
          "integTables": "PT3H",
          "stakePools": "PT4H",
          "kaminoScope": "PT5M"
        }
        """;
    final var config = DefensivePollingConfig.parseConfig(JsonIterator.parse(json));
    assertEquals(Duration.ofHours(1), config.globalConfig());
    assertEquals(Duration.ofHours(2), config.glamStateAccounts());
    assertEquals(Duration.ofHours(3), config.integTables());
    assertEquals(Duration.ofHours(4), config.stakePools());
    assertEquals(Duration.ofMinutes(5), config.kaminoScope());
  }

  @Test
  void testParseJsonDefaults() {
    final var json = "{}";
    final var config = DefensivePollingConfig.parseConfig(JsonIterator.parse(json));
    assertEquals(Duration.ofMinutes(1), config.globalConfig());
    assertEquals(Duration.ofHours(8), config.glamStateAccounts());
    assertEquals(Duration.ofHours(4), config.integTables());
    assertEquals(Duration.ofHours(12), config.stakePools());
    assertEquals(Duration.ofHours(8), config.kaminoScope());
  }

  @Test
  void testParseJsonPartial() {
    final var json = """
        {
          "stakePools": "PT5H"
        }
        """;
    final var config = DefensivePollingConfig.parseConfig(JsonIterator.parse(json));
    assertEquals(Duration.ofMinutes(1), config.globalConfig());
    assertEquals(Duration.ofHours(8), config.glamStateAccounts());
    assertEquals(Duration.ofHours(4), config.integTables());
    assertEquals(Duration.ofHours(5), config.stakePools());
    assertEquals(Duration.ofHours(8), config.kaminoScope());
  }

  @Test
  void testCreateDefault() {
    final var config = DefensivePollingConfig.createDefaultConfig();
    assertEquals(Duration.ofMinutes(1), config.globalConfig());
    assertEquals(Duration.ofHours(8), config.glamStateAccounts());
    assertEquals(Duration.ofHours(4), config.integTables());
    assertEquals(Duration.ofHours(12), config.stakePools());
    assertEquals(Duration.ofHours(8), config.kaminoScope());
  }

  // Properties Tests

  @Test
  void testParsePropertiesAllFields() {
    final var properties = new Properties();
    properties.setProperty("globalConfig", "PT30M");
    properties.setProperty("glamStateAccounts", "PT6H");
    properties.setProperty("integTables", "PT2H");
    properties.setProperty("stakePools", "PT10H");
    properties.setProperty("kaminoScope", "PT4H");

    final var config = DefensivePollingConfig.parseConfig(properties);
    assertEquals(Duration.ofMinutes(30), config.globalConfig());
    assertEquals(Duration.ofHours(6), config.glamStateAccounts());
    assertEquals(Duration.ofHours(2), config.integTables());
    assertEquals(Duration.ofHours(10), config.stakePools());
    assertEquals(Duration.ofHours(4), config.kaminoScope());
  }

  @Test
  void testParsePropertiesDefaults() {
    final var config = DefensivePollingConfig.parseConfig(new Properties());
    assertEquals(Duration.ofMinutes(1), config.globalConfig());
    assertEquals(Duration.ofHours(8), config.glamStateAccounts());
    assertEquals(Duration.ofHours(4), config.integTables());
    assertEquals(Duration.ofHours(12), config.stakePools());
    assertEquals(Duration.ofHours(8), config.kaminoScope());
  }

  @Test
  void testParsePropertiesWithPrefix() {
    final var properties = new Properties();
    properties.setProperty("polling.globalConfig", "PT5M");
    properties.setProperty("polling.glamStateAccounts", "PT3H");
    properties.setProperty("polling.integTables", "PT1H");
    properties.setProperty("polling.stakePools", "PT6H");
    properties.setProperty("polling.kaminoScope", "PT2H");

    final var config = DefensivePollingConfig.parseConfig("polling", properties);
    assertEquals(Duration.ofMinutes(5), config.globalConfig());
    assertEquals(Duration.ofHours(3), config.glamStateAccounts());
    assertEquals(Duration.ofHours(1), config.integTables());
    assertEquals(Duration.ofHours(6), config.stakePools());
    assertEquals(Duration.ofHours(2), config.kaminoScope());
  }

  @Test
  void testParsePropertiesWithDottedPrefix() {
    final var properties = new Properties();
    properties.setProperty("defensive.polling.globalConfig", "PT2M");
    properties.setProperty("defensive.polling.glamStateAccounts", "PT1H");
    properties.setProperty("defensive.polling.integTables", "PT30M");
    properties.setProperty("defensive.polling.stakePools", "PT3H");
    properties.setProperty("defensive.polling.kaminoScope", "PT45M");

    final var config = DefensivePollingConfig.parseConfig("defensive.polling.", properties);
    assertEquals(Duration.ofMinutes(2), config.globalConfig());
    assertEquals(Duration.ofHours(1), config.glamStateAccounts());
    assertEquals(Duration.ofMinutes(30), config.integTables());
    assertEquals(Duration.ofHours(3), config.stakePools());
    assertEquals(Duration.ofMinutes(45), config.kaminoScope());
  }

  @Test
  void testParsePropertiesPartial() {
    final var properties = new Properties();
    properties.setProperty("stakePools", "PT5H");

    final var config = DefensivePollingConfig.parseConfig(properties);
    assertEquals(Duration.ofMinutes(1), config.globalConfig());
    assertEquals(Duration.ofHours(8), config.glamStateAccounts());
    assertEquals(Duration.ofHours(4), config.integTables());
    assertEquals(Duration.ofHours(5), config.stakePools());
    assertEquals(Duration.ofHours(8), config.kaminoScope());
  }
}
