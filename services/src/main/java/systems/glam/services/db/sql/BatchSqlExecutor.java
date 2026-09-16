package systems.glam.services.db.sql;

import software.sava.services.core.remote.call.Backoff;
import systems.glam.services.LoopHeartbeat;

import javax.sql.DataSource;
import java.time.Duration;

public interface BatchSqlExecutor<T> extends Runnable {

  static <T> BatchSqlExecutor<T> create(final Class<T> componentType,
                                        final DataSource datasource,
                                        final String statement,
                                        final int batchSize,
                                        final StatementPreparer<T> statementPreparer,
                                        final Duration batchDelay,
                                        final Backoff backoff) {
    return create(
        componentType,
        datasource, statement, batchSize, statementPreparer,
        batchDelay, backoff,
        LoopHeartbeat.NONE
    );
  }

  /// `heartbeat` ticks once per cycle of [#run]: after every drain of the pending queue
  /// (or its contained failure and backoff), and, while idle, each time a batch-delay
  /// window lapses with nothing queued -- so an idle executor stays visibly alive and one
  /// stuck inside a statement goes quiet. The idle window is `batchDelay` floored at
  /// `BatchSqlExecutorImpl.IDLE_TICK_FLOOR_NANOS`.
  static <T> BatchSqlExecutor<T> create(final Class<T> componentType,
                                        final DataSource datasource,
                                        final String statement,
                                        final int batchSize,
                                        final StatementPreparer<T> statementPreparer,
                                        final Duration batchDelay,
                                        final Backoff backoff,
                                        final LoopHeartbeat heartbeat) {
    return new BatchSqlExecutorImpl<>(
        componentType,
        datasource, statement, batchSize, statementPreparer,
        batchDelay, backoff,
        heartbeat
    );
  }

  void awaitBatchComplete() throws InterruptedException;

  void queue(final T item);
}
