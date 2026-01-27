package systems.glam.services.pricing;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.lookup.AddressLookupTable;
import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.services.DelegateService;
import systems.glam.services.integrations.IntegrationServiceContext;
import systems.glam.services.state.MinGlamStateAccount;

import java.util.HashSet;

public interface VaultPriceService extends DelegateService {

  static VaultPriceService createService(final IntegrationServiceContext integContext,
                                         final PublicKey stateAccountKey,
                                         final MinGlamStateAccount minGlamStateAccount) {
    final var serviceContext = integContext.serviceContext();
    final var glamClient = GlamAccountClient.createClient(serviceContext.serviceKey(), stateAccountKey);
    final var mintPDA = glamClient.vaultAccounts().mintPDA().publicKey();
    final var accountsNeeded = HashSet.<PublicKey>newHashSet((3 + (minGlamStateAccount.numAccounts() << 2)));
    accountsNeeded.add(stateAccountKey);
    accountsNeeded.add(mintPDA);
    for (final var asset : minGlamStateAccount.assets()) {
      if (integContext.mintContext(asset) == null) {
        accountsNeeded.add(asset);
      }
    }
    for (final var externalPosition : minGlamStateAccount.externalPositions()) {
      accountsNeeded.add(externalPosition);
    }

    return new MultiAssetPriceService(
        integContext,
        mintPDA,
        glamClient,
        minGlamStateAccount,
        accountsNeeded
    );
  }

  static VaultPriceService createService(final IntegrationServiceContext integContext,
                                         final AccountInfo<byte[]> stateAccountInfo) {
    final var minStateAccount = MinGlamStateAccount.createRecord(stateAccountInfo);
    return createService(integContext, stateAccountInfo.pubKey(), minStateAccount);
  }

  static VaultPriceService createService(final IntegrationServiceContext integContext,
                                         final PublicKey stateAccountKey,
                                         final byte[] minStateData) {
    final var minStateAccount = MinGlamStateAccount.deserialize(minStateData);
    return createService(integContext, stateAccountKey, minStateAccount);
  }

  void init();

  boolean stateChange(final AccountInfo<byte[]> account);

  void removeTable(final PublicKey tableKey);

  void glamVaultTableUpdate(final AddressLookupTable addressLookupTable);
}
