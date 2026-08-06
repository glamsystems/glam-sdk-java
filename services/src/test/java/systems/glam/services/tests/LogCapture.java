package systems.glam.services.tests;

import java.text.MessageFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/// Captures a logger's records for the duration of a test, keeping the console
/// quiet. Services log before they swallow or reject something, so without this
/// a dropped `logger.log` is invisible to every assertion — capturing it pins
/// the contract that a failure is never silent.
public final class LogCapture extends Handler implements AutoCloseable {

  private final Logger logger;
  private final Level previousLevel;
  private final boolean previousUseParentHandlers;
  /// Services log from their own threads — a cache poll loop, a fetcher worker —
  /// so records arrive concurrently with the test thread reading them. A plain
  /// `ArrayList` here can drop or corrupt a record, and under PIT that reads as a
  /// mutant surviving rather than as a broken fixture.
  private final List<LogRecord> records = new CopyOnWriteArrayList<>();
  private volatile boolean closed;

  public static LogCapture attach(final String loggerName) {
    return new LogCapture(Logger.getLogger(loggerName));
  }

  private LogCapture(final Logger logger) {
    this.logger = logger;
    this.previousLevel = logger.getLevel();
    this.previousUseParentHandlers = logger.getUseParentHandlers();
    logger.setLevel(Level.ALL);
    logger.setUseParentHandlers(false);
    logger.addHandler(this);
  }

  @Override
  public void publish(final LogRecord record) {
    records.add(record);
  }

  @Override
  public void flush() {
  }

  /// Formats each record the way a handler would: services log with `{0}`
  /// style patterns, so the interesting values live in the parameters rather
  /// than in the raw message.
  public List<String> messages() {
    return records.stream().map(LogCapture::format).toList();
  }

  private static String format(final LogRecord record) {
    final var message = record.getMessage();
    final var parameters = record.getParameters();
    if (message == null || parameters == null || parameters.length == 0) {
      return message;
    }
    try {
      return MessageFormat.format(message, parameters);
    } catch (final IllegalArgumentException e) {
      return message;
    }
  }

  public void assertLogged(final String fragment) {
    assertTrue(
        messages().stream().anyMatch(m -> m != null && m.contains(fragment)),
        () -> "expected a log record containing \"" + fragment + "\", got " + messages()
    );
  }

  /// Idempotent: a test that closes in a finally block after an assertion already
  /// closed it must not restore stale state over a later capture.
  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    logger.removeHandler(this);
    logger.setLevel(previousLevel);
    logger.setUseParentHandlers(previousUseParentHandlers);
  }
}
