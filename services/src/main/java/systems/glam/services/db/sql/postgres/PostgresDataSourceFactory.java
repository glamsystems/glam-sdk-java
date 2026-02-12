package systems.glam.services.db.sql.postgres;

import org.postgresql.ds.PGSimpleDataSource;
import systems.glam.services.db.DatasourceConfig;
import systems.glam.services.db.DatasourceFormat;
import systems.glam.services.db.sql.SqlDataSource;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_TIME;

public final class PostgresDataSourceFactory implements SqlDataSource.Factory {

  private static final int DEFAULT_WRITE_BATCH_SIZE = 128;

  private static final DateTimeFormatter Z_DATE_TIME_FORMATTER_LENIENT = new DateTimeFormatterBuilder()
      .parseCaseInsensitive()
      .append(ISO_LOCAL_DATE)
      .appendLiteral(' ')
      .append(ISO_LOCAL_TIME)
      .parseLenient()
      .appendOffsetId()
      .toFormatter();

  public PostgresDataSourceFactory() {
  }

  @Override
  public DatasourceFormat format() {
    return DatasourceFormat.POSTGRES;
  }

  @Override
  public SqlDataSource createDataSource(final String appName, final DatasourceConfig config) {
    final var pgDataSource = new PGSimpleDataSource();
    pgDataSource.setServerNames(new String[]{config.uri()});
    pgDataSource.setPortNumbers(new int[]{config.port()});
    pgDataSource.setUser(config.user());

    if (config.password() != null && !config.password().isBlank()) {
      pgDataSource.setPassword(config.password());
    } else {
      final var passwordFilePath = config.passwordFilePath();
      if (passwordFilePath != null && !passwordFilePath.isBlank()) {
        try {
          pgDataSource.setPassword(new String(Files.readAllBytes(Path.of(passwordFilePath))));
        } catch (final IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    }

    final var sslMode = config.sslMode();
    if (sslMode != null && !sslMode.isBlank()) {
      pgDataSource.setSsl(true);
      pgDataSource.setSslMode(sslMode);
    }

    final var sslCertFilePath = config.sslCertFilePath();
    if (sslCertFilePath != null && !sslCertFilePath.isBlank()) {
      pgDataSource.setSsl(true);
      final var sslCertPath = Path.of(sslCertFilePath);
      final var pathString = sslCertPath.toAbsolutePath().toString();
      if (Files.exists(sslCertPath)) {
        pgDataSource.setSslcert(pathString);
      } else {
        throw new UncheckedIOException(new FileNotFoundException(pathString));
      }
    }

    pgDataSource.setDatabaseName(config.databaseName());
    pgDataSource.setApplicationName(appName);
    pgDataSource.setConnectTimeout(config.connectTimeout());
    pgDataSource.setReWriteBatchedInserts(true);

    final var helperBuilder = config.dataSourceHelperBuilder();
    helperBuilder.defaultWriteBatchSize(DEFAULT_WRITE_BATCH_SIZE);
    helperBuilder.defaultZDateTimeFormatter(Z_DATE_TIME_FORMATTER_LENIENT);
    final var helper = helperBuilder.createHelper();

    return SqlDataSource.createDataSource(config, pgDataSource, helper);
  }
}
