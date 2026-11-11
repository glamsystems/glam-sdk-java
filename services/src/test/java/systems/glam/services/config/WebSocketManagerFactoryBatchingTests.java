package systems.glam.services.config;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import software.sava.services.solana.websocket.WebSocketManager;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/// Subscribe batching through [DelegateServiceConfig#createWebSocketManager], on the wire: the
/// factory's own manager, ravina's, creates sava-rpc's real websocket from the configuration's
/// prototype, over a scripted JDK transport, so what the transport is asked to send is what a
/// server would receive. Nothing is connected to: an endpoint is only a name here, and the name
/// decides whether the prototype batches (`WebSocketBatching`; owner decision, 2026-11-11: batch
/// at an endpoint known to read a batch, single requests at any other).
///
/// Everything asserted here happens on the test thread. The manager hands a new websocket to the
/// consumers and then connects it, inside the first `webSocket()` call; the transport opens the
/// connection from inside `buildAsync`, as the JDK does before it completes the handshake's
/// future, and sava-rpc sends what is waiting from inside that call. sava-rpc's own loop is a
/// real thread, which sends no text on a connection whose requests have all been sent once: it
/// re-sends nothing on the connection a request went out on, and a request left unanswered ends
/// that connection, seconds later, without another text message on it. The manager then
/// reconnects, from a scheduled wake off the test thread, through the same transport. So only
/// what the first handshake and the first connection carried is asserted, which is complete when
/// `webSocket()` returns, however late the test reads it, and each test closes its manager, which
/// stops sava-rpc's loop and cancels the manager's wake.
final class WebSocketManagerFactoryBatchingTests {

  /// The public Agave endpoint, which `WebSocketBatching` knows to read a batch.
  private static final String READS_A_BATCH = "wss://api.mainnet-beta.solana.com";
  /// Helius' standard websocket, which it does not: it answered an array with a parse error.
  private static final String READS_NONE = "wss://mainnet.helius-rpc.com/?api-key=factory-batching-test";

  private static final PublicKey KEY_A = PublicKey.fromBase58Encoded("7ubS3GccjhQY99AYNKXjNJqnXjaokEdfdV915xnCb96r");
  private static final PublicKey KEY_B = PublicKey.fromBase58Encoded("So11111111111111111111111111111111111111112");
  private static final PublicKey KEY_C = PublicKey.fromBase58Encoded("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA");

  private static final String SUBSCRIBE = "\"method\":\"accountSubscribe\"";

  /// Records the text sava-rpc writes. Thread-safe, since sava-rpc's loop thread may write too.
  private static final class ScriptedSocket implements WebSocket {

    private final List<String> sentText = new CopyOnWriteArrayList<>();
    private volatile boolean outputClosed;

    @Override
    public CompletableFuture<WebSocket> sendText(final CharSequence data, final boolean last) {
      sentText.add(data.toString());
      return CompletableFuture.completedFuture(this);
    }

    @Override
    public CompletableFuture<WebSocket> sendBinary(final ByteBuffer data, final boolean last) {
      return CompletableFuture.completedFuture(this);
    }

    @Override
    public CompletableFuture<WebSocket> sendPing(final ByteBuffer message) {
      return CompletableFuture.completedFuture(this);
    }

    @Override
    public CompletableFuture<WebSocket> sendPong(final ByteBuffer message) {
      return CompletableFuture.completedFuture(this);
    }

    @Override
    public CompletableFuture<WebSocket> sendClose(final int statusCode, final String reason) {
      outputClosed = true;
      return CompletableFuture.completedFuture(this);
    }

    @Override
    public void request(final long n) {
    }

    @Override
    public String getSubprotocol() {
      return "";
    }

    @Override
    public boolean isOutputClosed() {
      return outputClosed;
    }

    @Override
    public boolean isInputClosed() {
      return false;
    }

    @Override
    public void abort() {
      outputClosed = true;
    }
  }

  /// The JDK builder sava-rpc connects through. Every handshake opens at once: `onOpen` is
  /// delivered from inside `buildAsync`.
  private static final class ScriptedTransport implements WebSocket.Builder {

    private final List<URI> handshakes = new CopyOnWriteArrayList<>();
    private final List<ScriptedSocket> opened = new CopyOnWriteArrayList<>();

    @Override
    public WebSocket.Builder header(final String name, final String value) {
      return this;
    }

    @Override
    public WebSocket.Builder connectTimeout(final Duration timeout) {
      return this;
    }

    @Override
    public WebSocket.Builder subprotocols(final String mostPreferred, final String... lesserPreferred) {
      return this;
    }

    @Override
    public CompletableFuture<WebSocket> buildAsync(final URI uri, final WebSocket.Listener listener) {
      handshakes.add(uri);
      final var socket = new ScriptedSocket();
      opened.add(socket);
      listener.onOpen(socket);
      return CompletableFuture.completedFuture(socket);
    }

    /// The text sent on the first connection, which the tests read as soon as it has opened.
    private List<String> sentOnTheFirstConnection() {
      assertFalse(opened.isEmpty(), "no connection was opened");
      return List.copyOf(opened.getFirst().sentText);
    }
  }

  /// The websocket client the factory is handed: all it may ask of it is a websocket builder.
  private static final class ScriptedClient extends HttpClient {

    private final ScriptedTransport transport;

    private ScriptedClient(final ScriptedTransport transport) {
      this.transport = transport;
    }

    @Override
    public WebSocket.Builder newWebSocketBuilder() {
      return transport;
    }

    @Override
    public Optional<CookieHandler> cookieHandler() {
      throw new UnsupportedOperationException("cookieHandler");
    }

    @Override
    public Optional<Duration> connectTimeout() {
      throw new UnsupportedOperationException("connectTimeout");
    }

    @Override
    public Redirect followRedirects() {
      throw new UnsupportedOperationException("followRedirects");
    }

    @Override
    public Optional<ProxySelector> proxy() {
      throw new UnsupportedOperationException("proxy");
    }

    @Override
    public SSLContext sslContext() {
      throw new UnsupportedOperationException("sslContext");
    }

    @Override
    public SSLParameters sslParameters() {
      throw new UnsupportedOperationException("sslParameters");
    }

    @Override
    public Optional<Authenticator> authenticator() {
      throw new UnsupportedOperationException("authenticator");
    }

    @Override
    public Version version() {
      throw new UnsupportedOperationException("version");
    }

    @Override
    public Optional<Executor> executor() {
      throw new UnsupportedOperationException("executor");
    }

    @Override
    public <T> HttpResponse<T> send(final HttpRequest request, final HttpResponse.BodyHandler<T> responseBodyHandler) {
      throw new UnsupportedOperationException("send");
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(final HttpRequest request,
                                                            final HttpResponse.BodyHandler<T> responseBodyHandler) {
      throw new UnsupportedOperationException("sendAsync");
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(final HttpRequest request,
                                                            final HttpResponse.BodyHandler<T> responseBodyHandler,
                                                            final HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
      throw new UnsupportedOperationException("sendAsync");
    }
  }

  /// A configuration whose websocket section names `endpoint` and, when `subscribeBatchBytes` is
  /// given, that cap; with none, the cap is the parser's default.
  private static DelegateServiceConfig config(final String endpoint, final String subscribeBatchBytes) {
    final var properties = new Properties();
    properties.setProperty("websocket.endpoint", endpoint);
    if (subscribeBatchBytes != null) {
      properties.setProperty("websocketSubscribeBatchBytes", subscribeBatchBytes);
    }
    // the parser builds its RPC clients over this one, and this configuration names none
    try (final var httpClient = HttpClient.newHttpClient()) {
      final var parser = new BaseDelegateServiceConfig.ConfigParser(
          Executors.newVirtualThreadPerTaskExecutor(), httpClient
      );
      parser.parseProperties("", properties);
      return parser.createBaseConfig();
    }
  }

  /// Two consumers, as a service registers one per subscriber, making three subscriptions between
  /// them on each websocket the manager creates. Each records in `accepted` what its subscribe
  /// answered, for the test thread to assert: the manager catches whatever a consumer throws, an
  /// assertion's failure included, and answers null for a websocket whose consumer threw.
  private static List<Consumer<SolanaRpcWebsocket>> twoConsumers(final List<Boolean> accepted) {
    return List.of(
        webSocket -> subscribe(webSocket, accepted, KEY_A, KEY_B),
        webSocket -> subscribe(webSocket, accepted, KEY_C)
    );
  }

  private static void subscribe(final SolanaRpcWebsocket webSocket,
                                final List<Boolean> accepted,
                                final PublicKey... accounts) {
    for (final var account : accounts) {
      accepted.add(webSocket.accountSubscribe(account, _ -> {
      }));
    }
  }

  /// The managed websocket, which the first call creates, hands to the consumers and connects.
  private static void openTheWebsocket(final WebSocketManager manager, final List<Boolean> accepted) {
    assertNotNull(
        manager.webSocket(),
        "no websocket: its builder or a consumer threw, which the manager logs at WARNING"
    );
    assertEquals(List.of(true, true, true), accepted, "each first subscription to an account is accepted");
  }

  private static int count(final String text, final String part) {
    int count = 0;
    for (int from = text.indexOf(part); from >= 0; from = text.indexOf(part, from + part.length())) {
      ++count;
    }
    return count;
  }

  /// Building the manager creates no websocket: no consumer runs and nothing is connected before
  /// the first `webSocket()` or `checkConnection()` call, so a service may register its consumers
  /// after it has the manager and before it first asks for a connection.
  @Test
  void theFactorysManagerConnectsNothingBeforeItIsAskedForAWebsocket() {
    final var transport = new ScriptedTransport();
    final var accepted = new CopyOnWriteArrayList<Boolean>();

    final var manager = config(READS_A_BATCH, null)
        .createWebSocketManager(new ScriptedClient(transport), twoConsumers(accepted));
    try (manager) {
      assertFalse(manager.closed());
    }
    assertTrue(manager.closed());
    assertEquals(List.of(), transport.handshakes, "a manager closed unused connected nothing");
    assertEquals(List.of(), accepted, "and handed no websocket to a consumer");
  }

  /// At an endpoint known to read a batch, a configuration that names no cap batches: the
  /// subscriptions every consumer makes on the new websocket wait for its first connection and
  /// reach the server as one JSON-RPC array, at the endpoint and the commitment the prototype
  /// carries.
  @Test
  void theConsumersSubscriptionsReachTheServerAsOneArrayWhenTheWebsocketOpens() {
    final var transport = new ScriptedTransport();
    final var accepted = new CopyOnWriteArrayList<Boolean>();

    try (final var manager = config(READS_A_BATCH, null)
        .createWebSocketManager(new ScriptedClient(transport), twoConsumers(accepted))) {
      openTheWebsocket(manager, accepted);

      assertEquals(URI.create(READS_A_BATCH), transport.handshakes.getFirst());
      final var sent = transport.sentOnTheFirstConnection();
      assertEquals(1, sent.size(), "one message for the three subscriptions: " + sent);
      final var frame = sent.getFirst();
      assertTrue(frame.startsWith("[{") && frame.endsWith("}]"), "a JSON-RPC array: " + frame);
      assertEquals(3, count(frame, SUBSCRIBE), "requests in the array: " + frame);
      // in the order the consumers made them, the first consumer's ahead of the second's
      int from = 0;
      for (final var account : List.of(KEY_A, KEY_B, KEY_C)) {
        final int at = frame.indexOf(account.toBase58(), from);
        assertTrue(at >= 0, account.toBase58() + " in order in: " + frame);
        from = at + 1;
      }
      assertEquals(3, count(frame, "\"commitment\":\"finalized\""), "each at FINALIZED: " + frame);
    }
  }

  /// The control, and the off switch: the same endpoint, factory, consumers and transport with
  /// the cap configured to 0 send each subscription as a message of its own. 0 is also the cap
  /// sava-rpc's builder starts with, so what this holds is that a configured 0 replaces the
  /// parser's default.
  @Test
  void aCapOfZeroSendsEachSubscriptionAlone() {
    assertEachSubscriptionIsSentAlone(READS_A_BATCH, "0");
  }

  /// No array is ever offered to an endpoint not known to read a batch, Helius' here: each
  /// subscription goes as a message of its own under the parser's default cap, and under a cap
  /// the configuration names, which cannot turn batching on there.
  @Test
  void anEndpointNotKnownToReadABatchIsSentEachSubscriptionAloneWhateverIsConfigured() {
    assertEachSubscriptionIsSentAlone(READS_NONE, null);
    assertEachSubscriptionIsSentAlone(READS_NONE, "4096");
  }

  private static void assertEachSubscriptionIsSentAlone(final String endpoint, final String subscribeBatchBytes) {
    final var transport = new ScriptedTransport();
    final var accepted = new CopyOnWriteArrayList<Boolean>();

    try (final var manager = config(endpoint, subscribeBatchBytes)
        .createWebSocketManager(new ScriptedClient(transport), twoConsumers(accepted))) {
      openTheWebsocket(manager, accepted);

      assertEquals(URI.create(endpoint), transport.handshakes.getFirst());
      final var sent = transport.sentOnTheFirstConnection();
      final var accounts = List.of(KEY_A, KEY_B, KEY_C);
      assertEquals(accounts.size(), sent.size(), "one message per subscription: " + sent);
      for (int i = 0; i < accounts.size(); ++i) {
        final var frame = sent.get(i);
        assertTrue(frame.startsWith("{") && frame.endsWith("}"), "a plain request, not an array: " + frame);
        assertEquals(1, count(frame, SUBSCRIBE), "one request: " + frame);
        assertTrue(frame.contains(accounts.get(i).toBase58()), accounts.get(i).toBase58() + " in: " + frame);
      }
    }
  }
}
