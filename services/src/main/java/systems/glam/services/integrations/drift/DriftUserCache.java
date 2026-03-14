package systems.glam.services.integrations.drift;

import software.sava.core.accounts.PublicKey;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;

public interface DriftUserCache {

  static DriftUserCache createCache(final PublicKey driftProgram) {
    return new DriftUserCacheImpl(driftProgram);
  }

  void subscribe(final SolanaRpcWebsocket websocket);

  void subscribeToUserChanges(final PublicKey userKey, final DriftListener listener);

  void unSubscribeToUserChanges(final PublicKey userKey, final DriftListener listener);
}
