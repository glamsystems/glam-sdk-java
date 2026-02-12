package systems.glam.services.db.sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import static java.lang.System.Logger.Level.ERROR;

public abstract class RootStmtExecutor<R> implements StmtExecutor<R> {

  private static final System.Logger log = System.getLogger(RootStmtExecutor.class.getName());

  protected final SqlDataSource datasource;
  protected final int maxRetrySleep;
  protected final TimeUnit timeUnit;

  protected RootStmtExecutor(final SqlDataSource datasource,
                             final int maxRetrySleep,
                             final TimeUnit timeUnit) {
    this.datasource = datasource;
    this.maxRetrySleep = maxRetrySleep;
    this.timeUnit = timeUnit;
  }

  protected abstract R execute(final Connection connection) throws SQLException;

  @Override
  public final R execute() throws InterruptedException {
    for (int failureCount = 0; ; ) {
      try (final var connection = datasource.getConnection()) {
        return execute(connection);
      } catch (final SQLException e) {
        log.log(ERROR, "Failed {0} times to execute: [ state => {1}, cause => {2}, message => {3} ]",
            ++failureCount, e.getSQLState(), e.getCause(), e.getMessage()
        );
      }
      timeUnit.sleep(Math.min(failureCount, maxRetrySleep));
    }
  }
}
