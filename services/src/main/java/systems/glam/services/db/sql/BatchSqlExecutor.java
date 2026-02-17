package systems.glam.services.db.sql;

import software.sava.services.core.remote.call.Backoff;

import java.time.Duration;

public interface BatchSqlExecutor<T> extends Runnable {

  static <T> BatchSqlExecutor<T> create(final Class<T> componentType,
                                        final SqlDataSource datasource,
                                        final String statement,
                                        final int batchSize,
                                        final StatementPreparer<T> statementPreparer,
                                        final Duration batchDelay,
                                        final Backoff backoff) {
    return new BatchSqlExecutorImpl<>(
        componentType,
        datasource, statement, batchSize, statementPreparer,
        batchDelay, backoff
    );
  }

  void queue(final T item);
}
