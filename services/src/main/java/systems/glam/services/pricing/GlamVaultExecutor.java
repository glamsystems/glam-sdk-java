package systems.glam.services.pricing;

import software.sava.services.solana.remote.call.RpcCaller;

import java.util.concurrent.TimeUnit;

public interface GlamVaultExecutor extends Runnable {

  static GlamVaultExecutor createExecutor(final RpcCaller rpcCaller,
                                          final GlamStateContextCache stateCache,
                                          final TimeUnit scheduleTimeUnit,
                                          final int maxGroupRetries) {
    return new GlamVaultExecutorImpl(rpcCaller, stateCache, scheduleTimeUnit, maxGroupRetries);
  }
}
