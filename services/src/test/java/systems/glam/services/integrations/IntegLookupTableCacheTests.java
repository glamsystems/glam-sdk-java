package systems.glam.services.integrations;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

final class IntegLookupTableCacheTests {

  @Test
  void rejectsAFetchDelayThatWouldNotSleep(@TempDir final Path tempDir) {
    // the run loop sleeps for this delay between passes, so anything under a
    // millisecond rounds to no sleep and spins a core
    for (final var tooSmall : new Duration[]{Duration.ZERO, Duration.ofNanos(999_999), Duration.ofMillis(-1)}) {
      final var ex = assertThrows(
          IllegalArgumentException.class,
          () -> new IntegLookupTableCacheImpl(tooSmall, tempDir, new ConcurrentHashMap<>(), null)
      );
      assertTrue(ex.getMessage().contains("at least one millisecond"), ex.getMessage());
    }
    // exactly the floor is accepted
    assertNotNull(new IntegLookupTableCacheImpl(Duration.ofMillis(1), tempDir, new ConcurrentHashMap<>(), null));
  }
}
