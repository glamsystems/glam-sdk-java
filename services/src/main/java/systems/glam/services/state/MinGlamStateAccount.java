package systems.glam.services.state;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.encoding.ByteUtil;
import software.sava.idl.clients.core.gen.SerDeUtil;
import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.sdk.GlamEnv;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.*;

import java.util.Arrays;

import static software.sava.core.encoding.ByteUtil.getInt16LE;

public record MinGlamStateAccount(long slot,
                                  byte[] data,
                                  GlamEnv glamEnv,
                                  AccountType accountType,
                                  boolean enabled,
                                  int baseAssetIndex,
                                  int baseAssetDecimals,
                                  int baseAssetTokenProgram,
                                  PublicKey[] assets,
                                  ProtocolIntegration[] protocolIntegrations,
                                  PublicKey[] delegates,
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
    final byte[] withSlot = new byte[data.length + 8];
    System.arraycopy(data, 0, withSlot, 0, data.length);
    ByteUtil.putInt64LE(withSlot, data.length, slot);
    return withSlot;
  }

  private static int readIntegrationAcls(final byte[] data,
                                         int i,
                                         final ProtocolIntegration[] protocolIntegrations) {
    final int from = i;
    for (int j = 0; j < protocolIntegrations.length; j++) {
      final var integrationProgram = PublicKey.readPubKey(data, i);
      i += PublicKey.PUBLIC_KEY_LENGTH;
      final var protocolsBitmask = getInt16LE(data, i);
      i += 2;
      protocolIntegrations[j] = new ProtocolIntegration(integrationProgram, protocolsBitmask);
      final int numPolicies = SerDeUtil.val(4, data, i);
      i += 4;
      for (int k = 0; k < numPolicies; k++) {
        i += ProtocolPolicy.DATA_OFFSET;
        i += SerDeUtil.val(4, data, i);
        i += 4;
      }
    }
    Arrays.sort(protocolIntegrations);
    return i - from;
  }

  private static int delegateAclsOffset(final byte[] data, int i) {
    final int len = SerDeUtil.val(4, data, i);
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
    return i;
  }

  private static int readDelegateAcls(final byte[] data, int i, final PublicKey[] delegates) {
    for (int j = 0; j < delegates.length; ++j) {
      delegates[j] = PublicKey.readPubKey(data, i);
      i += DelegateAcl.INTEGRATION_PERMISSIONS_OFFSET;
      final int numIntegrationPermissions = SerDeUtil.val(4, data, i);
      i += 4; // IntegrationPermissions
      for (int k = 0; k < numIntegrationPermissions; ++k) {
        i += IntegrationPermissions.PROTOCOL_PERMISSIONS_OFFSET;
        final int numPermissions = SerDeUtil.val(4, data, i);
        i += 4;
        i += numPermissions * ProtocolPermissions.BYTES;
      }
      i += Long.BYTES; // expiresAt
    }
    return i;
  }

  private static int externalPositionsOffset(final byte[] data, int i) {
    final int len = SerDeUtil.val(4, data, i);
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

  public static MinGlamStateAccount deserialize(final GlamEnv env, final byte[] data) {
    final long slot = ByteUtil.getInt64LE(data, data.length - 8);
    return createRecord(env, data, slot);
  }

  public static MinGlamStateAccount createRecord(final AccountInfo<byte[]> accountInfo) {
    return createRecord(GlamEnv.from(accountInfo.owner()), accountInfo.data(), accountInfo.context().slot());
  }

  public static MinGlamStateAccount createRecord(final GlamEnv env, final byte[] data, final long slot) {
    final var assets = SerDeUtil.readPublicKeyVector(4, data, StateAccount.ASSETS_OFFSET);
    Arrays.sort(assets);

    int i = StateAccount.ASSETS_OFFSET + SerDeUtil.lenVector(4, assets);

    final int numIntegrations = SerDeUtil.val(4, data, i);
    i += 4;
    final var protocolIntegrations = new ProtocolIntegration[numIntegrations];
    final var protocolIntegrationBytes = readIntegrationAcls(data, i, protocolIntegrations);
    i += protocolIntegrationBytes;

    final int numDelegates = SerDeUtil.val(4, data, i);
    i += 4;
    final var delegates = new PublicKey[numDelegates];
    i = readDelegateAcls(data, i, delegates);

    final var externalPositions = SerDeUtil.readPublicKeyVector(4, data, i);
    Arrays.sort(externalPositions);

    final var accountType = AccountType.values()[data[StateAccount.ACCOUNT_TYPE_OFFSET] & 0xFF];
    final boolean enabled = data[StateAccount.ENABLED_OFFSET] == 1;
    final var baseAssetMint = PublicKey.readPubKey(data, StateAccount.BASE_ASSET_MINT_OFFSET);
    final int baseAssetIndex = Arrays.binarySearch(assets, baseAssetMint);
    final int baseAssetDecimals = data[StateAccount.BASE_ASSET_DECIMALS_OFFSET] & 0xFF;
    final int baseAssetTokenProgram = data[StateAccount.BASE_ASSET_TOKEN_PROGRAM_OFFSET] & 0xFF;

    return new MinGlamStateAccount(
        slot,
        data,
        env,
        accountType,
        enabled,
        baseAssetIndex, baseAssetDecimals, baseAssetTokenProgram,
        assets,
        protocolIntegrations,
        delegates,
        externalPositions
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
    final int oToAssetsOffset = fromAssetsOffset + (numAssets * PublicKey.PUBLIC_KEY_LENGTH);

    final int oToProtocolIntegrationBytes = delegateAclsOffset(data, oToAssetsOffset);

    final int oToDelegatesOffset = externalPositionsOffset(data, oToProtocolIntegrationBytes);
    final int numExternalPositions = SerDeUtil.val(4, data, oToDelegatesOffset);
    final int oFromExternalPositionOffset = oToDelegatesOffset + 4;
    final int oToExternalPositionOffset = oFromExternalPositionOffset + (numExternalPositions * PublicKey.PUBLIC_KEY_LENGTH);

    final int toAssetsOffset = fromAssetsOffset + (this.assets.length * PublicKey.PUBLIC_KEY_LENGTH);
    final boolean sameAssets = numAssets == this.assets.length && Arrays.equals(
        this.data, fromAssetsOffset, toAssetsOffset,
        data, fromAssetsOffset, oToAssetsOffset
    );

    final int toProtocolIntegrationBytes = delegateAclsOffset(this.data, toAssetsOffset);
    final boolean sameProtocolIntegrations = Arrays.equals(
        this.data, toAssetsOffset, toProtocolIntegrationBytes,
        data, oToAssetsOffset, oToProtocolIntegrationBytes
    );

    final int toDelegatesOffset = externalPositionsOffset(this.data, toProtocolIntegrationBytes);
    final boolean sameDelegates = Arrays.equals(
        this.data, toProtocolIntegrationBytes, toDelegatesOffset,
        data, oToProtocolIntegrationBytes, oToDelegatesOffset
    );

    final boolean sameExternalPositions;
    if (numExternalPositions == this.externalPositions.length) {
      final int fromExternalPositionOffset = toDelegatesOffset + 4;
      final int toExternalPositionOffset = fromExternalPositionOffset + (this.externalPositions.length * PublicKey.PUBLIC_KEY_LENGTH);
      sameExternalPositions = Arrays.equals(
          this.data, fromExternalPositionOffset, toExternalPositionOffset,
          data, oFromExternalPositionOffset, oToExternalPositionOffset
      );
    } else {
      sameExternalPositions = false;
    }

    final boolean enabled = data[StateAccount.ENABLED_OFFSET] == 1;

    if (sameAssets
        && sameProtocolIntegrations
        && sameDelegates
        && sameExternalPositions
        && (this.enabled == enabled)) {
      return null;
    }

    final int baseAssetIndex;
    final PublicKey[] assets;
    if (sameAssets) {
      baseAssetIndex = this.baseAssetIndex;
      assets = this.assets;
    } else {
      assets = new PublicKey[numAssets];
      SerDeUtil.readArray(assets, data, fromAssetsOffset);
      Arrays.sort(assets);
      baseAssetIndex = Arrays.binarySearch(assets, this.baseAssetMint());
    }

    final ProtocolIntegration[] protocolIntegrations;
    if (sameProtocolIntegrations) {
      protocolIntegrations = this.protocolIntegrations;
    } else {
      final int numIntegrations = SerDeUtil.val(4, data, oToAssetsOffset);
      protocolIntegrations = new ProtocolIntegration[numIntegrations];
      readIntegrationAcls(data, oToAssetsOffset + 4, protocolIntegrations);
    }

    final PublicKey[] delegates;
    if (sameDelegates) {
      delegates = this.delegates;
    } else {
      final int numDelegates = SerDeUtil.val(4, data, oToProtocolIntegrationBytes);
      delegates = new PublicKey[numDelegates];
      readDelegateAcls(data, oToProtocolIntegrationBytes + 4, delegates);
    }

    final PublicKey[] externalPositions;
    if (sameExternalPositions) {
      externalPositions = this.externalPositions;
    } else {
      externalPositions = new PublicKey[numExternalPositions];
      SerDeUtil.readArray(externalPositions, data, oFromExternalPositionOffset);
      Arrays.sort(externalPositions);
    }

    return new MinGlamStateAccount(
        slot,
        data,
        this.glamEnv,
        this.accountType,
        enabled,
        baseAssetIndex, this.baseAssetDecimals, this.baseAssetTokenProgram,
        assets,
        protocolIntegrations,
        delegates,
        externalPositions
    );
  }

  @Override
  public boolean equals(final Object o) {
    if (!(o instanceof MinGlamStateAccount(
        _, _,
        GlamEnv oGlamEnv,
        AccountType oAccountType,
        boolean oEnabled,
        int oBaseAssetIndex,
        int oBaseAssetDecimals,
        int oBaseAssetTokenProgram,
        PublicKey[] oAssets,
        ProtocolIntegration[] oProtocolIntegrations,
        PublicKey[] oDelegates,
        PublicKey[] oExternalPositions
    ))) return false;
    return accountType == oAccountType
        && glamEnv == oGlamEnv
        && enabled == oEnabled
        && baseAssetIndex == oBaseAssetIndex
        && baseAssetDecimals == oBaseAssetDecimals
        && baseAssetTokenProgram == oBaseAssetTokenProgram
        && Arrays.equals(this.assets, oAssets)
        && Arrays.equals(this.protocolIntegrations, oProtocolIntegrations)
        && Arrays.equals(this.delegates, oDelegates)
        && Arrays.equals(this.externalPositions, oExternalPositions);
  }

  @Override
  public int hashCode() {
    int result = glamEnv.hashCode();
    result = 31 * result + accountType.hashCode();
    result = 31 * result + Boolean.hashCode(enabled);
    result = 31 * result + baseAssetIndex;
    result = 31 * result + baseAssetDecimals;
    result = 31 * result + baseAssetTokenProgram;
    result = 31 * result + Arrays.hashCode(assets);
    result = 31 * result + Arrays.hashCode(protocolIntegrations);
    result = 31 * result + Arrays.hashCode(delegates);
    result = 31 * result + Arrays.hashCode(externalPositions);
    return result;
  }
}
