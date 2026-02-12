package systems.glam.services.db.sql;

import systems.glam.services.db.DataSourceHelper;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

final class DelegatedSqlDataSource implements SqlDataSource {

  private final DataSource dataSource;
  private final DataSourceHelper dataSourceHelper;

  DelegatedSqlDataSource(final DataSource dataSource, final DataSourceHelper dataSourceHelper) {
    this.dataSource = dataSource;
    this.dataSourceHelper = dataSourceHelper;
  }

  @Override
  public int writeBatchSize() {
    return dataSourceHelper.writeBatchSize();
  }

  @Override
  public DateTimeFormatter zDateTimeFormatter() {
    return dataSourceHelper.zDateTimeFormatter();
  }

  @Override
  public Instant parseDatabaseTimestampZ(final String timestamp) {
    return dataSourceHelper.parseDatabaseTimestampZ(timestamp);
  }

  @Override
  public Connection getConnection() throws SQLException {
    return dataSource.getConnection();
  }

  @Override
  public Connection getConnection(final String username, final String password) throws SQLException {
    return dataSource.getConnection(username, password);
  }

  @Override
  public PrintWriter getLogWriter() throws SQLException {
    return dataSource.getLogWriter();
  }

  @Override
  public void setLogWriter(final PrintWriter out) throws SQLException {
    dataSource.setLogWriter(out);
  }

  @Override
  public void setLoginTimeout(final int seconds) throws SQLException {
    dataSource.setLoginTimeout(seconds);
  }

  @Override
  public int getLoginTimeout() throws SQLException {
    return dataSource.getLoginTimeout();
  }

  @Override
  public Logger getParentLogger() throws SQLFeatureNotSupportedException {
    return dataSource.getParentLogger();
  }

  @Override
  public ConnectionBuilder createConnectionBuilder() throws SQLException {
    return dataSource.createConnectionBuilder();
  }

  @Override
  public ShardingKeyBuilder createShardingKeyBuilder() throws SQLException {
    return dataSource.createShardingKeyBuilder();
  }

  @Override
  public <T> T unwrap(final Class<T> iface) throws SQLException {
    return dataSource.unwrap(iface);
  }

  @Override
  public boolean isWrapperFor(final Class<?> iface) throws SQLException {
    return dataSource.isWrapperFor(iface);
  }
}
