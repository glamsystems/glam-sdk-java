package systems.glam.services;

/// One tick per completed cycle of a supervised loop, called by the loop itself, on its own
/// thread, at its cycle boundary: a loop stuck inside a cycle stops ticking, while a loop that
/// merely idles keeps ticking on its own schedule. Each `run()` documents what its cycle is and
/// where the tick sits; a supervisor sizes its dead-after threshold from the loop's longest
/// healthy cycle, not only from its idle cadence.
///
/// A tick must return promptly and must not block or throw: some ticks are taken while the loop
/// holds the lock its producers queue through (an idle `BatchSqlExecutor`, a reactive
/// `AccountFetcher`), so a blocking tick stalls every producer, and every loop treats a throwing
/// tick as its own failure and ends.
///
/// [#NONE] is what every factory without a heartbeat parameter passes. The loops that only tick
/// are unchanged by it; the two that re-arm an idle window so an idle loop keeps ticking
/// (`BatchSqlExecutor`, a reactive `AccountFetcher`) now park in timed waits, one wake-up per
/// window, where they used to park indefinitely -- the only difference a caller can observe.
/// A supervisor with the same shape adapts with a method reference.
@FunctionalInterface
public interface LoopHeartbeat {

  LoopHeartbeat NONE = () -> {
  };

  void tick();
}
