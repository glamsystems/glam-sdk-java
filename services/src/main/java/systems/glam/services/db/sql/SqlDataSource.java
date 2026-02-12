package systems.glam.services.db.sql;

import com.zaxxer.hikari.HikariDataSource;
import systems.glam.services.db.DataSourceHelper;
import systems.glam.services.db.DatasourceConfig;
import systems.glam.services.db.DatasourceFormat;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

public interface SqlDataSource extends DataSource, DataSourceHelper {

  static String createInsertStatement(final String prefix,
                                      final String suffixFormat,
                                      final int skip,
                                      final String... columns) {
    return Arrays.stream(columns).collect(Collectors.joining(",",
        prefix,
        String.format(suffixFormat, ",?".repeat(columns.length - skip))
    ));
  }

  static int batchExecutionCount(final int[] result) throws SQLException {
    int sum = 0;
    for (int i = 0; i < result.length; ++i) {
      final int n = result[i];
      if (n == Statement.SUCCESS_NO_INFO) {
        sum += 1;
      } else if (n == Statement.EXECUTE_FAILED) {
        throw new SQLException("Failed to execute statement " + i);
      } else {
        sum += n;
      }
    }
    return sum;
  }

  static SqlDataSource createDataSource(final DatasourceConfig config,
                                        final DataSource dataSource,
                                        final DataSourceHelper dataSourceHelper) {
    final var hikariDataSource = new HikariDataSource();
    hikariDataSource.setDataSource(dataSource);
    // CONNECTION_TIMEOUT = SECONDS.toMillis(30);
    // VALIDATION_TIMEOUT = SECONDS.toMillis(5);
    hikariDataSource.setConnectionTimeout(SECONDS.toMillis(config.connectTimeout()));
    // IDLE_TIMEOUT = MINUTES.toMillis(10);
    hikariDataSource.setIdleTimeout(MINUTES.toMillis(5));
    // DEFAULT_KEEPALIVE_TIME = 0L;
    hikariDataSource.setKeepaliveTime(SECONDS.toMillis(15));
    // MAX_LIFETIME = MINUTES.toMillis(30);
    hikariDataSource.setMaxLifetime(MINUTES.toMillis(15));
    // DEFAULT_POOL_SIZE = 10;
    hikariDataSource.setMaximumPoolSize(Runtime.getRuntime().availableProcessors() << 2);
    return new DelegatedSqlDataSource(hikariDataSource, dataSourceHelper);
  }

  interface Factory {

    DatasourceFormat format();

    SqlDataSource createDataSource(final String appName, final DatasourceConfig databaseConfig);
  }

  static SqlDataSource createDataSource(final String appName, final DatasourceConfig config) {
    final var format = config.format();
    return ServiceLoader.load(SqlDataSource.Factory.class).stream()
        .map(ServiceLoader.Provider::get)
        .filter(provider -> provider.format().equals(format))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException(String.format("Failed to find a %s datasource factory.", format)))
        .createDataSource(appName, config);
  }

  default <R> StmtExecutor<R> createMultiStmtExecutor(final ConnectionExecutor<R> connectionExecutor) {
    return new ComposedMultiStmtExecutor<>(this, 5, SECONDS, connectionExecutor);
  }

  default <R> R executeMultiStmt(final ConnectionExecutor<R> connectionExecutor) throws InterruptedException {
    return this.createMultiStmtExecutor(connectionExecutor).execute();
  }

  default <R> StmtExecutor<R> createPreparedStatementExecutor(final String statement,
                                                              final PreparedStmtExecutor<R> preparedStmtExecutor) {
    return new ComposedPreparedStmtExecutor<>(this, 5, SECONDS, statement, Statement.NO_GENERATED_KEYS, preparedStmtExecutor);
  }

  default <R> R executePreparedStatement(final String statement,
                                         final PreparedStmtExecutor<R> preparedStmtExecutor) throws InterruptedException {
    return this.createPreparedStatementExecutor(statement, preparedStmtExecutor).execute();
  }

  default <R> StmtExecutor<R> createPreparedStatementReturningKeysExecutor(final String statement,
                                                                           final PreparedStmtExecutor<R> preparedStmtExecutor) {
    return new ComposedPreparedStmtExecutor<>(this, 5, SECONDS, statement, Statement.RETURN_GENERATED_KEYS, preparedStmtExecutor);
  }

  default <R> R executePreparedStatementReturningKeys(final String statement,
                                                      final PreparedStmtExecutor<R> preparedStmtExecutor) throws InterruptedException {
    return this.createPreparedStatementReturningKeysExecutor(statement, preparedStmtExecutor).execute();
  }
}
