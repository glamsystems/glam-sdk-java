package systems.glam.services.pricing;

final class GlamVaultExecutorImpl implements GlamVaultExecutor {

  private final GlamStateContextCache stateCache;

  GlamVaultExecutorImpl(final GlamStateContextCache stateCache) {
    this.stateCache = stateCache;
  }

  @Override
  public void run() {
    for (;;) {

    }
  }
}
