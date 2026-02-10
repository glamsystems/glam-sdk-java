package systems.glam.services.config;

import software.sava.services.core.config.ServiceConfigUtil;
import systems.comodal.jsoniter.FieldBufferPredicate;
import systems.comodal.jsoniter.JsonIterator;

import java.time.Duration;

import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

public record DefensivePollingConfig(Duration globalConfig,
                                     Duration glamStateAccounts,
                                     Duration integTables,
                                     Duration stakePools,
                                     Duration kaminoScope) {

  private static final Duration EIGHT_HOURS = Duration.ofHours(8);

  public static DefensivePollingConfig parseConfig(final JsonIterator ji) {
    final var parser = new DefensivePollingConfig.Parser();
    ji.testObject(parser);
    return parser.createConfig();
  }

  public static DefensivePollingConfig createDefaultConfig() {
    return new DefensivePollingConfig(
        Duration.ofMinutes(1),
        EIGHT_HOURS,
        Duration.ofHours(4),
        Duration.ofHours(12),
        EIGHT_HOURS
    );
  }

  private static final class Parser implements FieldBufferPredicate {

    private Duration globalConfig;
    private Duration glamStateAccounts;
    private Duration integTables;
    private Duration stakePools;
    private Duration kaminoScope;

    private Parser() {
    }

    private DefensivePollingConfig createConfig() {
      if (globalConfig == null) {
        globalConfig = Duration.ofMinutes(1);
      }
      if (glamStateAccounts == null) {
        glamStateAccounts = EIGHT_HOURS;
      }
      if (integTables == null) {
        integTables = Duration.ofHours(4);
      }
      if (stakePools == null) {
        stakePools = Duration.ofHours(12);
      }
      if (kaminoScope == null) {
        kaminoScope = EIGHT_HOURS;
      }
      return new DefensivePollingConfig(globalConfig, glamStateAccounts, integTables, stakePools, kaminoScope);
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
      } else if (fieldEquals("kaminoScope", buf, offset, len)) {
        kaminoScope = ServiceConfigUtil.parseDuration(ji);
      } else {
        throw new IllegalStateException("Unknown DefensivePollingConfiguration field " + new String(buf, offset, len));
      }
      return true;
    }
  }
}
