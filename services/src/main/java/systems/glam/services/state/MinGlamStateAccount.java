package systems.glam.services.state;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.encoding.ByteUtil;
import software.sava.idl.clients.core.gen.SerDeUtil;
import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.sdk.GlamEnv;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.*;

import java.util.Arrays;

public record MinGlamStateAccount(long slot,
                                  GlamEnv glamEnv,
                                  AccountType accountType,
                                  boolean enabled,
                                  int baseAssetIndex,
                                  int baseAssetDecimals,
                                  int baseAssetTokenProgram,
                                  byte[] assetBytes,
                                  PublicKey[] assets,
                                  byte[] externalPositionsBytes,
                                  PublicKey[] externalPositions) {

  public boolean disabled() {
    return !enabled;
  }

  public int numAssets() {
    return assets.length;
  }

  public int numAccounts() {
    return assets.length + externalPositions.length;
  }

  public PublicKey baseAssetMint() {
    return assets[baseAssetIndex];
  }

  public boolean doesNotContainsAsset(final PublicKey mint) {
    return Arrays.binarySearch(assets, mint) < 0;
  }

  public PublicKey tokenProgram(final SolanaAccounts solanaAccounts) {
    return baseAssetTokenProgram == 0
        ? solanaAccounts.tokenProgram()
        : solanaAccounts.token2022Program();
  }

  public byte[] serialize() {
    final int len = Long.BYTES + 1 + 1 + 1 + 1 + 1 + 1 + 1 + assetBytes.length + 1 + externalPositionsBytes.length;
    final byte[] data = new byte[len];
    ByteUtil.putInt64LE(data, 0, slot);
    int i = Long.BYTES;
    data[i++] = (byte) (glamEnv.ordinal() | 0b1000_0000);
    data[i++] = (byte) accountType.ordinal();
    data[i++] = (byte) (enabled ? 1 : 0);
    data[i++] = (byte) baseAssetIndex;
    data[i++] = (byte) baseAssetDecimals;
    data[i++] = (byte) baseAssetTokenProgram;
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
    final byte version = data[i++];
    final GlamEnv glamEnv;
    final AccountType accountType;
    if (version < 0) {
      glamEnv = GlamEnv.values()[version & 0b0111_1111];
      accountType = AccountType.values()[data[i++]];
    } else {
      glamEnv = GlamEnv.PRODUCTION;
      accountType = AccountType.values()[version];
    }
    final boolean enabled = data[i++] == 1;
    final var baseAssetMintIndex = data[i++] & 0xFF;
    final int baseAssetDecimals = data[i++] & 0xFF;
    final int baseAssetTokenProgram = data[i++] & 0xFF;
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
        glamEnv,
        accountType,
        enabled,
        baseAssetMintIndex, baseAssetDecimals, baseAssetTokenProgram,
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

  public static MinGlamStateAccount createRecord(final AccountInfo<byte[]> accountInfo) {
    final byte[] data = accountInfo.data();
    final var assets = SerDeUtil.readPublicKeyVector(4, data, StateAccount.ASSETS_OFFSET);
    Arrays.sort(assets);

    int i = StateAccount.ASSETS_OFFSET + SerDeUtil.lenVector(4, assets);
    final byte[] assetBytes = Arrays.copyOfRange(data, StateAccount.ASSETS_OFFSET + 4, i);

    i = externalPositionsOffset(data, i);
    final var externalPositions = SerDeUtil.readPublicKeyVector(4, data, i);
    Arrays.sort(externalPositions);
    i += 4; // 1295
    final byte[] externalPositionsBytes = Arrays.copyOfRange(data, i, i + (externalPositions.length * PublicKey.PUBLIC_KEY_LENGTH));

    final var accountType = AccountType.values()[data[StateAccount.ACCOUNT_TYPE_OFFSET] & 0xFF];
    final boolean enabled = data[StateAccount.ENABLED_OFFSET] == 1;
    final var baseAssetMint = PublicKey.readPubKey(data, StateAccount.BASE_ASSET_MINT_OFFSET);
    final int baseAssetIndex = Arrays.binarySearch(assets, baseAssetMint);
    final int baseAssetDecimals = data[StateAccount.BASE_ASSET_DECIMALS_OFFSET] & 0xFF;
    final int baseAssetTokenProgram = data[StateAccount.BASE_ASSET_TOKEN_PROGRAM_OFFSET] & 0xFF;

    return new MinGlamStateAccount(
        accountInfo.context().slot(),
        GlamEnv.from(accountInfo.owner()),
        accountType,
        enabled,
        baseAssetIndex, baseAssetDecimals, baseAssetTokenProgram,
        assetBytes, assets, externalPositionsBytes, externalPositions
    );
  }

  public MinGlamStateAccount createIfChanged(final AccountInfo<byte[]> accountInfo) {
    final long slot = accountInfo.context().slot();
    if (Long.compareUnsigned(slot, this.slot) <= 0) {
      return null;
    }
    final var stateKey = accountInfo.pubKey();
    final byte[] data = accountInfo.data();
    if (baseAssetDecimals != (data[StateAccount.BASE_ASSET_DECIMALS_OFFSET] & 0xFF)) {
      throw new IllegalStateException("Base asset decimals changed for state account " + stateKey);
    }
    final var expectedBaseAssetMint = baseAssetMint().toByteArray();
    if (!Arrays.equals(
        expectedBaseAssetMint, 0, PublicKey.PUBLIC_KEY_LENGTH,
        data, StateAccount.BASE_ASSET_MINT_OFFSET, StateAccount.BASE_ASSET_MINT_OFFSET + PublicKey.PUBLIC_KEY_LENGTH
    )) {
      throw new IllegalStateException("Base asset changed for state account " + stateKey);
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

    final boolean enabled = data[StateAccount.ENABLED_OFFSET] == 1;

    if (!assetsChanged && !externalPositionsChanged && (this.enabled == enabled)) {
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
        GlamEnv.from(accountInfo.owner()),
        this.accountType,
        enabled,
        baseAssetIndex, this.baseAssetDecimals, this.baseAssetTokenProgram,
        assetBytes, assets,
        externalPositionsBytes, externalPositions
    );
  }

  @Override
  public boolean equals(final Object o) {
    if (!(o instanceof MinGlamStateAccount(
        _,
        GlamEnv oGlamEnv,
        AccountType type,
        boolean oEnabled,
        int assetIndex, int assetDecimals, int assetTokenProgram,
        byte[] bytes, _,
        byte[] positionsBytes, _
    ))) return false;
    return glamEnv == oGlamEnv
        && enabled == oEnabled
        && baseAssetIndex == assetIndex
        && baseAssetDecimals == assetDecimals
        && baseAssetTokenProgram == assetTokenProgram
        && Arrays.equals(assetBytes, bytes)
        && accountType == type
        && Arrays.equals(externalPositionsBytes, positionsBytes);
  }

  @Override
  public int hashCode() {
    int result = accountType.hashCode();
    result = 31 * result + glamEnv.hashCode();
    result = 31 * result + baseAssetIndex;
    result = 31 * result + baseAssetDecimals;
    result = 31 * result + baseAssetTokenProgram;
    result = 31 * result + Arrays.hashCode(assetBytes);
    result = 31 * result + Arrays.hashCode(externalPositionsBytes);
    return result;
  }
}
