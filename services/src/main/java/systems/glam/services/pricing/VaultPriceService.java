package systems.glam.services.pricing;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.lookup.AddressLookupTable;
import software.sava.core.encoding.ByteUtil;
import software.sava.idl.clients.core.gen.SerDeUtil;
import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateAccount;
import systems.glam.services.DelegateService;
import systems.glam.services.integrations.IntegrationServiceContext;

import java.util.Arrays;
import java.util.HashSet;

public interface VaultPriceService extends DelegateService {

  static VaultPriceService createService(final IntegrationServiceContext integContext,
                                         final PublicKey stateAccountKey,
                                         final PublicKey baseAssetMint,
                                         final int baseAssetDecimals,
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
        integContext, mintPDA,
        baseAssetMint, baseAssetDecimals, glamClient, minGlamStateAccount,
        accountsNeeded
    );
  }

  static VaultPriceService createService(final IntegrationServiceContext integContext,
                                         final AccountInfo<byte[]> stateAccountInfo) {
    final byte[] data = stateAccountInfo.data();
    final var baseAssetMint = PublicKey.readPubKey(data, StateAccount.BASE_ASSET_MINT_OFFSET);
    final int baseAssetDecimals = data[StateAccount.BASE_ASSET_DECIMALS_OFFSET] & 0xFF;
    final var minStateAccount = MinGlamStateAccount.createRecord(stateAccountInfo);
    return createService(integContext, stateAccountInfo.pubKey(), baseAssetMint, baseAssetDecimals, minStateAccount);
  }

  static VaultPriceService createService(final IntegrationServiceContext integContext,
                                         final PublicKey stateAccountKey,
                                         final byte[] data) {
    final long slot = ByteUtil.getInt64LE(data, 0);
    int i = Long.BYTES;
    final var baseAssetMintIndex = data[i++] & 0xFF;
    final int baseAssetDecimals = data[i++] & 0xFF;
    final var assets = SerDeUtil.readPublicKeyVector(1, data, i++);
    Arrays.sort(assets);
    int to = i + (assets.length * PublicKey.PUBLIC_KEY_LENGTH);
    final byte[] assetBytes = Arrays.copyOfRange(data, i, to);
    i = to;
    final var externalPositions = SerDeUtil.readPublicKeyVector(1, data, i++);
    Arrays.sort(externalPositions);
    final byte[] externalPositionsBytes = Arrays.copyOfRange(data, i, data.length);
    final var minStateAccount = new MinGlamStateAccount(slot, assetBytes, assets, externalPositionsBytes, externalPositions);
    return createService(integContext, stateAccountKey, assets[baseAssetMintIndex], baseAssetDecimals, minStateAccount);
  }

  void init();

  boolean stateChange(final AccountInfo<byte[]> account);

  void removeTable(final PublicKey tableKey);

  void glamVaultTableUpdate(final AddressLookupTable addressLookupTable);
}
