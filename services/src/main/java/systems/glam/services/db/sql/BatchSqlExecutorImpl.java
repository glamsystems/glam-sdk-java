package systems.glam.services.db.sql;

import software.sava.services.core.remote.call.Backoff;

import java.lang.reflect.Array;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;

final class BatchSqlExecutorImpl<T> implements BatchSqlExecutor<T> {

  private static final System.Logger logger = System.getLogger(BatchSqlExecutor.class.getName());

  private final Class<T> componentType;
  private final SqlDataSource datasource;
  private final String statement;
  private final int batchSize;
  private final StatementPreparer<T> statementPreparer;
  private final long batchDelayNanos;
  private final Backoff backoff;
  private final ConcurrentLinkedDeque<T> pending;
  private final ReentrantLock lock;
  private final Condition startWindow;
  private final Condition batchLimit;

  BatchSqlExecutorImpl(final Class<T> componentType,
                       final SqlDataSource datasource,
                       final String statement,
                       final int batchSize,
                       final StatementPreparer<T> statementPreparer,
                       final Duration batchDelay,
                       final Backoff backoff) {
    this.componentType = componentType;
    this.datasource = datasource;
    this.statement = statement;
    this.batchSize = batchSize;
    this.statementPreparer = statementPreparer;
    this.batchDelayNanos = batchDelay.toNanos();
    this.backoff = backoff;
    this.pending = new ConcurrentLinkedDeque<>();
    this.lock = new ReentrantLock();
    this.startWindow = lock.newCondition();
    this.batchLimit = lock.newCondition();
  }

  @Override
  public void run() {
    try {
      //noinspection unchecked
      final T[] batch = (T[]) Array.newInstance(componentType, batchSize);
      int numItems = 0, numInserted = 0;
      for (long errorCount = 0, remainingNanos; ; ) {
        if (pending.size() < batchSize) {
          Arrays.fill(batch, null);
          lock.lock();
          try {
            while (pending.isEmpty()) {
              startWindow.await();
            }
            for (remainingNanos = batchDelayNanos; pending.size() < batchSize && remainingNanos > 0; ) {
              remainingNanos = batchLimit.awaitNanos(remainingNanos);
            }
          } finally {
            lock.unlock();
          }
        }
        try (final var connection = datasource.getConnection()) {
          connection.setAutoCommit(false);
          try (final var ps = connection.prepareStatement(statement)) {
            for (numItems = 0; ; ) {
              final var item = pending.pollFirst();
              if (item == null) {
                if (numItems > 0) {
                  numInserted = batchExecutionCount(ps.executeBatch());
                  connection.commit();
                  logger.log(INFO,
                      "Inserted {0} out of {1} {2} rows.",
                      numInserted, numItems, componentType.getSimpleName()
                  );
                  numItems = 0;
                }
                break;
              }
              batch[numItems] = item;
              statementPreparer.prepare(ps, item);
              ps.addBatch();
              if (++numItems == batchSize) {
                numInserted = batchExecutionCount(ps.executeBatch());
                connection.commit();
                logger.log(INFO,
                    "Inserted {0} out of {1} {2} rows.",
                    numInserted, numItems, componentType.getSimpleName()
                );
                numItems = 0;
              }
            }
          }
        } catch (final SQLException e) {
          for (int i = numItems - 1; i >= 0; --i) {
            pending.addFirst(batch[i]);
          }
          final var sqlState = e.getSQLState();
          logger.log(ERROR, "Failed {0} times to write {1}: [ state => {2}, errorCode => {3}, cause => {4}, message => {5} ]",
              ++errorCount, componentType.getSimpleName(), sqlState, e.getErrorCode(), e.getCause(), e.getMessage()
          );
          final long backoffDelay = backoff.delay(errorCount, TimeUnit.MILLISECONDS);
          Thread.sleep(backoffDelay);
        }
      }
    } catch (final InterruptedException e) {
      // exit
    } catch (final RuntimeException ex) {
      logger.log(ERROR, "Unexpected error executing batch.", ex);
    }
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

  @Override
  public void queue(final T item) {
    final boolean isEmpty = pending.isEmpty();
    pending.addLast(item);
    if (isEmpty || pending.size() >= batchSize) {
      lock.lock();
      try {
        if (isEmpty) {
          startWindow.signal();
        } else {
          batchLimit.signal();
        }
      } finally {
        lock.unlock();
      }
    }
  }
}
