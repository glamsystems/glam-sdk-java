package systems.glam.services.config;

import org.junit.jupiter.api.Test;
import systems.comodal.jsoniter.JsonIterator;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefensivePollingConfigTest {

  @Test
  void testDefaultConfig() {
    final var config = DefensivePollingConfig.createDefaultConfig();
    assertEquals(Duration.ofMinutes(30), config.globalConfig());
    assertEquals(Duration.ofHours(8), config.glamStateAccounts());
    assertEquals(Duration.ofHours(4), config.integTables());
    assertEquals(Duration.ofHours(12), config.stakePools());
  }

  @Test
  void testParseConfig() {
    final String json = """
        {
          "globalConfig": "PT1H",
          "glamStateAccounts": "PT2H",
          "integTables": "PT3H",
          "stakePools": "PT4H"
        }
        """;
    final var config = DefensivePollingConfig.parseConfig(JsonIterator.parse(json));
    assertEquals(Duration.ofHours(1), config.globalConfig());
    assertEquals(Duration.ofHours(2), config.glamStateAccounts());
    assertEquals(Duration.ofHours(3), config.integTables());
    assertEquals(Duration.ofHours(4), config.stakePools());
  }

  @Test
  void testParsePartialConfig() {
    final String json = """
        {
          "stakePools": "PT5H"
        }
        """;
    final var config = DefensivePollingConfig.parseConfig(JsonIterator.parse(json));
    assertEquals(Duration.ofMinutes(30), config.globalConfig());
    assertEquals(Duration.ofHours(8), config.glamStateAccounts());
    assertEquals(Duration.ofHours(4), config.integTables());
    assertEquals(Duration.ofHours(5), config.stakePools());
  }
}
