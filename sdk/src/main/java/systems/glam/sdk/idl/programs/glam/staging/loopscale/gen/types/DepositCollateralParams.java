package systems.glam.sdk.idl.programs.glam.staging.loopscale.gen.types;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

import static software.sava.core.accounts.PublicKey.readPubKey;
import static software.sava.core.encoding.ByteUtil.getInt64LE;
import static software.sava.core.encoding.ByteUtil.putInt64LE;

public record DepositCollateralParams(long amount,
                                      int assetType,
                                      PublicKey assetIdentifier,
                                      byte[] assetIndexGuidance) implements SerDe {

  public static final int AMOUNT_OFFSET = 0;
  public static final int ASSET_TYPE_OFFSET = 8;
  public static final int ASSET_IDENTIFIER_OFFSET = 9;
  public static final int ASSET_INDEX_GUIDANCE_OFFSET = 41;

  public static DepositCollateralParams read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var amount = getInt64LE(_data, i);
    i += 8;
    final var assetType = _data[i] & 0xFF;
    ++i;
    final var assetIdentifier = readPubKey(_data, i);
    i += 32;
    final var assetIndexGuidance = SerDeUtil.readbyteVector(4, _data, i);
    return new DepositCollateralParams(amount,
                                       assetType,
                                       assetIdentifier,
                                       assetIndexGuidance);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    putInt64LE(_data, i, amount);
    i += 8;
    _data[i] = (byte) assetType;
    ++i;
    assetIdentifier.write(_data, i);
    i += 32;
    i += SerDeUtil.writeVector(4, assetIndexGuidance, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return 8 + 1 + 32 + SerDeUtil.lenVector(4, assetIndexGuidance);
  }
}
