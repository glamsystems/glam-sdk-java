package systems.glam.services.tests;

import systems.glam.services.LoopHeartbeat;

import java.util.concurrent.atomic.AtomicInteger;

/// Counts ticks and, once `interruptOnTick` of them have landed, interrupts the ticking
/// thread -- the loop's own -- so a loop whose only activity is its heartbeat leaves
/// through its InterruptedException path at the very next blocking call, without the
/// test sleeping or spinning. A loop with work in flight should be exited through its
/// existing fake instead, so that a removed tick fails an assertion rather than hanging.
public final class RecordingHeartbeat implements LoopHeartbeat {

  private final AtomicInteger ticks = new AtomicInteger();
  private final int interruptOnTick;

  public RecordingHeartbeat() {
    this(Integer.MAX_VALUE);
  }

  public RecordingHeartbeat(final int interruptOnTick) {
    this.interruptOnTick = interruptOnTick;
  }

  @Override
  public void tick() {
    if (ticks.incrementAndGet() >= interruptOnTick) {
      Thread.currentThread().interrupt();
    }
  }

  public int ticks() {
    return ticks.get();
  }
}
