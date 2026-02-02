package systems.glam.services.oracles.scope;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.kamino.KaminoAccounts;
import software.sava.idl.clients.kamino.scope.gen.types.Configuration;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import software.sava.services.core.net.http.NotifyClient;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.services.pricing.ScopeAggregateIndexes;
import systems.glam.services.rpc.AccountFetcher;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

public interface ScopeMonitorService extends ScopeAggregateIndexes, Runnable, Consumer<AccountInfo<byte[]>> {

  static ScopeMonitorService createService(final NotifyClient notifyClient,
                                           final RpcCaller rpcCaller,
                                           final AccountFetcher accountFetcher,
                                           final KaminoAccounts kaminoAccounts,
                                           final Duration pollingDelay,
                                           final Path configurationsPath,
                                           final Path mappingsPath,
                                           final Path reserveContextsFilePath,
                                           final Map<PublicKey, Configuration> scopeConfigurations,
                                           final ConcurrentMap<PublicKey, MappingsContext> mappingsContextByPriceFeed,
                                           final ConcurrentMap<PublicKey, ReserveContext> reserveContextMap) {
    return new ScopeMonitorServiceImpl(
        notifyClient,
        rpcCaller,
        accountFetcher,
        kaminoAccounts.kLendProgram(),
        kaminoAccounts.scopePricesProgram(),
        pollingDelay.toNanos(),
        configurationsPath,
        mappingsPath,
        reserveContextsFilePath,
        scopeConfigurations,
        mappingsContextByPriceFeed,
        reserveContextMap
    );
  }

  void subscribe(final SolanaRpcWebsocket websocket);
}
