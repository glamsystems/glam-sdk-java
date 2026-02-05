package systems.glam.services.oracles.scope;

import software.sava.services.solana.websocket.WebSocketManager;

import java.net.http.HttpClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.lang.System.Logger.Level.ERROR;

public record ScopeMonitorServiceEntrypoint(ExecutorService executorService,
                                            WebSocketManager webSocketManager,
                                            ScopeMonitorService monitorService) implements Runnable {

  private static final System.Logger logger = System.getLogger(ScopeMonitorServiceEntrypoint.class.getName());

  static void main() {
    try (final var executorService = Executors.newFixedThreadPool(1)) {
      try (final var taskExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
        try (final var httpClient = HttpClient.newBuilder().executor(taskExecutor).build()) {
          try (final var wsHttpClient = HttpClient.newBuilder().executor(taskExecutor).build()) {
            try {
              final var service = createService(executorService, taskExecutor, httpClient, wsHttpClient);
              service.run();
            } catch (final Throwable ex) {
              logger.log(ERROR, "Unexpected service failure.", ex);
            }
          }
        } finally {
          taskExecutor.shutdownNow();
        }
      } catch (final Throwable ex) {
        logger.log(ERROR, "Unexpected service failure.", ex);
      }
    }
  }

  private static ScopeMonitorServiceEntrypoint createService(final ExecutorService executorService,
                                                             final ExecutorService taskExecutor,
                                                             final HttpClient httpClient,
                                                             final HttpClient wsHttpClient) {
    final var serviceConfig = ScopeMonitorConfig.loadConfig(taskExecutor, httpClient);

    final var monitorServiceFuture = ScopeMonitorService.initService(
        serviceConfig.configurationsPath(),
        serviceConfig.rpcCaller(),
        null, //  TODO
        serviceConfig.notifyClient(),
        serviceConfig.kaminoAccounts(),
        serviceConfig.pollingDelay()
    );

    final var websocketConfig = serviceConfig.websocketConfig();

    final var monitorService = monitorServiceFuture.join();
    final var webSocketManager = WebSocketManager.createManager(
        wsHttpClient,
        websocketConfig.endpoint(),
        websocketConfig.backoff(),
        monitorService::subscribe
    );
    webSocketManager.checkConnection();

    return new ScopeMonitorServiceEntrypoint(executorService, webSocketManager, monitorService);
  }

  @Override
  public void run() {
    try {
      executorService.execute(monitorService);

      for (; ; ) {
        //noinspection BusyWait
        Thread.sleep(2_000);
        webSocketManager.checkConnection();
      }
    } catch (final InterruptedException e) {
      // exit.
    } finally {
      webSocketManager.close();
    }
  }
}
