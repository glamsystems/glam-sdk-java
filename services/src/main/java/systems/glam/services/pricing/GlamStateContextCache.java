package systems.glam.services.pricing;

import software.sava.core.accounts.PublicKey;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import systems.glam.services.integrations.IntegrationServiceContext;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static java.lang.System.Logger.Level.WARNING;
import static systems.glam.services.io.FileUtils.ACCOUNT_FILE_EXTENSION;
import static systems.glam.services.pricing.GlamStateContextCacheImpl.logger;

public interface GlamStateContextCache extends Runnable {

  static GlamStateContextCache loadCache(final Duration fetchDelay,
                                         final IntegrationServiceContext integContext,
                                         final Path vaultTableDirectory) {
    final var cachedState = loadStateContextFromDisk(integContext);
    return new GlamStateContextCacheImpl(
        fetchDelay,
        integContext,
        vaultTableDirectory,
        cachedState
    );
  }

  private static Map<PublicKey, VaultStateContext> loadStateContextFromDisk(final IntegrationServiceContext integContext) {
    final var stateAccountDirectory = integContext.serviceContext().glamMinStateAccountCacheDirectory();
    final var priceServicesByState = new ConcurrentHashMap<PublicKey, VaultStateContext>();
    if (Files.notExists(stateAccountDirectory)) {
      try {
        Files.createDirectories(stateAccountDirectory);
      } catch (final IOException ex) {
        throw new UncheckedIOException(ex);
      }
      return priceServicesByState;
    } else {
      try (final var files = Files.list(stateAccountDirectory)) {
        files.parallel().forEach(stateFile -> {
          if (Files.isRegularFile(stateFile) && stateFile.getFileName().toString().endsWith(ACCOUNT_FILE_EXTENSION)) {
            try {
              final byte[] data = Files.readAllBytes(stateFile);
              final var fileName = stateFile.getFileName().toString();
              final var keyString = fileName.substring(0, fileName.length() - ACCOUNT_FILE_EXTENSION.length());
              final var stateAccountKey = PublicKey.fromBase58Encoded(keyString);
              final var priceService = VaultPriceService.createService(integContext, stateAccountKey, data);
              if (priceService != null) {
                priceServicesByState.put(stateAccountKey, priceService);
              }
            } catch (final Exception ex) {
              logger.log(WARNING, "Failed to load StateAccount from " + stateFile, ex);
            }
          }
        });
        return priceServicesByState;
      } catch (final IOException ex) {
        throw new UncheckedIOException(ex);
      }
    }
  }

  Stream<VaultStateContext> stream();

  void subscribe(final SolanaRpcWebsocket websocket);

  void acceptStateAccount(final VaultStateContext stateContext, final AccountInfo<byte[]> accountInfo);
}
