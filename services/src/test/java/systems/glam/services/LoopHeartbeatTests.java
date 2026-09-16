package systems.glam.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class LoopHeartbeatTests {

  @Test
  void theNoneHeartbeatAcceptsEveryTickSilently() {
    // NONE is what every factory without a heartbeat parameter passes: ticking
    // it must never disturb the loop
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
