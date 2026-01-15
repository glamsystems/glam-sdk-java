package systems.glam.services.pricing;

import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import systems.glam.services.ServiceContext;
import systems.glam.services.integrations.IntegrationServiceContext;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public interface PriceVaultsExecutor extends Runnable {

  static CompletableFuture<PriceVaultsExecutor> create(final ServiceContext serviceContext,
                                                       final IntegrationServiceContext integContext,
                                                       final Path vaultStateDirectory,
                                                       final Path vaultTableDirectory,
                                                       final AccountFetcher accountFetcher) {


    return null;
  }

  void subscribe(final SolanaRpcWebsocket websocket);
}
