package systems.glam.services.integrations;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.lookup.AddressLookupTable;
import software.sava.core.encoding.ByteUtil;
import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.services.rpc.AccountConsumer;
import systems.glam.services.rpc.AccountFetcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import static java.lang.System.Logger.Level.WARNING;

final class IntegLookupTableCacheImpl implements IntegLookupTableCache, AccountConsumer {

  static final System.Logger logger = System.getLogger(IntegLookupTableCache.class.getName());

  private final long fetchDelayMillis;
  private final Path integrationTablesDirectory;
  private final ConcurrentMap<PublicKey, AddressLookupTable> integrationTables;
  private final AccountFetcher accountFetcher;

  IntegLookupTableCacheImpl(final Duration fetchDelay,
                            final Path integrationTablesDirectory,
                            final ConcurrentMap<PublicKey, AddressLookupTable> integrationTables,
                            final AccountFetcher accountFetcher) {
    this.fetchDelayMillis = fetchDelay.toMillis();
    this.integrationTablesDirectory = integrationTablesDirectory;
    this.integrationTables = integrationTables;
    this.accountFetcher = accountFetcher;
  }

  @Override
  public AddressLookupTable table(final PublicKey tableKey) {
    return integrationTables.get(tableKey);
  }

  @Override
  public void run() {
    try {
      for (; ; ) {
        accountFetcher.queueBatchable(List.copyOf(integrationTables.keySet()), this);
        //noinspection BusyWait
        Thread.sleep(fetchDelayMillis);
      }
    } catch (final InterruptedException e) {
      // exit
    } catch (final RuntimeException ex) {
      logger.log(WARNING, "Unexpected error fetching accounts.", ex);
    }
  }

  private void deleteTableFile(final PublicKey tableKey) {
    final var fileName = IntegrationServiceContext.resolveFileName(integrationTablesDirectory, tableKey);
    try {
      Files.deleteIfExists(fileName);
    } catch (IOException e) {
      logger.log(WARNING, "Failed to delete integration lookup table data: " + fileName, e);
    }
  }

  @Override
  public AddressLookupTable acceptTableAccount(final AccountInfo<byte[]> accountInfo) {
    final var tableKey = accountInfo.pubKey();
    final byte[] data = accountInfo.data();
    final long deactivationSlot = ByteUtil.getInt64LE(data, AddressLookupTable.DEACTIVATION_SLOT_OFFSET);
    if (deactivationSlot != -1) {
      integrationTables.remove(tableKey);
      return null;
    } else {
      final var addressLookupTable = AddressLookupTable.read(accountInfo.pubKey(), data);
      return integrationTables.merge(
          tableKey, addressLookupTable,
          (a, b) -> a.numUniqueAccounts() >= b.numUniqueAccounts() ? a : b
      );
    }
  }

  @Override
  public void accept(final List<AccountInfo<byte[]>> accounts, final Map<PublicKey, AccountInfo<byte[]>> accountMap) {
    final var iterator = integrationTables.entrySet().iterator();
    for (Map.Entry<PublicKey, AddressLookupTable> entry; iterator.hasNext(); ) {
      entry = iterator.next();
      final var tableKey = entry.getKey();
      final var accountInfo = accountMap.get(tableKey);
      if (AccountFetcher.isNull(accountInfo)) {
        // TODO: Invalidate all consumers of this table.
        iterator.remove();
        deleteTableFile(tableKey);
        continue;
      }
      final byte[] data = accountInfo.data();
      final long deactivationSlot = ByteUtil.getInt64LE(data, AddressLookupTable.DEACTIVATION_SLOT_OFFSET);
      if (deactivationSlot != -1) {
        // TODO: Invalidate all consumers of this table.
        iterator.remove();
        deleteTableFile(tableKey);
      } else {
        final var addressLookupTable = AddressLookupTable.read(tableKey, data);
        final var previous = entry.getValue();
        if (addressLookupTable.numUniqueAccounts() > previous.numUniqueAccounts()) {
          final var result = integrationTables.merge(
              tableKey, addressLookupTable,
              (a, b) -> a.numUniqueAccounts() >= b.numUniqueAccounts() ? a : b
          );
          if (result == addressLookupTable) {
            writeTableData(integrationTablesDirectory, accountInfo);
          }
        }
      }
    }
  }

  static void writeTableData(final Path directory, final AccountInfo<byte[]> accountInfo) {
    final var fileName = IntegrationServiceContext.resolveFileName(directory, accountInfo.pubKey());
    try {
      Files.write(fileName, accountInfo.data());
    } catch (final IOException e) {
      logger.log(WARNING, "Failed to write integration lookup table data: " + fileName, e);
    }
  }
}
