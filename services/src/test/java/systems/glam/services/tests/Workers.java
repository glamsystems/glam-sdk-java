package systems.glam.services.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;

/// Joins a loop thread that is expected to exit on its own, bounded only in the failure
/// direction: a loop that keeps running because a tick went missing fails the assertion
/// rather than hanging the test, and the bound sits well inside the mutation watchdog's
/// budget. The thread is interrupted and joined again either way, so nothing leaks into
/// the next test or the next mutant.
public final class Workers {

  public static final long FIXTURE_DEADLINE_MILLIS = 500L;

  private Workers() {
  }

  public static void joinWithin(final Thread worker, final String expectation) throws InterruptedException {
    try {
      worker.join(FIXTURE_DEADLINE_MILLIS);
      assertFalse(worker.isAlive(), expectation);
    } finally {
      worker.interrupt();
      worker.join(FIXTURE_DEADLINE_MILLIS);
    }
  }
}
