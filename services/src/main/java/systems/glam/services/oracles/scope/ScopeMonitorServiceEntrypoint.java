package systems.glam.services.oracles.scope;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.kamino.scope.entries.ScopeReader;
import software.sava.idl.clients.kamino.scope.gen.types.Configuration;
import software.sava.idl.clients.kamino.scope.gen.types.OracleMappings;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.services.solana.websocket.WebSocketManager;
import systems.glam.services.io.FileUtils;
import systems.glam.services.oracles.scope.parsers.MarketParser;

import java.io.IOException;
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

    final var configurationsPath = serviceConfig.configurationsPath();
    final var scopeConfigKeys = serviceConfig.scopeConfigurationKeys();
    final Map<PublicKey, Configuration> scopeConfigurations;
    try {
      scopeConfigurations = loadConfigurations(configurationsPath);
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

    final var reserveContextsFilePath = serviceConfig.reserveContextsFilePath();
    final var reserveContextMap = new ConcurrentHashMap<PublicKey, ReserveContext>();
    try {
      MarketParser.parseReserves(Files.readAllBytes(reserveContextsFilePath), reserveContextMap);
    } catch (final IOException e) {
      logger.log(WARNING, "Failed to read reserve contexts file.", e);
      return null;
    }

    if (scopeConfigurationsFuture != null) {
      final var accounts = scopeConfigurationsFuture.join();
      for (final var accountInfo : accounts) {
        if (accountInfo == null) {
          continue;
        }
        final byte[] data = accountInfo.data();
        if (!Configuration.DISCRIMINATOR.equals(data, 0) || data.length != Configuration.BYTES) {
          logger.log(ERROR, String.format(
                  "%s is not a valid Scope Configuration account.", accountInfo.pubKey()
              )
          );
          return null;
        }
        final var configuration = Configuration.read(accountInfo);
        final var key = configuration._address();
        if (scopeConfigurations.putIfAbsent(key, configuration) == null) {
          try {
            writeConfigurations(configurationsPath, configuration);
          } catch (final IOException e) {
            logger.log(WARNING, "Failed to persist Scope Configuration.", e);
            return null;
          }
        }
      }
    }


    final var mappingToFeedMap = HashMap.<PublicKey, PublicKey>newHashMap(scopeConfigurations.size());
    for (final var scopeConfiguration : scopeConfigurations.values()) {
      mappingToFeedMap.put(scopeConfiguration.oracleMappings(), scopeConfiguration.oraclePrices());
    }
    final var mappingsPath = serviceConfig.mappingsPath();
    final Map<PublicKey, MappingsContext> mappingsContextByPriceFeed;
    try {
      mappingsContextByPriceFeed = loadMappings(mappingsPath, mappingToFeedMap);
    } catch (final IOException e) {
      logger.log(WARNING, "Failed to read mappings directory.", e);
      return null;
    }

    final var missingMappings = scopeConfigurations.values().stream().<PublicKey>mapMulti((configuration, downstream) -> {
      if (!mappingsContextByPriceFeed.containsKey(configuration.oraclePrices())) {
        downstream.accept(configuration.oracleMappings());
      }
    }).toList();

    if (!missingMappings.isEmpty()) {
      final var mappingsFuture = rpcCaller.courteousCall(
          rpcClient -> rpcClient.getAccounts(missingMappings),
          "Oracle Mappings accounts"
      );

      for (final var accountInfo : mappingsFuture.join()) {
        final var mappingsKey = accountInfo.pubKey();
        final var priceFeed = mappingToFeedMap.get(mappingsKey);
        final var mappingsContext = MappingsContext.createContext(accountInfo);
        mappingsContextByPriceFeed.put(priceFeed, mappingsContext);
        final var path = FileUtils.resolveAccountPath(mappingsPath, mappingsKey);
        try {
          Files.write(path, mappingsContext.data(), CREATE, TRUNCATE_EXISTING, WRITE);
        } catch (final IOException e) {
          logger.log(WARNING, "Failed to persist Oracle Mappings.", e);
          return null;
        }
      }
    }

    final var monitorService = ScopeMonitorService.createService(
        serviceConfig.notifyClient(),
        rpcCaller,
        kaminoAccounts,
        serviceConfig.pollingDelay(),
        configurationsPath,
        mappingsPath,
        reserveContextsFilePath,
        scopeConfigurations,
        mappingsContextByPriceFeed,
        reserveContextMap
    );

    final var websocketConfig = serviceConfig.websocketConfig();

    final var webSocketManager = WebSocketManager.createManager(
        wsHttpClient,
        websocketConfig.endpoint(),
        websocketConfig.backoff(),
        scopeConfigKeys.isEmpty()
            ? monitorService::subscribe
            : websocket -> {
          for (final var key : scopeConfigKeys) {
            websocket.accountSubscribe(key, monitorService);
          }
        }
    );
    webSocketManager.checkConnection();

    return new ScopeMonitorServiceEntrypoint(executorService, webSocketManager, monitorService);
  }

  static void writeConfigurations(final Path configurationsPath, final Configuration configuration) throws IOException {
    final var filePath = FileUtils.resolveAccountPath(configurationsPath, configuration._address());
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
          final var accountData = FileUtils.readAccountData(path);
          if (accountData.isAccount(Configuration.DISCRIMINATOR, Configuration.BYTES)) {
            try {
              final var configuration = accountData.read(Configuration::read);
              configurations.put(configuration._address(), configuration);
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

  private static Map<PublicKey, MappingsContext> loadMappings(final Path mappingsPath,
                                                              final Map<PublicKey, PublicKey> mappingToFeedMap) throws IOException {
    final var mappingsContextByPriceFeed = new ConcurrentHashMap<PublicKey, MappingsContext>();
    try (final var paths = Files.list(mappingsPath)) {
      paths.forEach(path -> {
        final var accountData = FileUtils.readAccountData(path);
        if (accountData.isAccount(OracleMappings.DISCRIMINATOR, OracleMappings.BYTES)) {
          try {
            final var mappings = accountData.read(OracleMappings::read);
            final var scopeEntries = ScopeReader.parseEntries(0, mappings);
            final var priceFeed = mappingToFeedMap.get(mappings._address());
            mappingsContextByPriceFeed.put(priceFeed, new MappingsContext(accountData.data(), scopeEntries));
          } catch (final RuntimeException e) {
            logger.log(WARNING, "Failed to parse OracleMappings file: " + path, e);
            throw e;
          }
        }
      });
    }
    return mappingsContextByPriceFeed;
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
