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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.System.Logger.Level.WARNING;

final class IntegLookupTableCacheImpl implements IntegLookupTableCache, AccountConsumer {

  static final System.Logger logger = System.getLogger(IntegLookupTableCache.class.getName());

  private final Path integrationTablesDirectory;
  private final ConcurrentHashMap<PublicKey, AddressLookupTable> integrationTables;
  private final AccountFetcher accountFetcher;

  IntegLookupTableCacheImpl(final Path integrationTablesDirectory,
                            final ConcurrentHashMap<PublicKey, AddressLookupTable> integrationTables,
                            final AccountFetcher accountFetcher) {
    this.integrationTablesDirectory = integrationTablesDirectory;
    this.integrationTables = integrationTables;
    this.accountFetcher = accountFetcher;
  }

  @Override
  public AddressLookupTable getTable(final PublicKey tableKey) {
    return integrationTables.get(tableKey);
  }

  @Override
  public void run() {
    accountFetcher.queue(integrationTables.keySet(), this);
  }

  @Override
  public void accept(final List<AccountInfo<byte[]>> accounts, final Map<PublicKey, AccountInfo<byte[]>> accountMap) {
    final var iterator = integrationTables.entrySet().iterator();
    for (Map.Entry<PublicKey, AddressLookupTable> entry; iterator.hasNext(); ) {
      entry = iterator.next();
      final var tableKey = entry.getKey();
      final var accountInfo = accountMap.get(tableKey);
      final byte[] data = accountInfo.data();
      final long deactivationSlot = ByteUtil.getInt64LE(data, AddressLookupTable.DEACTIVATION_SLOT_OFFSET);
      if (deactivationSlot != -1) {
        iterator.remove();
        final var fileName = IntegrationServiceContext.resolveFileName(integrationTablesDirectory, tableKey);
        try {
          Files.deleteIfExists(fileName);
        } catch (IOException e) {
          logger.log(WARNING, "Failed to delete integration lookup table data: " + fileName, e);
        }
      } else {
        final var addressLookupTable = AddressLookupTable.read(tableKey, data);
        final var previous = entry.getValue();
        if (addressLookupTable.numUniqueAccounts() > previous.numUniqueAccounts()) {
          integrationTables.put(tableKey, addressLookupTable);
          writeTableData(integrationTablesDirectory, accountInfo);
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
