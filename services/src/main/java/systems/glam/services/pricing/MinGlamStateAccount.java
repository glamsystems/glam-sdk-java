package systems.glam.services.pricing;

import software.sava.core.accounts.PublicKey;
import software.sava.core.encoding.ByteUtil;
import software.sava.idl.clients.core.gen.SerDeUtil;
import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.*;

import java.util.Arrays;

public record MinGlamStateAccount(long slot,
                                  byte[] assetBytes,
                                  PublicKey[] assets,
                                  byte[] externalPositionsBytes,
                                  PublicKey[] externalPositions) {

  public int numAccounts() {
    return assets.length + externalPositions.length;
  }

  public byte[] serialize(final PublicKey baseAssetMint, final int baseAssetDecimals) {
    final int len = Long.BYTES + 1 + 1 + 1 + assetBytes.length + 1 + externalPositionsBytes.length;
    final byte[] data = new byte[len];
    ByteUtil.putInt64LE(data, 0, slot);
    int i = Long.BYTES;
    data[i++] = (byte) Arrays.binarySearch(assets, baseAssetMint);
    data[i++] = (byte) baseAssetDecimals;
    data[i++] = (byte) assets.length;
    System.arraycopy(assetBytes, 0, data, i, assetBytes.length);
    i += assetBytes.length;
    data[i++] = (byte) externalPositionsBytes.length;
    System.arraycopy(externalPositionsBytes, 0, data, i, externalPositionsBytes.length);
    return data;
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

  private static MinGlamStateAccount createRecord(final long slot, final byte[] data) {
    final var assets = SerDeUtil.readPublicKeyVector(4, data, StateAccount.ASSETS_OFFSET);
    Arrays.sort(assets);
    int i = StateAccount.ASSETS_OFFSET + 4 + SerDeUtil.lenVector(4, assets);
    final byte[] assetBytes = Arrays.copyOfRange(data, StateAccount.ASSETS_OFFSET + 4, i);

    final int externalPositionOffset = externalPositionsOffset(data, i);

    final var externalPositions = SerDeUtil.readPublicKeyVector(4, data, externalPositionOffset);
    Arrays.sort(externalPositions);
    i += SerDeUtil.lenVector(4, externalPositions);
    final byte[] externalPositionsBytes = Arrays.copyOfRange(data, externalPositionOffset + 4, i);

    return new MinGlamStateAccount(slot, assetBytes, assets, externalPositionsBytes, externalPositions);
  }

  public static MinGlamStateAccount createRecord(final AccountInfo<byte[]> data) {
    return createRecord(data.context().slot(), data.data());
  }

  public MinGlamStateAccount createIfChanged(final long slot, final byte[] data) {
    final int numAssets = SerDeUtil.val(4, data, StateAccount.ASSETS_OFFSET);
    final int fromAssetsOffset = StateAccount.ASSETS_OFFSET + 4;

    final int toAssetsOffset = fromAssetsOffset + (numAssets * PublicKey.PUBLIC_KEY_LENGTH);
    int fromExternalPositionOffset = externalPositionsOffset(data, toAssetsOffset);
    final int numExternalPositions = SerDeUtil.val(4, data, fromExternalPositionOffset);
    fromExternalPositionOffset += 4;
    final int toExternalPositionOffset = fromExternalPositionOffset + (numExternalPositions * PublicKey.PUBLIC_KEY_LENGTH);

    final boolean assetsChanged = numAssets == this.assets.length && Arrays.equals(
        this.assetBytes, 0, this.assetBytes.length,
        data, fromAssetsOffset, toAssetsOffset
    );
    final boolean externalPositionsChanged = numExternalPositions == this.externalPositions.length && Arrays.equals(
        this.externalPositionsBytes, 0, numExternalPositions,
        data, fromExternalPositionOffset, toExternalPositionOffset
    );

    if (!assetsChanged && !externalPositionsChanged) {
      return null;
    }

    final byte[] assetBytes;
    final PublicKey[] assets;
    if (assetsChanged) {
      assets = new PublicKey[numAssets];
      SerDeUtil.readArray(assets, data, fromAssetsOffset);
      Arrays.sort(assets);
      assetBytes = Arrays.copyOfRange(data, fromAssetsOffset, toAssetsOffset);
    } else {
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

    return new MinGlamStateAccount(slot, assetBytes, assets, externalPositionsBytes, externalPositions);
  }

  @Override
  public boolean equals(final Object o) {
    //noinspection PatternVariableHidesField
    if (o instanceof MinGlamStateAccount(_, _, final var assets, _, final var externalPositions)) {
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
