package systems.glam.services.oracles.scope;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.kamino.scope.gen.types.Configuration;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.services.solana.websocket.WebSocketManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.WARNING;
import static java.nio.file.StandardOpenOption.*;
import static systems.glam.services.oracles.scope.ScopeMonitorServiceImpl.logger;

public record ScopeMonitorServiceEntrypoint(ExecutorService executorService,
                                            WebSocketManager webSocketManager,
                                            ScopeMonitorService monitorService) implements Runnable {

  static void main() {
    try (final var executorService = Executors.newFixedThreadPool(1)) {
      try (final var taskExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
        try (final var httpClient = HttpClient.newBuilder().executor(taskExecutor).build()) {
          try (final var wsHttpClient = HttpClient.newBuilder().executor(taskExecutor).build()) {
            try {
              final var service = createService(executorService, taskExecutor, httpClient, wsHttpClient);
              if (service != null) {
                service.run();
              }
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
    final var accountsNeeded = ConcurrentHashMap.<PublicKey>newKeySet(100);

    final var configurationsPath = serviceConfig.configurationsPath();
    final var scopeConfigKeys = serviceConfig.scopeConfigurationKeys();
    final Map<PublicKey, Configuration> scopeConfigurations;
    try {
      scopeConfigurations = loadConfigurations(configurationsPath);
      accountsNeeded.addAll(scopeConfigKeys);
      for (final var configuration : scopeConfigurations.values()) {
        accountsNeeded.add(configuration._address());
        accountsNeeded.add(configuration.oracleMappings());
      }
    } catch (final IOException e) {
      logger.log(WARNING, String.format(
              "Error listing Scope Configurations directory [%s]. exiting.",
              configurationsPath.toAbsolutePath()
          ), e
      );
      return null;
    }

    final var rpcCaller = serviceConfig.rpcCaller();
    final var scopeConfigsFilter = List.of(
        Configuration.SIZE_FILTER,
        Configuration.DISCRIMINATOR_FILTER
    );
    final var kaminoAccounts = serviceConfig.kaminoAccounts();
    final CompletableFuture<List<AccountInfo<byte[]>>> scopeConfigurationsFuture;
    if (scopeConfigKeys.isEmpty()) {
      scopeConfigurationsFuture = rpcCaller.courteousCall(
          rpcClient -> rpcClient.getProgramAccounts(
              kaminoAccounts.scopePricesProgram(),
              scopeConfigsFilter
          ),
          "Scope Configuration accounts"
      );
    } else if (!scopeConfigurations.keySet().containsAll(scopeConfigKeys)) {
      final var keyList = scopeConfigKeys.stream().filter(k -> !scopeConfigurations.containsKey(k)).toList();
      scopeConfigurationsFuture = rpcCaller.courteousCall(
          rpcClient -> rpcClient.getAccounts(keyList),
          "Scope Configuration accounts"
      );
    } else {
      scopeConfigurationsFuture = null;
    }

    if (scopeConfigurationsFuture != null) {
      final var accounts = scopeConfigurationsFuture.join();
      for (final var accountInfo : accounts) {
        if (accountInfo == null) {
          continue;
        }
        final var configuration = Configuration.read(accountInfo.pubKey(), accountInfo.data());
        if (!Configuration.DISCRIMINATOR.equals(configuration.discriminator())
            || configuration.l() != Configuration.BYTES) {
          logger.log(ERROR, String.format("""
                  %s is not a valid Scope Configuration account.""", configuration._address().toBase58()
              )
          );
        }
        final var key = configuration._address();
        if (scopeConfigurations.putIfAbsent(key, configuration) == null) {
          accountsNeeded.add(key);
          accountsNeeded.add(configuration.oracleMappings());
          try {
            writeConfigurations(configurationsPath, configuration);
          } catch (final IOException e) {
            logger.log(WARNING, "Failed to persist Scope configuration.", e);
            return null;
          }
        }
      }
    }

    final var monitorService = ScopeMonitorService.createService(
        serviceConfig.notifyClient(),
        rpcCaller,
        kaminoAccounts,
        accountsNeeded,
        scopeConfigurations.values(),
        serviceConfig.pollingDelay(),
        configurationsPath,
        serviceConfig.mappingsPath(),
        serviceConfig.reserveContextsFilePath()
    );

    final var websocketConfig = serviceConfig.websocketConfig();
    final var webSocketManager = WebSocketManager.createManager(
        wsHttpClient,
        websocketConfig.endpoint(),
        websocketConfig.backoff(),
        monitorService::subscribe
    );
    webSocketManager.checkConnection();

    return new ScopeMonitorServiceEntrypoint(executorService, webSocketManager, monitorService);
  }

  static void writeConfigurations(final Path configurationsPath, final Configuration configuration) throws IOException {
    final var filePath = configurationsPath.resolve(configuration._address().toBase58() + ".dat");
    final byte[] data = new byte[Configuration.BYTES];
    configuration.write(data, 0);
    Files.write(
        filePath,
        data,
        CREATE, TRUNCATE_EXISTING, WRITE
    );
  }

  private static Map<PublicKey, Configuration> loadConfigurations(final Path configurationsPath) throws IOException {
    final var configurations = new HashMap<PublicKey, Configuration>();
    if (Files.exists(configurationsPath)) {
      try (final var paths = Files.list(configurationsPath)) {
        paths.forEach(path -> {
          final String name = path.getFileName().toString();
          if (name.endsWith(".dat")) {
            final byte[] data;
            try {
              data = Files.readAllBytes(path);
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            }
            try {
              if (data.length > 0) {
                final var key = name.substring(0, name.lastIndexOf('.'));
                final var configuration = Configuration.read(PublicKey.fromBase58Encoded(key), data);
                configurations.put(configuration._address(), configuration);
              }
            } catch (RuntimeException e) {
              logger.log(WARNING, "Failed to read/parse configuration file: " + path, e);
            }
          }
        });
      }
    } else {
      Files.createDirectories(configurationsPath);
    }
    return configurations;
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
