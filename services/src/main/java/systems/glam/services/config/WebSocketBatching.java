package systems.glam.services.config;

import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

import static java.lang.System.Logger.Level.INFO;

/// Whether a websocket built from a delegate service's configuration batches its subscribe
/// requests, decided by its endpoint (owner decision, 2026-11-11): an endpoint known to answer a
/// JSON-RPC array of subscribe requests with an array of results keeps the batching the
/// configuration names, and any other is sent one request per message, whatever the configuration
/// names. So `websocketSubscribeBatchBytes` can lower the cap or turn batching off at a known
/// endpoint, and cannot turn it on at another: an endpoint is added to
/// [#HOSTS_THAT_READ_A_BATCH] first.
///
/// sava-rpc recovers by itself from an endpoint that answers an array with a parse error, by
/// re-sending each request alone, but only after the refused message and a WARNING on every
/// websocket it builds; and it has no recovery from an endpoint that reads an array and refuses
/// its requests one by one. Not sending an array to an endpoint nobody measured costs neither.
final class WebSocketBatching {

  private static final System.Logger logger = System.getLogger(WebSocketBatching.class.getName());

  /// The hosts measured to read a batch, in lower case: the public Agave endpoint under the name
  /// the Solana documentation gives it, `api.mainnet.solana.com`, and under its older one,
  /// `api.mainnet-beta.solana.com`. vault-stat-service, where this rule was first held, measured
  /// both on 2026-11-11: each answered an array of that service's twelve subscribe requests with
  /// an array of twelve results, on a first connection and on the replay after a reconnect, while
  /// Helius' standard websocket, `mainnet.helius-rpc.com`, answered the same array with one parse
  /// error (-32700), so it is not here. A host is added when it has been measured, not when it is
  /// expected to work.
  static final Set<String> HOSTS_THAT_READ_A_BATCH = Set.of("api.mainnet.solana.com", "api.mainnet-beta.solana.com");

  private WebSocketBatching() {
  }

  /// Whether the host of `endpoint` is one of [#HOSTS_THAT_READ_A_BATCH]: the whole host, compared
  /// without regard to case, as a name is resolved. A host that only ends or begins with a known
  /// one is another host, and so is the host of an endpoint that carries a known one as its user
  /// information. A null endpoint, or one the URI parser gave no host, reads none.
  static boolean readsABatch(final URI endpoint) {
    final var host = host(endpoint);
    return host != null && HOSTS_THAT_READ_A_BATCH.contains(host.toLowerCase(Locale.ROOT));
  }

  private static String host(final URI endpoint) {
    return endpoint == null ? null : endpoint.getHost();
  }

  /// `prototype` itself, batching as its configuration set it when its endpoint reads a batch and
  /// not at all when it does not. Logs the decision at INFO, naming the host and never the
  /// endpoint, whose path or query may carry a key.
  static SolanaRpcWebsocket.Builder onlyWhereRead(final SolanaRpcWebsocket.Builder prototype) {
    final var endpoint = prototype.wsUri();
    final var host = host(endpoint);
    if (!readsABatch(endpoint)) {
      logger.log(INFO, "Websocket subscribe requests to {0} go out one per message: the host is not known to read a batch.", host);
      return prototype.subscribeBatchBytes(0);
    }
    final int cap = prototype.subscribeBatchBytes();
    if (cap == 0) {
      logger.log(INFO, "Websocket subscribe requests to {0} go out one per message: batching is configured off.", host);
    } else {
      logger.log(INFO, "Websocket subscribe requests to {0} go out in batches of up to {1} bytes a message.", host, Integer.toString(cap));
    }
    return prototype;
  }
}
