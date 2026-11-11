package systems.glam.services.config;

import org.junit.jupiter.api.Test;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import systems.glam.services.tests.LogCapture;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Pins which websocket endpoints a delegate service's prototype batches its subscribe requests
/// at, and what `WebSocketBatching.onlyWhereRead` does to a prototype (owner decision,
/// 2026-11-11: batch at an endpoint known to support it, single requests at any other).
///
/// - Known means measured, and the public Agave endpoint is, under both of its names,
///   `api.mainnet.solana.com` and `api.mainnet-beta.solana.com`: by the whole host name, in any
///   case and with any port, path, query or scheme. Helius' standard websocket, which refused a
///   batch on 2026-11-11, is not, nor is a host that only ends with, begins with or contains a
///   known one, one that carries it as user information, or an endpoint with no host.
/// - At a known endpoint the prototype keeps the cap it arrives with, a cap of 0 included: the
///   configuration can still turn batching off there. At any other the cap becomes 0, whatever it
///   was. Either way the very builder handed in is answered, and nothing else of it changes.
/// - Each decision is logged once at INFO, naming the host and the reason and never the rest of
///   the endpoint, where a provider's key travels. A prototype that names no endpoint yet is
///   decided like any unknown one, and logged with no host.
final class WebSocketBatchingTests {

  private static final String LOGGER = WebSocketBatching.class.getName();

  private static SolanaRpcWebsocket.Builder prototype(final String endpoint, final int cap) {
    return SolanaRpcWebsocket.build().uri(URI.create(endpoint)).subscribeBatchBytes(cap).singleSubscribeBudget(41);
  }

  @Test
  void thePublicAgaveEndpointIsKnownByItsWholeHostName() {
    assertEquals(
        Set.of("api.mainnet.solana.com", "api.mainnet-beta.solana.com"),
        WebSocketBatching.HOSTS_THAT_READ_A_BATCH
    );
    for (final var endpoint : List.of(
        "wss://api.mainnet.solana.com",
        "wss://api.mainnet.solana.com:443/?key=batching-test",
        "wss://Api.Mainnet.Solana.Com",
        "wss://api.mainnet-beta.solana.com",
        "wss://api.mainnet-beta.solana.com/",
        "wss://api.mainnet-beta.solana.com:443/ws?key=batching-test",
        "wss://API.Mainnet-Beta.Solana.COM",
        "ws://api.mainnet-beta.solana.com:8900"
    )) {
      assertTrue(WebSocketBatching.readsABatch(URI.create(endpoint)), endpoint);
    }
  }

  @Test
  void anyOtherHostIsNotKnown() {
    for (final var endpoint : List.of(
        // measured on 2026-11-11 to answer a batch with a parse error
        "wss://mainnet.helius-rpc.com/?api-key=batching-test",
        // not measured
        "wss://atlas-mainnet.helius-rpc.com/?api-key=batching-test",
        "wss://api.devnet.solana.com",
        "wss://example.invalid/ws",
        // look-alikes of each known host: a suffix, a prefix, a parent, a child, and the known
        // host as user information
        "wss://api.mainnet-beta.solana.com.example.invalid",
        "wss://not-api.mainnet-beta.solana.com",
        "wss://mainnet-beta.solana.com",
        "wss://ws.api.mainnet-beta.solana.com",
        "wss://api.mainnet-beta.solana.com@example.invalid/",
        "wss://api.mainnet.solana.com.example.invalid",
        "wss://not-api.mainnet.solana.com",
        "wss://mainnet.solana.com",
        "wss://ws.api.mainnet.solana.com",
        "wss://api.mainnet.solana.com@example.invalid/",
        // a sibling cluster's public endpoint, not measured
        "wss://api.testnet.solana.com",
        // no host for the URI parser to give
        "wss:///ws"
    )) {
      assertFalse(WebSocketBatching.readsABatch(URI.create(endpoint)), endpoint);
    }
    assertFalse(WebSocketBatching.readsABatch(null));
  }

  @Test
  void aKnownEndpointKeepsItsConfiguredBatching() {
    final var prototype = prototype("wss://api.mainnet-beta.solana.com/?key=kept", 1_234);
    final var decision = "Websocket subscribe requests to api.mainnet-beta.solana.com go out in batches of up to 1234 bytes a message.";

    try (final var log = LogCapture.attach(LOGGER)) {
      final var answered = WebSocketBatching.onlyWhereRead(prototype);

      assertSame(prototype, answered);
      assertEquals(1_234, answered.subscribeBatchBytes());
      assertEquals(41, answered.singleSubscribeBudget());
      assertEquals(List.of(decision), log.messages());
      log.assertLogged(Level.INFO, decision);
    }
  }

  @Test
  void aKnownEndpointConfiguredOffStaysOff() {
    final var prototype = prototype("wss://api.mainnet-beta.solana.com", 0);
    final var decision = "Websocket subscribe requests to api.mainnet-beta.solana.com go out one per message: batching is configured off.";

    try (final var log = LogCapture.attach(LOGGER)) {
      final var answered = WebSocketBatching.onlyWhereRead(prototype);

      assertSame(prototype, answered);
      assertEquals(0, answered.subscribeBatchBytes());
      assertEquals(List.of(decision), log.messages());
      log.assertLogged(Level.INFO, decision);
    }
  }

  @Test
  void anEndpointNotKnownLosesItsConfiguredBatchingAndItsKeyIsNotLogged() {
    final var endpoint = URI.create("wss://mainnet.helius-rpc.com/?api-key=a-key-that-must-not-be-logged");
    final var prototype = prototype(endpoint.toString(), 4_096);
    final var decision = "Websocket subscribe requests to mainnet.helius-rpc.com go out one per message: the host is not known to read a batch.";

    try (final var log = LogCapture.attach(LOGGER)) {
      final var answered = WebSocketBatching.onlyWhereRead(prototype);

      assertSame(prototype, answered);
      assertEquals(0, answered.subscribeBatchBytes());
      // nothing else of the prototype is touched
      assertEquals(endpoint, answered.wsUri());
      assertEquals(41, answered.singleSubscribeBudget());
      // the whole record, so nothing of the endpoint but its host is in it
      assertEquals(List.of(decision), log.messages());
      log.assertLogged(Level.INFO, decision);
    }
  }

  /// A builder with no endpoint set answers a null URI. It reads no batch, like any endpoint not
  /// known to, and the decision is still made and logged rather than thrown: refusing a builder
  /// without an endpoint is sava-rpc's to do, when the manager first creates a websocket from it.
  @Test
  void aPrototypeWithoutAnEndpointIsDecidedLikeAnUnknownOne() {
    final var prototype = SolanaRpcWebsocket.build().subscribeBatchBytes(2_048);
    final var decision = "Websocket subscribe requests to null go out one per message: the host is not known to read a batch.";

    try (final var log = LogCapture.attach(LOGGER)) {
      final var answered = WebSocketBatching.onlyWhereRead(prototype);

      assertSame(prototype, answered);
      assertEquals(0, answered.subscribeBatchBytes());
      assertEquals(List.of(decision), log.messages());
      log.assertLogged(Level.INFO, decision);
    }
  }
}
