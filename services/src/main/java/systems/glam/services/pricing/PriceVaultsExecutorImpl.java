package systems.glam.services.pricing;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.lookup.AddressLookupTable;
import software.sava.core.encoding.ByteUtil;
import software.sava.core.rpc.Filter;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateAccount;
import systems.glam.services.ServiceContext;
import systems.glam.services.fulfillment.MultiAssetPriceService;
import systems.glam.services.integrations.IntegrationServiceContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static java.lang.System.Logger.Level.WARNING;
import static software.sava.core.accounts.lookup.AddressLookupTable.LOOKUP_TABLE_META_SIZE;

final class PriceVaultsExecutorImpl implements PriceVaultsExecutor, Consumer<AccountInfo<byte[]>> {

  private static final System.Logger logger = System.getLogger(PriceVaultsExecutor.class.getName());

  private final ServiceContext serviceContext;
  private final IntegrationServiceContext integContext;
  private final Path vaultStateDirectory;
  private final List<Filter> stateFilters;
  private final Path vaultTableDirectory;
  private final List<Filter> tableFilters;
  private final AccountFetcher accountFetcher;
  private final Map<PublicKey, MultiAssetPriceService> priceServicesByState;
  private final Set<PublicKey> unsupportedVaults;

  PriceVaultsExecutorImpl(final ServiceContext serviceContext,
                          final IntegrationServiceContext integContext,
                          final Path vaultStateDirectory,
                          final Path vaultTableDirectory,
                          final AccountFetcher accountFetcher,
                          final Map<PublicKey, MultiAssetPriceService> priceServicesByState,
                          final Set<PublicKey> unsupportedVaults) {
    this.serviceContext = serviceContext;
    this.integContext = integContext;
    this.vaultStateDirectory = vaultStateDirectory;
    this.stateFilters = List.of(StateAccount.DISCRIMINATOR_FILTER);
    // 0: State Key
    // 1: Vault Key
    // 3: GLAM Config Key
    this.tableFilters = List.of(
        Filter.createMemCompFilter(
            LOOKUP_TABLE_META_SIZE + PublicKey.PUBLIC_KEY_LENGTH + PublicKey.PUBLIC_KEY_LENGTH,
            serviceContext.glamAccounts().configProgram()
        )
    );
    this.vaultTableDirectory = vaultTableDirectory;
    this.accountFetcher = accountFetcher;
    this.priceServicesByState = priceServicesByState;
    this.unsupportedVaults = unsupportedVaults;
  }

  @Override
  public void run() {
    for (; ; ) {

    }
  }


  @Override
  public void subscribe(final SolanaRpcWebsocket websocket) {
    final var solanaAccounts = serviceContext.solanaAccounts();
    final var glamAccounts = serviceContext.glamAccounts();
    websocket.programSubscribe(solanaAccounts.addressLookupTableProgram(), tableFilters, this);
    websocket.programSubscribe(glamAccounts.protocolProgram(), stateFilters, this);
  }

  @Override
  public void accept(final AccountInfo<byte[]> accountInfo) {
    final var programOwner = accountInfo.owner();
    final var accountKey = accountInfo.pubKey();
    if (programOwner.equals(serviceContext.glamAccounts().protocolProgram())) {
      final var priceService = priceServicesByState.get(accountKey);
      if (priceService == null) {
        if (!unsupportedVaults.contains(accountKey)) {
          // TODO: Create new service
        }
      } else if (!priceService.stateChange(accountInfo)) {
        unsupportedVaults.add(accountKey);
      }
    } else if (programOwner.equals(serviceContext.solanaAccounts().addressLookupTableProgram())) {
      final byte[] data = accountInfo.data();
      final var stateKey = PublicKey.readPubKey(data, LOOKUP_TABLE_META_SIZE);
      final var priceService = priceServicesByState.get(stateKey);
      if (priceService != null) {
        final long deactivationSlot = ByteUtil.getInt64LE(data, AddressLookupTable.DEACTIVATION_SLOT_OFFSET);
        if (deactivationSlot != -1) {
          final var addressLookupTable = AddressLookupTable.read(accountKey, data);
          priceService.glamVaultTableUpdate(addressLookupTable);
          writeAccountData(vaultTableDirectory, accountInfo, "Failed to write glam vault lookup table data: ");
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


  public static void writeAccountData(final Path directory,
                                      final AccountInfo<byte[]> accountInfo,
                                      final String errorLogContext) {
    final var fileName = IntegrationServiceContext.resolveFileName(directory, accountInfo.pubKey());
    try {
      Files.write(fileName, accountInfo.data());
    } catch (final IOException e) {
      logger.log(WARNING, errorLogContext + fileName, e);
    }
  }
}
