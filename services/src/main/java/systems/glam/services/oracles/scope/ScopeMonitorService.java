package systems.glam.services.oracles.scope;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.kamino.KaminoAccounts;
import software.sava.idl.clients.kamino.scope.gen.types.Configuration;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import software.sava.services.core.net.http.NotifyClient;
import software.sava.services.solana.remote.call.RpcCaller;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

interface ScopeMonitorService extends Runnable, Consumer<AccountInfo<byte[]>> {

  static ScopeMonitorService createService(final NotifyClient notifyClient,
                                           final RpcCaller rpcCaller,
                                           final KaminoAccounts kaminoAccounts,
                                           final Duration pollingDelay,
                                           final Path configurationsPath,
                                           final Path mappingsPath,
                                           final Path reserveContextsFilePath,
                                           final Map<PublicKey, Configuration> scopeConfigurations,
                                           final Map<PublicKey, MappingsContext> mappingsContextByPriceFeed) {
    return new ScopeMonitorServiceImpl(
        notifyClient,
        rpcCaller,
        kaminoAccounts.kLendProgram(),
        kaminoAccounts.scopePricesProgram(),
        pollingDelay.toNanos(),
        configurationsPath,
        mappingsPath,
        reserveContextsFilePath,
        scopeConfigurations,
        mappingsContextByPriceFeed
    );
  }

  void subscribe(final SolanaRpcWebsocket websocket);
}
