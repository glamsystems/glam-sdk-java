package systems.glam.services.pricing.config;

import systems.comodal.jsoniter.JsonIterator;
import systems.glam.services.config.BaseDelegateServiceConfig;
import systems.glam.services.config.DelegateServiceConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

public record PriceVaultsServiceConfig(DelegateServiceConfig delegateServiceConfig,
                                       TimeUnit valueVaultTimeUnit,
                                       int valueVaultMaxRetries) {

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

    private TimeUnit valueVaultTimeUnit;
    private int valueVaultMaxRetries = 5;

    protected Parser(final ExecutorService taskExecutor, final HttpClient httpClient) {
      super(taskExecutor, httpClient);
    }

    public PriceVaultsServiceConfig createServiceConfig() {
      final var delegateServiceConfig = createBaseConfig();

      return new PriceVaultsServiceConfig(
          delegateServiceConfig,
          Objects.requireNonNullElse(valueVaultTimeUnit, TimeUnit.MINUTES),
          valueVaultMaxRetries
      );
    }

    @Override
    public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
      if (fieldEquals("valueVaultTimeUnit", buf, offset, len)) {
        valueVaultTimeUnit = TimeUnit.valueOf(ji.readString().toUpperCase(Locale.ENGLISH));
      } else if (fieldEquals("valueVaultMaxRetries", buf, offset, len)) {
        valueVaultMaxRetries = ji.readInt();
      } else {
        return super.test(buf, offset, len, ji);
      }
      return true;
    }
  }
}
