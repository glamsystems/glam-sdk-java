package systems.glam.services.config;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.kms.core.signing.SigningService;
import software.sava.kms.core.signing.SigningServiceConfig;
import software.sava.rpc.json.http.client.SolanaRpcClient;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import software.sava.services.core.config.RemoteResourceConfig;
import software.sava.services.core.net.http.NotifyClient;
import software.sava.services.core.remote.call.Backoff;
import software.sava.services.core.remote.load_balance.LoadBalancer;
import software.sava.services.solana.config.ChainItemFormatter;
import software.sava.services.solana.epoch.EpochInfoService;
import software.sava.services.solana.epoch.EpochServiceConfig;
import software.sava.services.solana.remote.call.RpcCaller;
import software.sava.services.solana.transactions.*;
import software.sava.services.solana.websocket.WebSocketManager;
import systems.glam.sdk.GlamAccounts;
import systems.glam.services.LoopHeartbeat;
import systems.glam.services.ServiceContext;
import systems.glam.services.execution.ExecutionServiceContext;
import systems.glam.services.execution.InstructionProcessor;
import systems.glam.services.mints.MintCache;
import systems.glam.services.rpc.AccountFetcher;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.SequencedCollection;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public interface DelegateServiceConfig {

  PublicKey glamStateKey();

  SigningServiceConfig signingServiceConfig();

  SolanaAccounts solanaAccounts();

  ChainItemFormatter formatter();

  NotifyClient notifyClient();

  Path cacheDirectory();

  RpcCaller rpcCaller();

  LoadBalancer<SolanaRpcClient> sendClients();

  LoadBalancer<HeliusFeeProvider> feeProviders();

  RemoteResourceConfig websocketConfig();

  /// The cap, in UTF-8 bytes, on one batched subscribe message of a websocket built from
  /// [#createWebSocketPrototype(WebSocket.Builder)], where its endpoint is known to read a batch:
  /// one measured to answer a JSON-RPC array of subscribe requests with an array of results,
  /// which is the public Agave endpoint under its two names, `api.mainnet.solana.com` and
  /// `api.mainnet-beta.solana.com`. At any other endpoint the prototype sends one request per
  /// message, whatever this names; Helius' standard websocket, which answers an array with a
  /// parse error, is one of those. A host joins the known ones in this module's code, once it
  /// has been measured, and never by configuration. This answers what the configuration names:
  /// the cap a prototype was given is its own
  /// [subscribeBatchBytes()][SolanaRpcWebsocket.Builder#subscribeBatchBytes()].
  ///
  /// Where batching applies, sava-rpc sends the typed subscribe requests that are due together
  /// in one pass of its send loop as JSON-RPC arrays, one message per array where each request
  /// was a message of its own: what waits when a connection opens, a replay after a reconnect,
  /// and a burst made on an open connection. 0 turns batching off. The ceiling is 4,096, Agave's
  /// inbound message limit, and a configuration that does not name
  /// `websocketSubscribeBatchBytes` is parsed with that ceiling, so at a known endpoint batching
  /// is on unless it is turned off. The key sits beside the `websocket` section, not in it:
  /// inside, it fails a JSON parse and is ignored in properties.
  ///
  /// [SolanaRpcWebsocket.Builder#subscribeBatchBytes(int)] is the authority on what shares a
  /// message and on the two ways sava-rpc falls back to one request per message, neither of
  /// which covers every endpoint that is not Agave's: one that answers an array with a parse
  /// error is sent no array again by that websocket, but only after the refused message; one
  /// that drops an array, or closes on it, costs the connection that sent it, and every second
  /// connection after it; and one that reads an array and refuses its requests one by one starts
  /// neither fallback. That is why no array is offered to an endpoint nobody measured.
  int websocketSubscribeBatchBytes();

  /// How many subscribe requests sent one per message the websocket endpoint accepts on one
  /// connection before it closes that connection, for an endpoint known to count them; 0, which
  /// a configuration that does not name `websocketSingleSubscribeBudget` is parsed with,
  /// declares no such budget. It is a statement about the endpoint, read only while batching is
  /// on, so only at an endpoint known to read a batch, and declaring it gives up sava-rpc's
  /// one-connection fallback for a replay larger than the budget, which is then sent in arrays
  /// whatever its predecessor was lost to:
  /// [SolanaRpcWebsocket.Builder#singleSubscribeBudget(int)] says when that is the better trade.
  int websocketSingleSubscribeBudget();

  EpochServiceConfig epochServiceConfig();

  TxMonitorConfig txMonitorConfig();

  AccountFetcherConfig accountFetcherConfig();

  DefensivePollingConfig defensivePollingConfig();

  BigDecimal maxLamportPriorityFee();

  BigInteger warnFeePayerBalance();

  BigInteger minFeePayerBalance();

  Duration minCheckStateDelay();

  Duration maxCheckStateDelay();

  Backoff serviceBackoff();

  double defaultCuBudgetMultiplier();

  int maxTransactionRetries();

  SequencedCollection<String> hikariPropertiesFiles();

  /// A new sava-rpc websocket builder for the configured endpoint over `webSocketBuilder`, with
  /// the configured subscribe batching
  /// ([#websocketSubscribeBatchBytes()], [#websocketSingleSubscribeBudget()]) where the
  /// endpoint's host is one known to read a batch and with batching off, a cap of 0, at any
  /// other, and nothing else set, so its websockets subscribe at the commitment a sava-rpc
  /// builder starts with, FINALIZED. Each call logs its decision at INFO, naming the host and
  /// never the rest of the endpoint, where a provider's key travels. It is the prototype
  /// [#createWebSocketManager(HttpClient, Collection)] hands its manager, and what a service
  /// that builds its own manager, to add lifecycle handlers, starts from before it passes the
  /// builder to `WebSocketManager.createManager(Backoff, SolanaRpcWebsocket.Builder, Consumer)`;
  /// the two settings, and the rule that decides where they apply, reach no websocket built any
  /// other way. Every call answers a builder of its own: a manager consumes the one it is given,
  /// which must not be changed afterwards or shared with another manager. `webSocketBuilder` is
  /// kept, not copied, and sava-rpc sets its connect timeout and connects through it, so each
  /// call takes one of its own as well, a new `HttpClient.newWebSocketBuilder()`.
  ///
  /// @throws NullPointerException if this configuration has no websocket section
  SolanaRpcWebsocket.Builder createWebSocketPrototype(final WebSocket.Builder webSocketBuilder);

  /// A manager over [#createWebSocketPrototype(WebSocket.Builder)], so every websocket it
  /// creates batches its subscribe requests as that prototype does: as configured at an endpoint
  /// known to read a batch, and not at all at any other. The manager hands each new websocket to
  /// every consumer in `webSocketConsumers` before that websocket first connects, so the
  /// subscriptions they make wait for the connection and, where batching applies, go out as
  /// arrays when it opens.
  ///
  /// @throws NullPointerException if this configuration has no websocket section
  WebSocketManager createWebSocketManager(final HttpClient wsHttpClient,
                                          final Collection<Consumer<SolanaRpcWebsocket>> webSocketConsumers);

  /// The processor builds, signs and publishes SIMD-0385 v1 transactions paid by `serviceKey`: the
  /// compute unit limit and priority fee ride as ConfigValues, so the instructions it is handed must
  /// not invoke the ComputeBudget program.
  TransactionProcessor createTransactionProcessor(final ExecutorService taskExecutor,
                                                  final SigningService signingService,
                                                  final PublicKey serviceKey,
                                                  final WebSocketManager webSocketManager);

  TxMonitorService createTxMonitorService(final EpochInfoService epochInfoService,
                                          final WebSocketManager webSocketManager,
                                          final TransactionProcessor transactionProcessor);

  EpochInfoService createEpochInfoService();

  InstructionProcessor createInstructionProcessor(final TransactionProcessor transactionProcessor,
                                                  final InstructionService instructionService,
                                                  final GlamAccounts glamAccounts);

  ServiceContext createServiceContext(final ExecutorService taskExecutor,
                                      final PublicKey serviceKey,
                                      final GlamAccounts glamAccounts,
                                      final GlamAccounts glamStagingAccounts);

  MintCache createMintCache();

  ExecutionServiceContext createExecutionServiceContext(final ServiceContext serviceContext,
                                                        final EpochInfoService epochInfoService,
                                                        final InstructionProcessor instructionProcessor);

  AccountFetcher createAccountFetcher(final Set<PublicKey> alwaysFetch);

  /// The configured fetcher with a supervisor's `heartbeat` -- see
  /// [AccountFetcher#createFetcher(Duration, boolean, RpcCaller, Set, LoopHeartbeat)].
  AccountFetcher createAccountFetcher(final Set<PublicKey> alwaysFetch, final LoopHeartbeat heartbeat);
}
