package systems.glam.services.integrations;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.lookup.AddressLookupTable;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.services.rpc.AccountFetcher;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static java.lang.System.Logger.Level.WARNING;
import static systems.glam.services.integrations.IntegLookupTableCacheImpl.logger;
import static systems.glam.services.integrations.IntegLookupTableCacheImpl.writeTableData;

public interface IntegLookupTableCache extends Runnable {

  static CompletableFuture<IntegLookupTableCache> createCache(final Path integrationTablesDirectory,
                                                              final Set<PublicKey> integrationTableKeys,
                                                              final RpcCaller rpcCaller,
                                                              final AccountFetcher accountFetcher) {
    final var integrationTables = new ConcurrentHashMap<PublicKey, AddressLookupTable>();

    try {
      Files.createDirectories(integrationTablesDirectory);
      try (final Stream<Path> files = Files.list(integrationTablesDirectory)) {
        files.filter(p -> p.getFileName().toString().endsWith(".dat"))
            .forEach(datFile -> {
              try {
                final var fileName = datFile.getFileName().toString();
                final var tableKeyStr = fileName.substring(0, fileName.length() - 4);
                final var tableKey = PublicKey.fromBase58Encoded(tableKeyStr);
                final byte[] data = Files.readAllBytes(datFile);
                final var table = AddressLookupTable.read(tableKey, data);
                integrationTables.put(tableKey, table);
              } catch (final IOException ex) {
                throw new UncheckedIOException(ex);
              }
            });
      }
    } catch (final IOException ex) {
      throw new UncheckedIOException(ex);
    }


    final var missingKeys = integrationTableKeys.stream().filter(k -> !integrationTables.containsKey(k)).toList();

    if (missingKeys.isEmpty()) {
      return CompletableFuture.completedFuture(
          new IntegLookupTableCacheImpl(integrationTablesDirectory, integrationTables, accountFetcher)
      );
    }

    return rpcCaller.courteousCall(
        rpcClient -> rpcClient.getAccounts(missingKeys),
        "rpcClient::getIntegrationTables"
    ).thenApply(accountInfoList -> {
      for (final var accountInfo : accountInfoList) {
        if (accountInfo != null && accountInfo.data() != null) {
          final var tableKey = accountInfo.pubKey();
          final var table = AddressLookupTable.read(tableKey, accountInfo.data());
          integrationTables.put(tableKey, table);
          writeTableData(integrationTablesDirectory, accountInfo);
        }
      }
      for (final var expectedKey : integrationTableKeys) {
        if (!integrationTables.containsKey(expectedKey)) {
          logger.log(WARNING, "Integration lookup table does not exist: " + expectedKey.toBase58());
        }
      }
      return new IntegLookupTableCacheImpl(integrationTablesDirectory, integrationTables, accountFetcher);
    });
  }

  AddressLookupTable getTable(final PublicKey tableKey);
}
