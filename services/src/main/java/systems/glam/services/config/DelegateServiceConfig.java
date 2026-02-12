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
import software.sava.services.solana.alt.LookupTableCache;
import software.sava.services.solana.alt.TableCacheConfig;
import software.sava.services.solana.config.ChainItemFormatter;
import software.sava.services.solana.epoch.EpochInfoService;
import software.sava.services.solana.epoch.EpochServiceConfig;
import software.sava.services.solana.remote.call.RpcCaller;
import software.sava.services.solana.transactions.*;
import software.sava.services.solana.websocket.WebSocketManager;
import systems.glam.sdk.GlamAccounts;
import systems.glam.services.ServiceContext;
import systems.glam.services.execution.ExecutionServiceContext;
import systems.glam.services.db.DatasourceConfig;
import systems.glam.services.execution.InstructionProcessor;
import systems.glam.services.mints.MintCache;
import systems.glam.services.rpc.AccountFetcher;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
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

  TableCacheConfig tableCacheConfig();

  RpcCaller rpcCaller();

  LoadBalancer<SolanaRpcClient> sendClients();

  LoadBalancer<HeliusFeeProvider> feeProviders();

  RemoteResourceConfig websocketConfig();

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

  DatasourceConfig datasourceConfig();

  WebSocketManager createWebSocketManager(final HttpClient wsHttpClient,
                                          final Collection<Consumer<SolanaRpcWebsocket>> webSocketConsumers);

  LookupTableCache createLookupTableCache(final ExecutorService taskExecutor);

  TransactionProcessor createTransactionProcessor(final ExecutorService taskExecutor,
                                                  final SigningService signingService,
                                                  final LookupTableCache tableCache,
                                                  final PublicKey serviceKey,
                                                  final WebSocketManager webSocketManager);

  TxMonitorService createTxMonitorService(final EpochInfoService epochInfoService,
                                          final WebSocketManager webSocketManager,
                                          final TransactionProcessor transactionProcessor);

  EpochInfoService createEpochInfoService();

  InstructionProcessor createInstructionProcessor(final TransactionProcessor transactionProcessor,
                                                  final InstructionService instructionService);

  ServiceContext createServiceContext(final ExecutorService taskExecutor,
                                      final PublicKey serviceKey,
                                      final GlamAccounts glamAccounts);

  MintCache createMintCache();

  ExecutionServiceContext createExecutionServiceContext(final ServiceContext serviceContext,
                                                        final EpochInfoService epochInfoService,
                                                        final InstructionProcessor instructionProcessor);

  AccountFetcher createAccountFetcher(final Set<PublicKey> alwaysFetch);
}
