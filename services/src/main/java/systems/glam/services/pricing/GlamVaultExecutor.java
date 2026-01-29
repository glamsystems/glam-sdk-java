package systems.glam.services.pricing;

import software.sava.services.solana.remote.call.RpcCaller;

public interface GlamVaultExecutor extends Runnable {

  static GlamVaultExecutor createExecutor(final RpcCaller rpcCaller, final GlamStateContextCache stateCache) {
    return new GlamVaultExecutorImpl(rpcCaller, stateCache);
  }
}
