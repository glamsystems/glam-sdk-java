package systems.glam.services.pricing;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.lookup.AddressLookupTable;
import software.sava.core.encoding.ByteUtil;
import software.sava.core.rpc.Filter;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateAccount;
import systems.glam.services.integrations.IntegrationServiceContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.lang.System.Logger.Level.WARNING;
import static software.sava.core.accounts.lookup.AddressLookupTable.LOOKUP_TABLE_META_SIZE;

final class GlamStateContextCacheImpl implements GlamStateContextCache, Consumer<AccountInfo<byte[]>> {

  static final System.Logger logger = System.getLogger(GlamStateContextCache.class.getName());

  private final long fetchDelayMillis;
  private final PublicKey protocolProgram;
  private final PublicKey addressLookupTableProgram;
  private final IntegrationServiceContext integContext;
  private final List<Filter> stateFilters;
  private final Path vaultTableDirectory;
  private final List<Filter> tableFilters;
  private final Map<PublicKey, VaultStateContext> priceServicesByState;
  private final Set<PublicKey> unsupportedVaults;

  GlamStateContextCacheImpl(final Duration fetchDelay,
                            final IntegrationServiceContext integContext,
                            final Path vaultTableDirectory,
                            final Map<PublicKey, VaultStateContext> priceServicesByState) {
    this.fetchDelayMillis = fetchDelay.toNanos();
    this.integContext = integContext;
    final var serviceContext = integContext.serviceContext();
    final var glamAccounts = serviceContext.glamAccounts();
    this.protocolProgram = glamAccounts.protocolProgram();
    final var solanaAccounts = serviceContext.solanaAccounts();
    this.addressLookupTableProgram = solanaAccounts.addressLookupTableProgram();
    // 0: State Key
    // 1: Vault Key
    // 3: GLAM Config Key
    this.tableFilters = List.of(
        Filter.createMemCompFilter(
            LOOKUP_TABLE_META_SIZE + PublicKey.PUBLIC_KEY_LENGTH + PublicKey.PUBLIC_KEY_LENGTH,
            glamAccounts.configProgram()
        )
    );
    this.stateFilters = List.of(StateAccount.DISCRIMINATOR_FILTER);
    this.vaultTableDirectory = vaultTableDirectory;
    this.priceServicesByState = priceServicesByState;
    this.unsupportedVaults = new ConcurrentSkipListSet<>();
  }

  @Override
  public Stream<VaultStateContext> stream() {
    return priceServicesByState.values().stream();
  }

  @Override
  public void run() {
    try {
      final var rpcCaller = integContext.rpcCaller();
      for (; ; ) { // Defensive discovery of new Glam Vaults & Tables.
        final var glamStateAccounts = rpcCaller.courteousGet(
            rpcClient -> rpcClient.getProgramAccounts(protocolProgram, stateFilters),
            "rpcClient::getGlamStateAccounts"
        );
        glamStateAccounts.parallelStream().forEach(this::acceptStateAccount);

        final var addressLookupTableAccounts = rpcCaller.courteousGet(
            rpcClient -> rpcClient.getProgramAccounts(addressLookupTableProgram, tableFilters),
            "rpcClient::getAddressLookupTableAccounts"
        );
        addressLookupTableAccounts.parallelStream().forEach(this::acceptTable);

        //noinspection BusyWait
        Thread.sleep(fetchDelayMillis);
      }

    } catch (final InterruptedException e) {
      // exit
    } catch (final RuntimeException ex) {
      logger.log(WARNING, "Unexpected error fetching accounts.", ex);
    }
    // TODO:
    // * Establish default schedule and overrides per vault.
    // * Refresh view of each vault N minutes before next scheduled pricing.
    //   * Fetch highest AUM last so they are freshest.
    //   * Consider tracking change frequency and fetch those last.
    // * Price highest AUM vaults first on schedule as concurrently as possible.
  }

  @Override
  public void subscribe(final SolanaRpcWebsocket websocket) {
    websocket.programSubscribe(protocolProgram, stateFilters, this);
    websocket.programSubscribe(addressLookupTableProgram, tableFilters, this);
  }

  @Override
  public void accept(final AccountInfo<byte[]> accountInfo) {
    final var programOwner = accountInfo.owner();
    if (programOwner.equals(protocolProgram) && StateAccount.DISCRIMINATOR.equals(accountInfo.data(), 0)) {
      acceptStateAccount(accountInfo);
    } else if (programOwner.equals(addressLookupTableProgram)) {
      acceptTable(accountInfo);
    } else {
      logger.log(WARNING, String.format(
              "Unexpected Account [key=%s] [owner=%s]",
              accountInfo.pubKey(), programOwner
          )
      );
    }
  }

  private void acceptStateAccount(final AccountInfo<byte[]> accountInfo) {
    final var accountKey = accountInfo.pubKey();
    var stateContext = priceServicesByState.get(accountKey);
    if (stateContext == null) {
      if (!unsupportedVaults.contains(accountKey)) {
        stateContext = VaultPriceService.createService(integContext, accountInfo);
        if (stateContext == null) {
          unsupportedVaults.add(accountKey);
        } else if (priceServicesByState.putIfAbsent(accountKey, stateContext) == null) {
          stateContext.init();
        }
      }
    } else {
      acceptStateAccount(stateContext, accountInfo);
    }
  }

  @Override
  public void acceptStateAccount(final VaultStateContext stateContext, final AccountInfo<byte[]> accountInfo) {
    if (!stateContext.stateChange(accountInfo)) {
      final var accountKey = accountInfo.pubKey();
      unsupportedVaults.add(accountKey);
      priceServicesByState.remove(accountKey);
    }
  }

  private void acceptTable(final AccountInfo<byte[]> accountInfo) {
    final byte[] data = accountInfo.data();
    final var stateKey = PublicKey.readPubKey(data, LOOKUP_TABLE_META_SIZE);
    final var priceService = priceServicesByState.get(stateKey);
    if (priceService != null) {
      final var accountKey = accountInfo.pubKey();
      final long deactivationSlot = ByteUtil.getInt64LE(data, AddressLookupTable.DEACTIVATION_SLOT_OFFSET);
      if (deactivationSlot != -1) {
        final var addressLookupTable = AddressLookupTable.read(accountKey, data);
        priceService.glamVaultTableUpdate(addressLookupTable);
        final var fileName = IntegrationServiceContext.resolveFileName(vaultTableDirectory, accountInfo.pubKey());
        try {
          Files.write(fileName, accountInfo.data());
        } catch (final IOException e) {
          logger.log(WARNING, "Failed to write glam vault lookup table data: " + fileName, e);
        }
      } else {
        priceService.removeTable(accountKey);
        final var fileName = IntegrationServiceContext.resolveFileName(vaultTableDirectory, accountInfo.pubKey());
        try {
          Files.deleteIfExists(fileName);
        } catch (IOException e) {
          logger.log(WARNING, "Failed to delete glam vault lookup table data: " + fileName, e);
        }
      }
    }
  }
}
