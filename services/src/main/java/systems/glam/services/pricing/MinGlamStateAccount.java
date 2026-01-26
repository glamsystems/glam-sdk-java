package systems.glam.services.pricing;

import software.sava.core.accounts.PublicKey;
import software.sava.core.encoding.ByteUtil;
import software.sava.idl.clients.core.gen.SerDeUtil;
import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.*;

import java.util.Arrays;

public record MinGlamStateAccount(long slot,
                                  int baseAssetIndex,
                                  int baseAssetDecimals,
                                  byte[] assetBytes,
                                  PublicKey[] assets,
                                  byte[] externalPositionsBytes,
                                  PublicKey[] externalPositions) {

  public int numAccounts() {
    return assets.length + externalPositions.length;
  }

  public PublicKey baseAssetMint() {
    return assets[baseAssetIndex];
  }

  public byte[] serialize() {
    final int len = Long.BYTES + 1 + 1 + 1 + assetBytes.length + 1 + externalPositionsBytes.length;
    final byte[] data = new byte[len];
    ByteUtil.putInt64LE(data, 0, slot);
    int i = Long.BYTES;
    data[i++] = (byte) baseAssetIndex;
    data[i++] = (byte) baseAssetDecimals;
    data[i++] = (byte) assets.length;
    System.arraycopy(assetBytes, 0, data, i, assetBytes.length);
    i += assetBytes.length;
    data[i++] = (byte) externalPositions.length;
    System.arraycopy(externalPositionsBytes, 0, data, i, externalPositionsBytes.length);
    return data;
  }

  public static MinGlamStateAccount deserialize(final byte[] data) {
    final long slot = ByteUtil.getInt64LE(data, 0);
    int i = Long.BYTES;
    final var baseAssetMintIndex = data[i++] & 0xFF;
    final int baseAssetDecimals = data[i++] & 0xFF;
    final var assets = SerDeUtil.readPublicKeyVector(1, data, i++);
    Arrays.sort(assets);
    final int to = i + (assets.length * PublicKey.PUBLIC_KEY_LENGTH);
    final byte[] assetBytes = Arrays.copyOfRange(data, i, to);
    i = to;
    final var externalPositions = SerDeUtil.readPublicKeyVector(1, data, i++);
    Arrays.sort(externalPositions);
    final byte[] externalPositionsBytes = Arrays.copyOfRange(data, i, data.length);
    return new MinGlamStateAccount(
        slot,
        baseAssetMintIndex, baseAssetDecimals,
        assetBytes, assets,
        externalPositionsBytes, externalPositions
    );
  }

  private static int externalPositionsOffset(final byte[] data, int i) {
    int len = SerDeUtil.val(4, data, i);
    i += 4; // IntegrationAcl
    for (int j = 0; j < len; ++j) {
      i += IntegrationAcl.PROTOCOL_POLICIES_OFFSET;
      final int numPolicies = SerDeUtil.val(4, data, i);
      i += 4; // ProtocolPolicy
      for (int k = 0; k < numPolicies; ++k) {
        i += ProtocolPolicy.DATA_OFFSET;
        i += SerDeUtil.val(4, data, i);
        i += 4;
      }
    }

    len = SerDeUtil.val(4, data, i);
    i += 4; // DelegateAcl
    for (int j = 0; j < len; ++j) {
      i += DelegateAcl.INTEGRATION_PERMISSIONS_OFFSET;
      final int numIntegrationPermissions = SerDeUtil.val(4, data, i);
      i += 4; // IntegrationPermissions
      for (int k = 0; k < numIntegrationPermissions; ++k) {
        i += IntegrationPermissions.PROTOCOL_PERMISSIONS_OFFSET;
        final int numPermissions = SerDeUtil.val(4, data, i);
        i += 4;
        for (int l = 0; l < numPermissions; ++l) {
          i += ProtocolPermissions.BYTES;
        }
      }
      i += Long.BYTES; // expiresAt
    }

    return i;
  }

  public static MinGlamStateAccount createRecord(final long slot, final byte[] data) {
    final var assets = SerDeUtil.readPublicKeyVector(4, data, StateAccount.ASSETS_OFFSET);
    Arrays.sort(assets);

    int i = StateAccount.ASSETS_OFFSET + SerDeUtil.lenVector(4, assets);
    final byte[] assetBytes = Arrays.copyOfRange(data, StateAccount.ASSETS_OFFSET + 4, i);

    i = externalPositionsOffset(data, i);
    final var externalPositions = SerDeUtil.readPublicKeyVector(4, data, i);
    Arrays.sort(externalPositions);
    i += 4; // 1295
    final byte[] externalPositionsBytes = Arrays.copyOfRange(data, i, i + (externalPositions.length * PublicKey.PUBLIC_KEY_LENGTH));

    final var baseAssetMint = PublicKey.readPubKey(data, StateAccount.BASE_ASSET_MINT_OFFSET);
    final int baseAssetIndex = Arrays.binarySearch(assets, baseAssetMint);
    final int baseAssetDecimals = data[StateAccount.BASE_ASSET_DECIMALS_OFFSET] & 0xFF;

    return new MinGlamStateAccount(
        slot,
        baseAssetIndex, baseAssetDecimals,
        assetBytes, assets, externalPositionsBytes, externalPositions
    );
  }

  public static MinGlamStateAccount createRecord(final AccountInfo<byte[]> data) {
    return createRecord(data.context().slot(), data.data());
  }

  public MinGlamStateAccount createIfChanged(final long slot, final byte[] data) {
    if (Long.compareUnsigned(slot, this.slot) <= 0) {
      return null;
    }
    final int numAssets = SerDeUtil.val(4, data, StateAccount.ASSETS_OFFSET);
    final int fromAssetsOffset = StateAccount.ASSETS_OFFSET + 4;

    final int toAssetsOffset = fromAssetsOffset + (numAssets * PublicKey.PUBLIC_KEY_LENGTH);
    int fromExternalPositionOffset = externalPositionsOffset(data, toAssetsOffset);
    final int numExternalPositions = SerDeUtil.val(4, data, fromExternalPositionOffset);
    fromExternalPositionOffset += 4; // 1295
    final int toExternalPositionOffset = fromExternalPositionOffset + (numExternalPositions * PublicKey.PUBLIC_KEY_LENGTH);

    final boolean assetsChanged = numAssets != this.assets.length || !Arrays.equals(
        this.assetBytes, 0, this.assetBytes.length,
        data, fromAssetsOffset, toAssetsOffset
    );
    final boolean externalPositionsChanged = numExternalPositions != this.externalPositions.length || !Arrays.equals(
        this.externalPositionsBytes, 0, this.externalPositionsBytes.length,
        data, fromExternalPositionOffset, toExternalPositionOffset
    );

    if (!assetsChanged && !externalPositionsChanged) {
      return null;
    }

    final int baseAssetIndex;
    final byte[] assetBytes;
    final PublicKey[] assets;
    if (assetsChanged) {
      assets = new PublicKey[numAssets];
      SerDeUtil.readArray(assets, data, fromAssetsOffset);
      Arrays.sort(assets);
      baseAssetIndex = Arrays.binarySearch(assets, this.baseAssetMint());
      assetBytes = Arrays.copyOfRange(data, fromAssetsOffset, toAssetsOffset);
    } else {
      baseAssetIndex = this.baseAssetIndex;
      assetBytes = this.assetBytes;
      assets = this.assets;
    }

    final byte[] externalPositionsBytes;
    final PublicKey[] externalPositions;
    if (externalPositionsChanged) {
      externalPositions = new PublicKey[numExternalPositions];
      SerDeUtil.readArray(externalPositions, data, fromExternalPositionOffset);
      Arrays.sort(externalPositions);
      externalPositionsBytes = Arrays.copyOfRange(data, fromExternalPositionOffset, toExternalPositionOffset);
    } else {
      externalPositionsBytes = this.externalPositionsBytes;
      externalPositions = this.externalPositions;
    }

    return new MinGlamStateAccount(
        slot,
        baseAssetIndex, baseAssetDecimals,
        assetBytes, assets,
        externalPositionsBytes, externalPositions
    );
  }

  @Override
  public boolean equals(final Object o) {
    //noinspection PatternVariableHidesField
    if (o instanceof MinGlamStateAccount(_, _, _, _, final var assets, _, final var externalPositions)) {
      return Arrays.equals(this.assets, assets) && Arrays.equals(this.externalPositions, externalPositions);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    int result = Arrays.hashCode(assets);
    result = 31 * result + Arrays.hashCode(externalPositions);
    return result;
  }
}
