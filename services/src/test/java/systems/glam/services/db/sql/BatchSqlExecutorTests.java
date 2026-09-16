package systems.glam.services.db.sql;

import org.junit.jupiter.api.Test;
import software.sava.services.core.remote.call.Backoff;
import systems.glam.services.LoopHeartbeat;
import systems.glam.services.tests.LogCapture;
import systems.glam.services.tests.RecordingHeartbeat;
import systems.glam.services.tests.Workers;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

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
  /// interrupts the running thread once `interruptOnExecution` is reached. `onExecution`
  /// runs on the runner's thread with each execution's ordinal before the fake decides
  /// its fate: a producer hooked there can queue into the executor mid-drain. The
  /// `failConnection`th getConnection call (0: never) fails instead of handing out the
  /// connection, so a retry can be made to fail before it reaches a statement.
  private static final class FakeJdbc {

    int executions;
    int commits;
    int connections;
    int failFirst;
    int failConnection;
    int interruptOnExecution = 1;
    IntConsumer onExecution = execution -> {
    };

    DataSource dataSource() {
      final var preparedStatement = (PreparedStatement) Proxy.newProxyInstance(
          PreparedStatement.class.getClassLoader(),
          new Class<?>[]{PreparedStatement.class},
          (proxy, method, args) -> switch (method.getName()) {
            case "executeBatch" -> {
              onExecution.accept(++executions);
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
            case "getConnection" -> {
              if (++connections == failConnection) {
                throw new SQLException("pool exhausted", "08001", 0);
              }
              yield connection;
            }
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

  private static BatchSqlExecutorImpl<String> createExecutor(final FakeJdbc jdbc,
                                                             final int batchSize,
                                                             final List<String> prepared,
                                                             final Duration batchDelay,
                                                             final LoopHeartbeat heartbeat) {
    return (BatchSqlExecutorImpl<String>) BatchSqlExecutor.create(
        String.class,
        jdbc.dataSource(),
        "INSERT INTO items (v) VALUES (?)",
        batchSize,
        (ps, item) -> {
          prepared.add(item);
          return 1;
        },
        batchDelay,
        Backoff.single(MILLISECONDS, 0),
        heartbeat
    );
  }

  @Test
  void aSingleCommittedBatchTicksOnce() {
    final var jdbc = new FakeJdbc();
    final var prepared = new ArrayList<String>();
    final var heartbeat = new RecordingHeartbeat();
    final var executor = createExecutor(jdbc, 3, prepared, Duration.ZERO, heartbeat);
    executor.queue("a");
    executor.queue("b");
    executor.queue("c");

    executor.run();

    // one batch, one commit, one tick -- and nothing extra once the queue is found empty
    assertEquals(List.of("a", "b", "c"), prepared);
    assertEquals(1, jdbc.executions);
    assertEquals(1, jdbc.commits);
    assertEquals(1, heartbeat.ticks());
    assertFalse(executor.lock.isLocked());
  }

  @Test
  void aDrainTicksOncePerCommittedBatch() {
    final var jdbc = new FakeJdbc();
    jdbc.interruptOnExecution = 3;
    final var prepared = new ArrayList<String>();
    final var heartbeat = new RecordingHeartbeat();
    final var executor = createExecutor(jdbc, 2, prepared, Duration.ZERO, heartbeat);
    for (int i = 0; i < 5; ++i) {
      executor.queue("item" + i);
    }

    executor.run();

    // one wake-up drained the whole queue as two full batches and the odd remainder:
    // three commits, three ticks -- the remainder's commit is a cycle like any other
    assertEquals(3, jdbc.executions);
    assertEquals(3, jdbc.commits);
    assertEquals(3, heartbeat.ticks());
    assertFalse(executor.lock.isLocked());
  }

  @Test
  void aProducerThatKeepsTheQueueAheadOfTheRunnerTicksPerCommittedBatch() {
    final var jdbc = new FakeJdbc();
    final int batches = 4;
    jdbc.interruptOnExecution = batches;
    final var prepared = new ArrayList<String>();
    final var heartbeat = new RecordingHeartbeat();
    final var executor = createExecutor(jdbc, 1, prepared, Duration.ZERO, heartbeat);
    // the producer queues the next item during each execution, before the runner polls
    // again: the queue is never empty while commits keep succeeding, so the runner never
    // returns to its idle wait and a tick taken only there, or only after a drain that
    // ends, would never land
    jdbc.onExecution = execution -> {
      if (execution < batches) {
        executor.queue("item" + execution);
      }
    };
    executor.queue("item0");

    executor.run();

    // every committed batch ticked, and the fake's interrupt then ended the run at the
    // idle wait before any idle window could lapse
    assertEquals(List.of("item0", "item1", "item2", "item3"), prepared);
    assertEquals(batches, jdbc.executions);
    assertEquals(batches, jdbc.commits);
    assertEquals(batches, heartbeat.ticks());
    assertTrue(executor.batchComplete);
    assertFalse(executor.lock.isLocked());
  }

  @Test
  void aFailedBatchStillTicksAfterItsBackoff() {
    final var jdbc = new FakeJdbc();
    jdbc.failFirst = 1;
    jdbc.interruptOnExecution = 2;
    final var prepared = new ArrayList<String>();
    final var heartbeat = new RecordingHeartbeat();
    final var executor = createExecutor(jdbc, 2, prepared, Duration.ZERO, heartbeat);
    executor.queue("a");
    executor.queue("b");

    executor.run();

    // the contained failure ticks once after its backoff (a retrying runner is alive),
    // then the retry's commit ticks once more: two ticks
    assertEquals(List.of("a", "b", "a", "b"), prepared);
    assertEquals(2, jdbc.executions);
    assertEquals(1, jdbc.commits);
    assertEquals(2, heartbeat.ticks());
  }

  @Test
  void aCycleCutShortInItsBackoffDoesNotTick() {
    final var jdbc = new FakeJdbc();
    jdbc.failFirst = 1;
    jdbc.interruptOnExecution = 1;
    final var prepared = new ArrayList<String>();
    final var heartbeat = new RecordingHeartbeat();
    final var executor = createExecutor(jdbc, 2, prepared, Duration.ZERO, heartbeat);
    executor.queue("a");
    executor.queue("b");

    executor.run();

    // the interrupt lands in the backoff sleep, ahead of the failure's tick: nothing
    // committed and nothing was slept through, so nothing ticked
    assertEquals(1, jdbc.executions);
    assertEquals(0, heartbeat.ticks());
  }

  /// The idle-window seam: a one-millisecond window so the idle tests wait out
  /// windows, not the production floor.
  private static BatchSqlExecutorImpl<String> createIdleExecutor(final FakeJdbc jdbc,
                                                                 final int batchSize,
                                                                 final List<String> prepared,
                                                                 final LoopHeartbeat heartbeat) {
    return new BatchSqlExecutorImpl<>(
        String.class,
        jdbc.dataSource(),
        "INSERT INTO items (v) VALUES (?)",
        batchSize,
        (ps, item) -> {
          prepared.add(item);
          return 1;
        },
        Duration.ofMillis(1),
        MILLISECONDS.toNanos(1),
        Backoff.single(MILLISECONDS, 0),
        heartbeat
    );
  }

  @Test
  void theIdleWindowIsTheBatchDelayFlooredAtTheProductionFloor() {
    final var jdbc = new FakeJdbc();
    final var prepared = new ArrayList<String>();
    // a flush hint below the floor would spin the idle loop: the floor wins
    final var hinted = createExecutor(jdbc, 2, prepared, Duration.ofMillis(1), LoopHeartbeat.NONE);
    assertEquals(BatchSqlExecutorImpl.IDLE_TICK_FLOOR_NANOS, hinted.idleTickNanos);
    assertEquals(MILLISECONDS.toNanos(100), hinted.idleTickNanos);
    // a pacing delay above it is the idle cadence as configured
    final var paced = createExecutor(jdbc, 2, prepared, Duration.ofMillis(250), LoopHeartbeat.NONE);
    assertEquals(MILLISECONDS.toNanos(250), paced.idleTickNanos);
  }

  @Test
  void anIdleRunnerTicksOncePerLapsedWindow() throws InterruptedException {
    final var jdbc = new FakeJdbc();
    final var prepared = new ArrayList<String>();
    // the heartbeat interrupts the runner on its third idle tick: the re-armed
    // window then throws instead of parking, and run() exits on its own
    final var heartbeat = new RecordingHeartbeat(3);
    final var executor = createIdleExecutor(jdbc, 2, prepared, heartbeat);

    final var worker = new Thread(executor::run, "batch-sql-idle-runner");
    worker.start();
    Workers.joinWithin(worker, "an idle runner must keep ticking on its window and honour the interrupt");

    // nothing was ever queued: every tick was an idle window, no statement ran
    assertEquals(3, heartbeat.ticks());
    assertEquals(0, jdbc.executions);
    assertTrue(prepared.isEmpty());
    assertTrue(executor.batchComplete);
    assertFalse(executor.lock.isLocked());
  }

  @Test
  void workArrivingWhileParkedTicksOnlyForItsDrain() throws InterruptedException {
    final var jdbc = new FakeJdbc();
    final var prepared = new ArrayList<String>();
    final var ticks = new AtomicInteger();
    final var parked = new CountDownLatch(1);
    // an idle tick is taken under the executor's lock, so once it lands the runner
    // holds the lock until it re-arms its window and parks: the lock is the seam
    final var executor = createIdleExecutor(jdbc, 1, prepared, () -> {
      ticks.incrementAndGet();
      parked.countDown();
    });

    final var worker = new Thread(executor::run, "batch-sql-runner");
    worker.start();
    try {
      assertTrue(parked.await(Workers.FIXTURE_DEADLINE_MILLIS, MILLISECONDS), "the idle runner never ticked");
      final int idleTicks;
      executor.lock.lock();
      try {
        // holding the lock the runner is inside its timed wait, so this count is
        // stable and the item lands before that wait's own emptiness check
        idleTicks = ticks.get();
        executor.queue("a");
      } finally {
        executor.lock.unlock();
      }
      Workers.joinWithin(worker, "the queued item must wake the runner, which then exits on the fake's interrupt");

      // the wake-up that found work is not an idle window: only the drain ticked
      assertEquals(List.of("a"), prepared);
      assertEquals(1, jdbc.executions);
      assertEquals(idleTicks + 1, ticks.get());
      assertFalse(executor.lock.isLocked());
    } finally {
      worker.interrupt();
    }
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
      // the runner parks awaiting the first item -- a timed park, since an idle
      // runner re-arms its window to keep its heartbeat ticking
      awaitTrue("runner parked on an empty queue",
          () -> executor.batchComplete && worker.getState() == Thread.State.TIMED_WAITING);

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

  @Test
  void aConnectionFailureAfterARequeuedFullBatchDoesNotRequeueItAgain() {
    final var jdbc = new FakeJdbc();
    // the full batch fails, is requeued, then the retry's getConnection fails before any
    // statement: that failure has nothing in flight to hand back
    jdbc.failFirst = 1;
    jdbc.failConnection = 2;
    jdbc.interruptOnExecution = 2;
    final var prepared = new ArrayList<String>();
    final var heartbeat = new RecordingHeartbeat();
    final var executor = createExecutor(jdbc, 2, prepared, Duration.ZERO, heartbeat);
    executor.queue("a");
    executor.queue("b");

    try (final var log = LogCapture.attach(BatchSqlExecutor.class.getName())) {
      executor.run();
      log.assertLogged("Failed 2 times");
      assertTrue(log.messages().stream().noneMatch(m -> m.contains("Unexpected error")),
          () -> String.join("\n", log.messages()));
    }

    // the batch is written exactly once after its single requeue, not a second copy of
    // it from the connection failure
    assertEquals(List.of("a", "b", "a", "b"), prepared);
    assertEquals(3, jdbc.connections);
    assertEquals(2, jdbc.executions);
    assertEquals(1, jdbc.commits);
    // the two contained failures each ticked after their backoff, then the commit
    assertEquals(3, heartbeat.ticks());
    assertFalse(executor.lock.isLocked());
  }

  @Test
  void aConnectionFailureAfterARequeuedRemainderKeepsTheRunnerAlive() {
    final var jdbc = new FakeJdbc();
    // a sub-batch-size remainder fails and is requeued; the runner then passes through
    // the fill/wait block, which clears batch[], before the retry's getConnection fails
    jdbc.failFirst = 1;
    jdbc.failConnection = 2;
    jdbc.interruptOnExecution = 2;
    final var prepared = new ArrayList<String>();
    final var heartbeat = new RecordingHeartbeat();
    final var executor = createExecutor(jdbc, 2, prepared, Duration.ZERO, heartbeat);
    executor.queue("a");

    try (final var log = LogCapture.attach(BatchSqlExecutor.class.getName())) {
      executor.run();
      log.assertLogged("Failed 2 times");
      // requeueing the cleared slots would push a null into the deque and end the run
      assertTrue(log.messages().stream().noneMatch(m -> m.contains("Unexpected error")),
          () -> String.join("\n", log.messages()));
    }

    assertEquals(List.of("a", "a"), prepared);
    assertEquals(3, jdbc.connections);
    assertEquals(2, jdbc.executions);
    assertEquals(1, jdbc.commits);
    assertEquals(3, heartbeat.ticks());
    assertFalse(executor.lock.isLocked());
  }
}
