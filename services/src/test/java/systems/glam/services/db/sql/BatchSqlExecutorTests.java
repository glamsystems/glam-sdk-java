package systems.glam.services.db.sql;

import org.junit.jupiter.api.Test;
import software.sava.services.core.remote.call.Backoff;
import systems.glam.services.tests.LogCapture;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.*;

/// run() executes on the test thread against proxied JDBC interfaces; the fake
/// interrupts the thread once the last expected batch lands, so the loop exits
/// through its InterruptedException path instead of awaiting new work.
final class BatchSqlExecutorTests {

  @Test
  void parseTableNameHandlesEachStatementShape() {
    assertEquals("holdings", BatchSqlExecutorImpl.parseTableName("INSERT INTO holdings (a, b) VALUES (?, ?)"));
    assertEquals("holdings", BatchSqlExecutorImpl.parseTableName("  insert into holdings(a) values(?)"));
    assertEquals("holdings", BatchSqlExecutorImpl.parseTableName("UPDATE holdings SET a = ?"));
    assertEquals("holdings", BatchSqlExecutorImpl.parseTableName("DELETE FROM holdings;"));
    assertEquals("holdings", BatchSqlExecutorImpl.parseTableName("MERGE INTO holdings USING dual"));
    // unrecognized statements fall back to the trimmed statement itself
    assertEquals("SELECT 1", BatchSqlExecutorImpl.parseTableName("  SELECT 1"));
    // a name running to the end of the statement, and a keyword with nothing
    // after it, must both stay within bounds
    assertEquals("holdings", BatchSqlExecutorImpl.parseTableName("UPDATE holdings"));
    assertEquals("", BatchSqlExecutorImpl.parseTableName("INSERT INTO "));
    assertEquals("", BatchSqlExecutorImpl.parseTableName("INSERT INTO"));
  }

  @Test
  void batchExecutionCountSumsAndFails() throws SQLException {
    assertEquals(5, BatchSqlExecutorImpl.batchExecutionCount(new int[]{2, 3}));
    // SUCCESS_NO_INFO counts as one row
    assertEquals(3, BatchSqlExecutorImpl.batchExecutionCount(new int[]{1, Statement.SUCCESS_NO_INFO, 1}));
    assertEquals(0, BatchSqlExecutorImpl.batchExecutionCount(new int[0]));
    final var failure = assertThrows(
        SQLException.class,
        () -> BatchSqlExecutorImpl.batchExecutionCount(new int[]{1, Statement.EXECUTE_FAILED})
    );
    assertTrue(failure.getMessage().contains("statement 1"), failure.getMessage());
  }

  /// Counts commits and executeBatch calls; can fail the first N executions and
  /// interrupts the running thread once `interruptOnExecution` is reached.
  private static final class FakeJdbc {

    int executions;
    int commits;
    int failFirst;
    int interruptOnExecution = 1;

    DataSource dataSource() {
      final var preparedStatement = (PreparedStatement) Proxy.newProxyInstance(
          PreparedStatement.class.getClassLoader(),
          new Class<?>[]{PreparedStatement.class},
          (proxy, method, args) -> switch (method.getName()) {
            case "executeBatch" -> {
              ++executions;
              if (executions >= interruptOnExecution) {
                Thread.currentThread().interrupt();
              }
              if (executions <= failFirst) {
                throw new SQLException("boom", "57P01", 57);
              }
              yield new int[]{1};
            }
            case "close" -> null;
            default -> throw new UnsupportedOperationException(method.getName());
          }
      );
      final var connection = (Connection) Proxy.newProxyInstance(
          Connection.class.getClassLoader(),
          new Class<?>[]{Connection.class},
          (proxy, method, args) -> switch (method.getName()) {
            case "prepareStatement" -> preparedStatement;
            case "commit" -> {
              ++commits;
              yield null;
            }
            case "close" -> null;
            default -> throw new UnsupportedOperationException(method.getName());
          }
      );
      return (DataSource) Proxy.newProxyInstance(
          DataSource.class.getClassLoader(),
          new Class<?>[]{DataSource.class},
          (proxy, method, args) -> switch (method.getName()) {
            case "getConnection" -> connection;
            default -> throw new UnsupportedOperationException(method.getName());
          }
      );
    }
  }

  private static BatchSqlExecutor<String> createExecutor(final FakeJdbc jdbc,
                                                         final int batchSize,
                                                         final List<String> prepared) {
    return BatchSqlExecutor.create(
        String.class,
        jdbc.dataSource(),
        "INSERT INTO items (v) VALUES (?)",
        batchSize,
        (ps, item) -> {
          prepared.add(item);
          return 1;
        },
        Duration.ZERO,
        Backoff.single(MILLISECONDS, 0)
    );
  }

  @Test
  void executesAFullBatchAndCommits() throws InterruptedException {
    final var jdbc = new FakeJdbc();
    final var prepared = new ArrayList<String>();
    final var executor = createExecutor(jdbc, 3, prepared);
    executor.queue("a");
    executor.queue("b");
    executor.queue("c");

    try (final var log = LogCapture.attach(BatchSqlExecutor.class.getName())) {
      executor.run();
      // a completed batch reports what it wrote
      log.assertLogged("Inserted");
      // a clean interrupt exit is not an error; the lock discipline held
      assertTrue(log.messages().stream().noneMatch(m -> m.contains("Unexpected error")),
          () -> String.join("\n", log.messages()));
    }

    assertEquals(List.of("a", "b", "c"), prepared);
    // the run loop hands the lock back on the way out
    assertFalse(((BatchSqlExecutorImpl<String>) executor).lock.isLocked());
    assertEquals(1, jdbc.executions);
    assertEquals(1, jdbc.commits);
    // the queue drained fully, so the completion latch is already open
    Thread.interrupted();
    executor.awaitBatchComplete();
  }

  @Test
  void flushesTheRemainderAsAFinalSubBatch() {
    final var jdbc = new FakeJdbc();
    jdbc.interruptOnExecution = 3;
    final var prepared = new ArrayList<String>();
    final var executor = createExecutor(jdbc, 2, prepared);
    for (int i = 0; i < 5; ++i) {
      executor.queue("item" + i);
    }

    try (final var log = LogCapture.attach(BatchSqlExecutor.class.getName())) {
      executor.run();
      // the odd remainder reports its own insert
      log.assertLogged("1 out of 1");
    }

    assertEquals(List.of("item0", "item1", "item2", "item3", "item4"), prepared);
    assertFalse(((BatchSqlExecutorImpl<String>) executor).lock.isLocked());
    // two full batches plus the odd remainder
    assertEquals(3, jdbc.executions);
    assertEquals(3, jdbc.commits);
  }

  @Test
  void interruptionDuringBackoffCancelsTheRetry() {
    final var jdbc = new FakeJdbc();
    jdbc.failFirst = 1;
    jdbc.interruptOnExecution = 1;
    final var prepared = new ArrayList<String>();
    final var executor = createExecutor(jdbc, 2, prepared);
    executor.queue("a");
    executor.queue("b");

    executor.run();

    // the backoff sleep is where a pending interrupt cancels the retry;
    // skipping it would re-execute the failed batch before exiting
    assertEquals(List.of("a", "b"), prepared);
    assertEquals(1, jdbc.executions);
    assertEquals(0, jdbc.commits);
  }

  @Test
  void aSubBatchSizeItemQueuedUpFrontIsFlushed() throws InterruptedException {
    final var jdbc = new FakeJdbc();
    final var prepared = new ArrayList<String>();
    final var executor = createExecutor(jdbc, 2, prepared);
    executor.queue("a");

    // fewer items than a batch at loop entry: the runner must pass through the
    // delay window and flush, not treat the non-empty queue as drained
    final var worker = new Thread(executor::run, "batch-sql-runner");
    worker.start();
    worker.join(5_000);
    if (worker.isAlive()) {
      worker.interrupt();
      worker.join(5_000);
      fail("the runner never flushed the sub-batch-size item");
    }
    assertEquals(List.of("a"), prepared);
    assertEquals(1, jdbc.executions);
    assertEquals(1, jdbc.commits);
  }

  private static void awaitTrue(final String what, final java.util.function.BooleanSupplier condition) throws InterruptedException {
    for (int i = 0; i < 5_000; ++i) {
      if (condition.getAsBoolean()) {
        return;
      }
      Thread.sleep(1);
    }
    fail("timed out awaiting " + what);
  }

  @Test
  void queueSignalsWakeTheRunnerAndCompletionWakesWaiters() throws InterruptedException {
    final var jdbc = new FakeJdbc();
    final var prepared = new ArrayList<String>();
    // a long delay window: only the batch-full signal can wake the runner in time
    final var executor = (BatchSqlExecutorImpl<String>) BatchSqlExecutor.create(
        String.class,
        jdbc.dataSource(),
        "INSERT INTO items (v) VALUES (?)",
        2,
        (ps, item) -> {
          prepared.add(item);
          return 1;
        },
        Duration.ofSeconds(30),
        Backoff.single(MILLISECONDS, 0)
    );

    final var worker = new Thread(executor::run, "batch-sql-runner");
    worker.start();
    try {
      // the runner parks awaiting the first item
      awaitTrue("runner parked on an empty queue",
          () -> executor.batchComplete && worker.getState() == Thread.State.WAITING);

      // the first item must signal the start window
      executor.queue("a");
      awaitTrue("the start-window signal woke the runner", () -> !executor.batchComplete);

      // a waiter arriving while the batch is open must block until completion
      final var sizeAtRelease = new java.util.concurrent.atomic.AtomicInteger(-1);
      final var waiter = new Thread(() -> {
        try {
          executor.awaitBatchComplete();
          sizeAtRelease.set(prepared.size());
        } catch (final InterruptedException e) {
          // fail via the join assert below
        }
      }, "batch-complete-waiter");
      waiter.start();
      awaitTrue("the waiter parked", () -> waiter.getState() == Thread.State.WAITING);

      // filling the batch must signal the delay window, not wait out the 30s
      executor.queue("b");
      worker.join(5_000);
      assertFalse(worker.isAlive(), "the batch-full signal never woke the runner");

      waiter.join(5_000);
      assertFalse(waiter.isAlive(), "completion never signalled the waiter");
      // the waiter was only released once the batch had fully executed
      assertEquals(2, sizeAtRelease.get());
      assertEquals(List.of("a", "b"), prepared);
      assertEquals(1, jdbc.executions);
      assertEquals(1, jdbc.commits);
      assertFalse(executor.lock.isLocked());
    } finally {
      worker.interrupt();
    }
  }

  @Test
  void aFailedMultiRowBatchRequeuesEveryItem() {
    final var jdbc = new FakeJdbc();
    jdbc.failFirst = 1;
    jdbc.interruptOnExecution = 2;
    final var prepared = new ArrayList<String>();
    // each item expands to two rows: the row count reaches the batch size
    // after two items, and a failure must requeue both of them
    final var executor = BatchSqlExecutor.create(
        String.class,
        jdbc.dataSource(),
        "INSERT INTO items (v) VALUES (?)",
        4,
        (ps, item) -> {
          prepared.add(item);
          return 2;
        },
        Duration.ZERO,
        Backoff.single(MILLISECONDS, 0)
    );
    executor.queue("a");
    executor.queue("b");
    executor.queue("c");

    executor.run();

    // the failed two-item batch is retried whole, then the remainder flushes
    assertEquals(List.of("a", "b", "a", "b", "c"), prepared);
    assertEquals(3, jdbc.executions);
    assertEquals(2, jdbc.commits);
  }

  @Test
  void zeroRowItemsStillFlushWithoutOverflowingTheBatch() {
    final var jdbc = new FakeJdbc();
    jdbc.interruptOnExecution = 2;
    final var prepared = new ArrayList<String>();
    // a preparer may add no rows for an item (e.g. filtered out); more items
    // than the batch size must still cycle through without overflowing batch[]
    final var executor = BatchSqlExecutor.create(
        String.class,
        jdbc.dataSource(),
        "INSERT INTO items (v) VALUES (?)",
        2,
        (ps, item) -> {
          prepared.add(item);
          return 0;
        },
        Duration.ZERO,
        Backoff.single(MILLISECONDS, 0)
    );
    executor.queue("a");
    executor.queue("b");
    executor.queue("c");

    try (final var log = LogCapture.attach(BatchSqlExecutor.class.getName())) {
      executor.run();
      assertTrue(log.messages().stream().noneMatch(m -> m.contains("Unexpected error")),
          () -> String.join("\n", log.messages()));
    }
    assertEquals(List.of("a", "b", "c"), prepared);
    assertEquals(2, jdbc.executions);
  }

  @Test
  void anUnexpectedRuntimeErrorIsLoggedAndEndsTheRun() {
    final var badDataSource = (DataSource) Proxy.newProxyInstance(
        DataSource.class.getClassLoader(),
        new Class<?>[]{DataSource.class},
        (proxy, method, args) -> {
          throw new IllegalStateException("pool torn down");
        }
    );
    final var executor = BatchSqlExecutor.create(
        String.class,
        badDataSource,
        "INSERT INTO items (v) VALUES (?)",
        2,
        (ps, item) -> 1,
        Duration.ZERO,
        Backoff.single(MILLISECONDS, 0)
    );
    executor.queue("a");
    executor.queue("b");

    try (final var log = LogCapture.attach(BatchSqlExecutor.class.getName())) {
      // the loop must not leak the runtime error to the executing thread,
      // and the death of the run loop must never be silent
      assertDoesNotThrow(executor::run);
      log.assertLogged("Unexpected error executing batch.");
    }
  }

  @Test
  void requeuesTheFailedBatchInOrderAndRetries() {
    final var jdbc = new FakeJdbc();
    jdbc.failFirst = 1;
    jdbc.interruptOnExecution = 2;
    final var prepared = new ArrayList<String>();
    final var executor = createExecutor(jdbc, 2, prepared);
    executor.queue("a");
    executor.queue("b");

    try (final var log = LogCapture.attach(BatchSqlExecutor.class.getName())) {
      executor.run();
      // the failure is reported with its SQL state and attempt count,
      // never swallowed silently
      log.assertLogged("Failed 1 times");
      log.assertLogged("57P01");
    }

    // the failed batch is prepared again, oldest first
    assertEquals(List.of("a", "b", "a", "b"), prepared);
    assertEquals(2, jdbc.executions);
    // only the successful execution commits
    assertEquals(1, jdbc.commits);
  }
}
