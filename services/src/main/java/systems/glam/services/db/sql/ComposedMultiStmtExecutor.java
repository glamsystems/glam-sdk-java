package systems.glam.services.db.sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

final class ComposedMultiStmtExecutor<R> extends RootStmtExecutor<R> {

  private final ConnectionExecutor<R> connectionExecutor;

  ComposedMultiStmtExecutor(final SqlDataSource datasource,
                            final int maxRetrySleep, final TimeUnit timeUnit,
                            final ConnectionExecutor<R> connectionExecutor) {
    super(datasource, maxRetrySleep, timeUnit);
    this.connectionExecutor = connectionExecutor;
  }

  @Override
  protected R execute(final Connection connection) throws SQLException {
    connection.setAutoCommit(false);
    final R result = connectionExecutor.execute(connection);
    connection.commit();
    return result;
  }
}
