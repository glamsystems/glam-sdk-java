package systems.glam.services.io;

import java.util.concurrent.locks.ReentrantLock;

/// Reads a [KeyedFlatFile]'s lock for tests outside this package: the caches in
/// `systems.glam.services.mints` persist through one, and a lock an operation leaves held
/// is visible to no result assertion. Another thread that touches the file then parks in
/// `lock()`, which takes no timeout and ignores interrupts, so a test that closes the file
/// after a worker leaked its lock hangs instead of failing. Probing first turns that hang
/// into a synchronous failure.
///
/// Test code, not a mutation target: the name carries `Test` so the services suite's
/// `systems.glam.services.*Test*` exclusion covers it, and it declares no tests.
public final class KeyedFlatFileTestProbe {

  private KeyedFlatFileTestProbe() {
  }

  /// Whether any thread holds the file's lock. This reads ownership directly; `tryLock()`
  /// could not, since the owner of a reentrant lock can always take it again.
  public static boolean isLocked(final KeyedFlatFile<?> file) {
    return lock(file).isLocked();
  }

  /// Releases every hold the calling thread still has on the file's lock and says whether
  /// it had any. Only the owner can release a reentrant lock, so this is how a worker that
  /// leaked one, having recorded the leak, hands it back instead of leaving every other
  /// worker parked behind it for good.
  public static boolean releaseHeldLock(final KeyedFlatFile<?> file) {
    final var lock = lock(file);
    final int holds = lock.getHoldCount();
    for (int i = 0; i < holds; ++i) {
      lock.unlock();
    }
    return holds > 0;
  }

  private static ReentrantLock lock(final KeyedFlatFile<?> file) {
    return ((KeyedFlatFileImpl<?>) file).lock;
  }
}
