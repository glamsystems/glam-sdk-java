package systems.glam.services.config;

import software.sava.services.core.config.ServiceConfigUtil;
import systems.comodal.jsoniter.FieldBufferPredicate;
import systems.comodal.jsoniter.JsonIterator;

import java.time.Duration;

import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

public record DefensivePollingConfig(Duration globalConfig,
                                     Duration glamStateAccounts,
                                     Duration integTables,
                                     Duration stakePools) {

  static DefensivePollingConfig parseConfig(final JsonIterator ji) {
    final var parser = new DefensivePollingConfig.Parser();
    ji.testObject(parser);
    return parser.createConfig();
  }

  static DefensivePollingConfig createDefaultConfig() {
    return new DefensivePollingConfig(
        Duration.ofMinutes(30),
        Duration.ofHours(8),
        Duration.ofHours(4),
        Duration.ofHours(12)
    );
  }

  private static final class Parser implements FieldBufferPredicate {

    private Duration globalConfig;
    private Duration glamStateAccounts;
    private Duration integTables;
    private Duration stakePools;

    private Parser() {
    }

    private DefensivePollingConfig createConfig() {
      if (globalConfig == null) {
        globalConfig = Duration.ofMinutes(30);
      }
      if (glamStateAccounts == null) {
        glamStateAccounts = Duration.ofHours(8);
      }
      if (integTables == null) {
        integTables = Duration.ofHours(4);
      }
      if (stakePools == null) {
        stakePools = Duration.ofHours(12);
      }
      return new DefensivePollingConfig(globalConfig, glamStateAccounts, integTables, stakePools);
    }

    @Override
    public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
      if (fieldEquals("globalConfig", buf, offset, len)) {
        globalConfig = ServiceConfigUtil.parseDuration(ji);
      } else if (fieldEquals("glamStateAccounts", buf, offset, len)) {
        glamStateAccounts = ServiceConfigUtil.parseDuration(ji);
      } else if (fieldEquals("integTables", buf, offset, len)) {
        integTables = ServiceConfigUtil.parseDuration(ji);
      } else if (fieldEquals("stakePools", buf, offset, len)) {
        stakePools = ServiceConfigUtil.parseDuration(ji);
      } else {
        throw new IllegalStateException("Unknown DefensivePollingConfiguration field " + new String(buf, offset, len));
      }
      return true;
    }
  }
}
