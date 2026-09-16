package systems.glam.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class LoopHeartbeatTests {

  @Test
  void theNoneHeartbeatAcceptsEveryTickSilently() {
    // NONE is what every factory without a heartbeat parameter passes: a loop
    // ticking it must behave exactly as it did before the seam existed
    for (int i = 0; i < 3; ++i) {
      assertDoesNotThrow(LoopHeartbeat.NONE::tick);
    }
  }

  @Test
  void aHeartbeatIsOneMethodALambdaCanSupply() {
    final int[] ticks = new int[1];
    final LoopHeartbeat heartbeat = () -> ++ticks[0];
    heartbeat.tick();
    heartbeat.tick();
    assertEquals(2, ticks[0]);
  }
}
