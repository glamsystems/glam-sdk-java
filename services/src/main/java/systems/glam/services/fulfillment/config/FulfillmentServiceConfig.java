package systems.glam.services.fulfillment.config;

import systems.comodal.jsoniter.JsonIterator;
import systems.glam.services.config.BaseDelegateServiceConfig;
import systems.glam.services.config.DelegateServiceConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

public record FulfillmentServiceConfig(DelegateServiceConfig delegateServiceConfig, boolean softRedeem) {

  public static FulfillmentServiceConfig loadConfig(final Path serviceConfigFile,
                                                    final ExecutorService executorService,
                                                    final HttpClient httpClient) {
    try (final var ji = JsonIterator.parse(Files.readAllBytes(serviceConfigFile))) {
      final var parser = new Parser(executorService, httpClient);
      ji.testObject(parser);
      return parser.createFulfillmentConfig();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static class Parser extends BaseDelegateServiceConfig.ConfigParser {

    private boolean softRedeem = true;

    protected Parser(final ExecutorService taskExecutor, final HttpClient httpClient) {
      super(taskExecutor, httpClient);
    }

    public FulfillmentServiceConfig createFulfillmentConfig() {
      final var delegateServiceConfig = createBaseConfig();

      return new FulfillmentServiceConfig(delegateServiceConfig, softRedeem);
    }

    @Override
    public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
      if (fieldEquals("softRedeem", buf, offset, len)) {
        softRedeem = ji.readBoolean();
      } else {
        return super.test(buf, offset, len, ji);
      }
      return true;
    }
  }
}
