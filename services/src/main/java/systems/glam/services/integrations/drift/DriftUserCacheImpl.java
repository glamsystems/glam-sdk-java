package systems.glam.services.integrations.drift;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.drift.gen.types.User;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

final class DriftUserCacheImpl implements DriftUserCache, Consumer<AccountInfo<byte[]>> {

  private static final Function<PublicKey, Map<PublicKey, DriftListener>> CREATE_LISTENER_MAP =
      _ -> new ConcurrentHashMap<>();
  private final PublicKey driftProgram;
  private final Map<PublicKey, Map<PublicKey, DriftListener>> listeners;

  DriftUserCacheImpl(final PublicKey driftProgram) {
    this.driftProgram = driftProgram;
    this.listeners = new ConcurrentHashMap<>(128);
  }

  @Override
  public void subscribe(final SolanaRpcWebsocket websocket) {
    websocket.programSubscribe(
        driftProgram,
        List.of(
            User.DISCRIMINATOR_FILTER,
            User.SIZE_FILTER
        ),
        this
    );
  }

  @Override
  public void subscribeToUserChanges(final PublicKey userKey, final DriftListener listener) {
    this.listeners.computeIfAbsent(userKey, CREATE_LISTENER_MAP).put(listener.key(), listener);
  }

  @Override
  public void unSubscribeToUserChanges(final PublicKey userKey, final DriftListener listener) {
    final var listeners = this.listeners.get(userKey);
    if (listeners != null) {
      listeners.remove(listener.key());
    }
  }

  @Override
  public void accept(final AccountInfo<byte[]> accountInfo) {
    final var userKey = accountInfo.pubKey();
    final var listeners = this.listeners.get(userKey);
    if (listeners != null) {
      final long slot = accountInfo.context().slot();
      final byte[] data = accountInfo.data();
      for (final var listener : listeners.values()) {
        listener.onDriftUserChange(slot, userKey, data);
      }
    }
  }
}
