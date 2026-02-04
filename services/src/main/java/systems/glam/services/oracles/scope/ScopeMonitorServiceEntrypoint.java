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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.WARNING;
import static java.nio.file.StandardOpenOption.*;

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
    final Map<PublicKey, FeedContext> feedContextMap;
    try {
      feedContextMap = loadConfigurations(configurationsPath);
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
    } else if (!feedContextMap.keySet().containsAll(scopeConfigKeys)) {
      final var keyList = scopeConfigKeys.stream().filter(k -> !feedContextMap.containsKey(k)).toList();
      scopeConfigurationsFuture = rpcCaller.courteousCall(
          rpcClient -> rpcClient.getAccounts(keyList),
          "Scope Configuration accounts"
      );
    } else {
      scopeConfigurationsFuture = null;
    }

    final var reserveContextsFilePath = serviceConfig.reserveContextsFilePath();
    final var reserveContextMap = new ConcurrentHashMap<PublicKey, ReserveContext>();
    if (Files.exists(reserveContextsFilePath)) {
      try {
        MarketParser.parseReserves(Files.readAllBytes(reserveContextsFilePath), reserveContextMap);
      } catch (final IOException e) {
        logger.log(WARNING, "Failed to read reserve contexts file.", e);
        return null;
      }
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
        final var feedContext = FeedContext.createContext(accountInfo);
        feedContextMap.put(feedContext.configurationKey(), feedContext);
        feedContextMap.put(feedContext.oracleMappings(), feedContext);
        feedContextMap.put(feedContext.priceFeed(), feedContext);
        try {
          writeConfigurations(configurationsPath, feedContext.configurationKey(), feedContext.configurationData());
        } catch (final IOException e) {
          logger.log(WARNING, "Failed to persist Scope Configuration.", e);
          return null;
        }
      }
    }


    final var mappingsPath = serviceConfig.mappingsPath();
    final ConcurrentMap<PublicKey, MappingsContext> mappingsContextByPriceFeed;
    try {
      mappingsContextByPriceFeed = loadMappings(mappingsPath, feedContextMap);
    } catch (final IOException e) {
      logger.log(WARNING, "Failed to read mappings directory.", e);
      return null;
    }

    final var missingMappings = feedContextMap.values().stream().<PublicKey>mapMulti((configuration, downstream) -> {
      if (!mappingsContextByPriceFeed.containsKey(configuration.priceFeed())) {
        downstream.accept(configuration.oracleMappings());
      }
    }).toList();

    if (!missingMappings.isEmpty()) {
      final var mappingsFuture = rpcCaller.courteousCall(
          rpcClient -> rpcClient.getAccounts(missingMappings),
          "Oracle Mappings accounts"
      );

      for (final var accountInfo : mappingsFuture.join()) {
        if (accountInfo == null) {
          logger.log(WARNING, "Oracle Mappings account not found.");
          return null;
        }
        final byte[] data = accountInfo.data();
        if (!OracleMappings.DISCRIMINATOR.equals(data, 0) || data.length != OracleMappings.BYTES) {
          logger.log(ERROR, String.format(
                  "%s is not a valid Scope OracleMappings account.", accountInfo.pubKey()
              )
          );
          return null;
        }
        final var mappingsKey = accountInfo.pubKey();
        final var priceFeedContext = feedContextMap.get(mappingsKey);
        final var mappingsContext = MappingsContext.createContext(accountInfo);
        mappingsContextByPriceFeed.put(priceFeedContext.priceFeed(), mappingsContext);
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
        null, // TODO
        kaminoAccounts,
        serviceConfig.pollingDelay(),
        configurationsPath,
        mappingsPath,
        reserveContextsFilePath,
        feedContextMap,
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

  static void writeConfigurations(final Path configurationsPath,
                                  final PublicKey configurationKey,
                                  final byte[] data) throws IOException {
    final var filePath = FileUtils.resolveAccountPath(configurationsPath, configurationKey);
    Files.write(
        filePath,
        data.length > Configuration.PADDING_OFFSET ? Arrays.copyOfRange(data, 0, Configuration.PADDING_OFFSET) : data,
        CREATE, TRUNCATE_EXISTING, WRITE
    );
  }

  private static Map<PublicKey, FeedContext> loadConfigurations(final Path configurationsPath) throws IOException {
    final var configurations = new HashMap<PublicKey, FeedContext>();
    if (Files.exists(configurationsPath)) {
      try (final var paths = Files.list(configurationsPath)) {
        paths.forEach(path -> {
          final var accountData = FileUtils.readAccountData(path);
          if (accountData.isAccount(Configuration.DISCRIMINATOR, Configuration.BYTES)) {
            try {
              final var feedContext = accountData.read(FeedContext::createContext);
              configurations.put(feedContext.configurationKey(), feedContext);
              configurations.put(feedContext.priceFeed(), feedContext);
              configurations.put(feedContext.oracleMappings(), feedContext);
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

  private static ConcurrentMap<PublicKey, MappingsContext> loadMappings(final Path mappingsPath,
                                                                        final Map<PublicKey, FeedContext> feedContextMap) throws IOException {

    final var mappingsContextByPriceFeed = new ConcurrentHashMap<PublicKey, MappingsContext>();
    try (final var paths = Files.list(mappingsPath)) {
      paths.forEach(path -> {
        final var accountData = FileUtils.readAccountData(path);
        if (accountData.isAccount(OracleMappings.DISCRIMINATOR, OracleMappings.BYTES)) {
          try {
            final var mappings = accountData.read(OracleMappings::read);
            final var scopeEntries = ScopeReader.parseEntries(0, mappings);
            final var feedContext = feedContextMap.get(mappings._address());
            mappingsContextByPriceFeed.put(feedContext.priceFeed(), new MappingsContext(accountData.data(), scopeEntries));
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
