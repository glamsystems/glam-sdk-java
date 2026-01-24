package systems.glam.services.config;

import software.sava.services.core.config.ServiceConfigUtil;
import systems.comodal.jsoniter.FieldBufferPredicate;
import systems.comodal.jsoniter.JsonIterator;

import java.time.Duration;

import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

public record DefensivePollingConfig(Duration globalConfig) {

  static DefensivePollingConfig parseConfig(final JsonIterator ji) {
    final var parser = new DefensivePollingConfig.Parser();
    ji.testObject(parser);
    return parser.createConfig();
  }

  static DefensivePollingConfig createDefaultConfig() {
    return new DefensivePollingConfig(
        Duration.ofMinutes(30)
    );
  }

  private static final class Parser implements FieldBufferPredicate {

    private Duration globalConfig;

    private Parser() {
    }

    private DefensivePollingConfig createConfig() {
      if (globalConfig == null) {
        globalConfig = Duration.ofMinutes(30);
      }
      return new DefensivePollingConfig(globalConfig);
    }

    @Override
    public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
      if (fieldEquals("globalConfig", buf, offset, len)) {
        globalConfig = ServiceConfigUtil.parseDuration(ji);
      } else {
        throw new IllegalStateException("Unknown DefensivePollingConfiguration field " + new String(buf, offset, len));
      }
      return true;
    }
  }
}
