package systems.glam.services.config;

import software.sava.services.core.config.ServiceConfigUtil;
import systems.comodal.jsoniter.FieldBufferPredicate;
import systems.comodal.jsoniter.JsonIterator;

import java.time.Duration;

import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

public record AccountFetcherConfig(Duration fetchDelay, boolean reactive) {

  public static AccountFetcherConfig createDefault() {
    return new AccountFetcherConfig(Duration.ofSeconds(5), false);
  }

  public static AccountFetcherConfig parseConfig(final JsonIterator ji) {
    final var parser = new AccountFetcherConfig.Parser();
    ji.testObject(parser);
    return parser.createConfig();
  }

  private static final class Parser implements FieldBufferPredicate {

    private Duration fetchDelay;
    private boolean reactive;

    private Parser() {
    }

    private AccountFetcherConfig createConfig() {
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
