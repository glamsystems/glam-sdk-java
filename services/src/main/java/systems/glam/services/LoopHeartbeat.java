package systems.glam.services;

/// One tick per completed cycle of a supervised loop, called by the loop itself at its
/// cycle boundary: a loop stuck inside a cycle stops ticking, while a loop that merely
/// idles keeps ticking on its own schedule. Each `run()` documents what its cycle is and
/// where the tick sits. [#NONE] is behaviourally identical to the code before the seam
/// existed and is what every factory without a heartbeat parameter passes; a supervisor
/// with the same shape adapts with a method reference.
@FunctionalInterface
public interface LoopHeartbeat {

  LoopHeartbeat NONE = () -> {
  };

  void tick();
}
