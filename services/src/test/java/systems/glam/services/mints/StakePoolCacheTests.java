package systems.glam.services.mints;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class StakePoolCacheTests {

  @Test
  void rejectsAFetchDelayThatWouldNotSleep() {
    // the run loop sleeps for this delay between passes, so anything under a
    // millisecond rounds to no sleep and spins a core
    for (final var tooSmall : new Duration[]{Duration.ZERO, Duration.ofNanos(999_999), Duration.ofMillis(-1)}) {
      final var ex = assertThrows(
          IllegalArgumentException.class,
          () -> new StakePoolCacheImpl(tooSmall, null, List.of(), Map.of(), Map.of())
      );
      assertTrue(ex.getMessage().contains("at least one millisecond"), ex.getMessage());
    }
    // exactly the floor is accepted
    assertNotNull(new StakePoolCacheImpl(Duration.ofMillis(1), null, List.of(), Map.of(), Map.of()));
  }
}
