package systems.glam.services.db.sql;

import software.sava.services.core.remote.call.Backoff;
import systems.glam.services.LoopHeartbeat;

import javax.sql.DataSource;
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
  /// An idle runner re-arms its batch delay as the heartbeat period; below this floor a
  /// delay is a flush hint, not a pacing interval, and re-arming it would spin the idle loop.
  static final long IDLE_TICK_FLOOR_NANOS = TimeUnit.MILLISECONDS.toNanos(100);

  private final Class<T> componentType;
  private final DataSource datasource;
  private final String statement;
  private final String table;
  private final int batchSize;
  private final StatementPreparer<T> statementPreparer;
  private final long batchDelayNanos;
  /// Package-private so a test can pin the floor the public constructor applies.
  final long idleTickNanos;
  private final Backoff backoff;
  private final LoopHeartbeat heartbeat;
  private final ConcurrentLinkedDeque<T> pending;
  /// Package-private so tests can assert the lock is released; a leaked lock
  /// blocks every other caller and no result assertion can see it.
  final ReentrantLock lock;
  private final Condition startWindow;
  private final Condition batchLimit;
  private final Condition batchCompleteCondition;
  /// Package-private so tests can sequence the run loop deterministically
  /// against the queue/await signalling without sleeping on timing guesses.
  volatile boolean batchComplete;

  BatchSqlExecutorImpl(final Class<T> componentType,
                       final DataSource datasource,
                       final String statement,
                       final int batchSize,
                       final StatementPreparer<T> statementPreparer,
                       final Duration batchDelay,
                       final Backoff backoff,
                       final LoopHeartbeat heartbeat) {
    this(
        componentType,
        datasource, statement, batchSize, statementPreparer,
        batchDelay, Math.max(batchDelay.toNanos(), IDLE_TICK_FLOOR_NANOS),
        backoff,
        heartbeat
    );
  }

  // package-private for tests: the idle window is otherwise the batch delay floored at
  // IDLE_TICK_FLOOR_NANOS, and a test of the idle heartbeat should not wait out real windows
  BatchSqlExecutorImpl(final Class<T> componentType,
                       final DataSource datasource,
                       final String statement,
                       final int batchSize,
                       final StatementPreparer<T> statementPreparer,
                       final Duration batchDelay,
                       final long idleTickNanos,
                       final Backoff backoff,
                       final LoopHeartbeat heartbeat) {
    this.componentType = componentType;
    this.datasource = datasource;
    this.statement = statement;
    this.table = parseTableName(statement);
    this.batchSize = batchSize;
    this.statementPreparer = statementPreparer;
    this.batchDelayNanos = batchDelay.toNanos();
    this.idleTickNanos = idleTickNanos;
    this.backoff = backoff;
    this.heartbeat = heartbeat;
    this.pending = new ConcurrentLinkedDeque<>();
    this.lock = new ReentrantLock();
    this.startWindow = lock.newCondition();
    this.batchLimit = lock.newCondition();
    this.batchCompleteCondition = lock.newCondition();
    this.batchComplete = true;
  }

  /// A cycle is one committed batch. The runner wakes with work (or finds a full batch
  /// already pending), waits out the batch window, then drains everything pending as
  /// batches of `batchSize` rows and ticks once after each batch's commit -- a drain of N
  /// batches ticks N times, so a producer that keeps the queue ahead of the runner keeps it
  /// ticking. A failed batch ticks once after its contained backoff: a runner that keeps
  /// failing and retrying is alive, one stuck in getConnection, executeBatch, commit or the
  /// backoff sleep is not. The runner only wakes when work is queued, so while idle it
  /// re-arms `idleTickNanos` at a time instead of parking indefinitely and ticks each time a
  /// window lapses with nothing queued: an idle executor is visibly alive, at the cost of
  /// one timed wake-up per window.
  @Override
  public void run() {
    try {
      //noinspection unchecked
      final T[] batch = (T[]) Array.newInstance(componentType, batchSize);
      // batch[] is indexed by item, densely; numRows drives the executed
      // threshold. Multi-row preparers must not leave gaps in batch[], or the
      // failure requeue below would stop early and drop items from the retry.
      int numItems = 0, numRows, numInserted;
      for (long errorCount = 0, remainingNanos; ; ) {
        if (pending.size() < batchSize) {
          Arrays.fill(batch, null);
          lock.lock();
          try {
            while (pending.isEmpty()) {
              this.batchComplete = true;
              this.batchCompleteCondition.signalAll();
              startWindow.awaitNanos(idleTickNanos);
              // queue() adds before it signals, so a wake-up that still finds nothing
              // pending is a lapsed idle window (or a spurious wake-up), not work arriving.
              if (pending.isEmpty()) {
                heartbeat.tick();
              }
            }
            this.batchComplete = false;
            for (remainingNanos = batchDelayNanos; pending.size() < batchSize && remainingNanos > 0; ) {
              remainingNanos = batchLimit.awaitNanos(remainingNanos);
            }
          } finally {
            lock.unlock();
          }
        }
        try (final var connection = datasource.getConnection()) {
          try (final var ps = connection.prepareStatement(statement)) {
            for (numItems = 0, numRows = 0; ; ) {
              final var item = pending.pollFirst();
              if (item == null) {
                if (numItems > 0) {
                  numInserted = batchExecutionCount(ps.executeBatch());
                  connection.commit();
                  logger.log(INFO,
                      "Inserted {0} out of {1} {2} rows into {3}.",
                      numInserted, numRows, componentType.getSimpleName(), table
                  );
                  numItems = 0;
                  numRows = 0;
                  // a committed batch is one cycle
                  heartbeat.tick();
                }
                break;
              }
              batch[numItems++] = item;
              numRows += statementPreparer.prepare(ps, item);
              if (numRows >= batchSize || numItems == batch.length) {
                numInserted = batchExecutionCount(ps.executeBatch());
                connection.commit();
                logger.log(INFO,
                    "Inserted {0} out of {1} {2} rows into {3}.",
                    numInserted, numRows, componentType.getSimpleName(), table
                );
                numItems = 0;
                numRows = 0;
                heartbeat.tick();
              }
            }
          }
        } catch (final SQLException e) {
          // every slot below numItems holds an item from the failed batch
          for (int i = numItems - 1; i >= 0; --i) {
            pending.addFirst(batch[i]);
          }
          final var sqlState = e.getSQLState();
          logger.log(ERROR, "Failed {0} times to write {1}: [ state => {2}, errorCode => {3}, cause => {4}, message => {5} ]",
              ++errorCount, componentType.getSimpleName(), sqlState, e.getErrorCode(), e.getCause(), e.getMessage()
          );
          final long backoffDelay = backoff.delay(errorCount, TimeUnit.MILLISECONDS);
          //noinspection BusyWait
          Thread.sleep(backoffDelay);
          // the failure was contained and its backoff slept through: a retrying runner is alive
          heartbeat.tick();
        }
      }
    } catch (final InterruptedException e) {
      // exit
    } catch (final RuntimeException ex) {
      logger.log(ERROR, "Unexpected error executing batch.", ex);
    }
  }

  @Override
  public void awaitBatchComplete() throws InterruptedException {
    if (!this.batchComplete) {
      lock.lock();
      try {
        while (!this.batchComplete) {
          this.batchCompleteCondition.await();
        }
      } finally {
        lock.unlock();
      }
    }
  }

  static String parseTableName(final String statement) {
    final String normalized = statement.stripLeading();
    final String upper = normalized.toUpperCase();
    final int start;
    if (upper.startsWith("INSERT INTO")) {
      start = "INSERT INTO".length();
    } else if (upper.startsWith("UPDATE")) {
      start = "UPDATE".length();
    } else if (upper.startsWith("DELETE FROM")) {
      start = "DELETE FROM".length();
    } else if (upper.startsWith("MERGE INTO")) {
      start = "MERGE INTO".length();
    } else {
      return normalized;
    }
    int i = start;
    final int len = normalized.length();
    while (i < len && Character.isWhitespace(normalized.charAt(i))) {
      ++i;
    }
    int end = i;
    while (end < len) {
      final char c = normalized.charAt(end);
      if (Character.isWhitespace(c) || c == '(' || c == ';') {
        break;
      }
      ++end;
    }
    return normalized.substring(i, end);
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
