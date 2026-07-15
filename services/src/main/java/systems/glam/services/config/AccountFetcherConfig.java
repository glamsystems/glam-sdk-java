package systems.glam.services.config;

import software.sava.services.core.config.PropertiesParser;
import software.sava.services.core.config.ServiceConfigUtil;
import systems.comodal.jsoniter.FieldBufferPredicate;
import systems.comodal.jsoniter.JsonIterator;

import java.time.Duration;
import java.util.Properties;
import java.util.function.Supplier;

import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

public record AccountFetcherConfig(Duration fetchDelay, boolean reactive) {

  public static AccountFetcherConfig createDefault() {
    return new AccountFetcherConfig(Duration.ofSeconds(5), false);
  }

  public static AccountFetcherConfig parseConfig(final Properties properties) {
    return parseConfig("", properties);
  }

  public static AccountFetcherConfig parseConfig(final String prefix, final Properties properties) {
    final var parser = new AccountFetcherConfig.Parser();
    parser.parseProperties(prefix, properties);
    return parser.get();
  }

  public static AccountFetcherConfig parseConfig(final JsonIterator ji) {
    return ji.parseObject(new AccountFetcherConfig.Parser());
  }

  private static final class Parser extends PropertiesParser implements FieldBufferPredicate, Supplier<AccountFetcherConfig> {

    private Duration fetchDelay;
    private boolean reactive;

    private Parser() {
    }

    private void parseProperties(final String prefix, final Properties properties) {
      final var p = propertyPrefix(prefix);
      final var fetchDelay = parseDuration(properties, p, "fetchDelay");
      if (fetchDelay != null) {
        this.fetchDelay = fetchDelay;
      }
      this.reactive = parseBoolean(properties, p, "reactive", this.reactive);
    }

    @Override
    public AccountFetcherConfig get() {
      return new AccountFetcherConfig(
          fetchDelay == null ? Duration.ofSeconds(5) : fetchDelay,
          reactive
      );
    }

    @Override
    public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
      if (fieldEquals("fetchDelay", buf, offset, len)) {
        fetchDelay = ServiceConfigUtil.parseDuration(ji);
      } else if (fieldEquals("reactive", buf, offset, len)) {
        reactive = ji.readBoolean();
      } else {
        throw new IllegalStateException("Unknown AccountFetcherConfig field " + new String(buf, offset, len));
      }
      return true;
    }
  }
}
