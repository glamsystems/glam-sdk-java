package systems.glam.services.pricing.config;

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

public record PriceVaultsServiceConfig(DelegateServiceConfig delegateServiceConfig) {

  public static PriceVaultsServiceConfig loadConfig(final Path serviceConfigFile,
                                                    final ExecutorService executorService,
                                                    final HttpClient httpClient) {
    try (final var ji = JsonIterator.parse(Files.readAllBytes(serviceConfigFile))) {
      final var parser = new PriceVaultsServiceConfig.Parser(executorService, httpClient);
      ji.testObject(parser);
      return parser.createServiceConfig();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static class Parser extends BaseDelegateServiceConfig.ConfigParser {


    protected Parser(final ExecutorService taskExecutor, final HttpClient httpClient) {
      super(taskExecutor, httpClient);
    }

    public PriceVaultsServiceConfig createServiceConfig() {
      final var delegateServiceConfig = createBaseConfig();

      return new PriceVaultsServiceConfig(
          delegateServiceConfig
      );
    }

    @Override
    public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
      if (fieldEquals("", buf, offset, len)) {
      } else {
        return super.test(buf, offset, len, ji);
      }
      return true;
    }
  }
}
