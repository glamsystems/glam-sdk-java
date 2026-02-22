package systems.glam.services.pricing;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.lookup.AddressLookupTable;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import systems.glam.services.integrations.IntegrationServiceContext;
import systems.glam.services.io.FileUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static java.lang.System.Logger.Level.WARNING;
import static software.sava.core.accounts.lookup.AddressLookupTable.LOOKUP_TABLE_META_SIZE;
import static systems.glam.services.pricing.GlamStateContextCacheImpl.logger;

public interface GlamStateContextCache extends Runnable {

  static GlamStateContextCache loadCache(final Duration fetchDelay,
                                         final IntegrationServiceContext integContext,
                                         final Path vaultTableDirectory) {
    final var cachedState = loadStateContextFromDisk(integContext);
    loadTables(cachedState, vaultTableDirectory);
    return new GlamStateContextCacheImpl(
        fetchDelay,
        integContext,
        vaultTableDirectory,
        cachedState
    );
  }

  private static void loadTables(final Map<PublicKey, VaultStateContext> priceServicesByState,
                                 final Path vaultTableDirectory) {
    if (Files.notExists(vaultTableDirectory)) {
      try {
        Files.createDirectories(vaultTableDirectory);
      } catch (final IOException ex) {
        throw new UncheckedIOException(ex);
      }
    } else {
      try (final var files = Files.list(vaultTableDirectory)) {
        files.parallel().forEach(tableFile -> {
          if (Files.isRegularFile(tableFile)) {
            try {
              final var tableKey = FileUtils.parseKey(tableFile);
              if (tableKey != null) {
                final byte[] data = Files.readAllBytes(tableFile);
                final var stateKey = PublicKey.readPubKey(data, LOOKUP_TABLE_META_SIZE);
                final var priceService = priceServicesByState.get(stateKey);
                if (priceService != null) {
                  final var addressLookupTable = AddressLookupTable.read(tableKey, data);
                  priceService.glamVaultTableUpdate(addressLookupTable);
                }
              }
            } catch (final Exception ex) {
              logger.log(WARNING, "Failed to load vault lookup table from " + tableFile, ex);
            }
          }
        });
      } catch (final IOException ex) {
        throw new UncheckedIOException(ex);
      }
    }
  }

  private static Map<PublicKey, VaultStateContext> loadStateContextFromDisk(final IntegrationServiceContext integContext) {
    final var stateAccountDirectory = integContext.serviceContext().glamMinStateAccountCacheDirectory();
    final var priceServicesByState = new ConcurrentHashMap<PublicKey, VaultStateContext>(1_024);
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
          if (Files.isRegularFile(stateFile)) {
            try {
              final var stateAccountKey = FileUtils.parseKey(stateFile);
              if (stateAccountKey != null) {
                final byte[] data = Files.readAllBytes(stateFile);
                final var priceService = VaultPriceService.createService(integContext, stateAccountKey, data);
                if (priceService != null) {
                  priceServicesByState.put(stateAccountKey, priceService);
                }
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

  int size();

  Stream<VaultStateContext> stream();

  void subscribe(final SolanaRpcWebsocket websocket);

  void acceptStateAccount(final VaultStateContext stateContext, final AccountInfo<byte[]> accountInfo);
}
