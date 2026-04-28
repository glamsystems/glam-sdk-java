package systems.glam.sdk.idl.programs.glam.staging.loopscale.gen.types;

import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

import static software.sava.core.encoding.ByteUtil.getInt64LE;
import static software.sava.core.encoding.ByteUtil.putInt64LE;

public record MultiCollateralTermsUpdateParams(long apy, CollateralTermsIndices[] indices) implements SerDe {

  public static final int APY_OFFSET = 0;
  public static final int INDICES_OFFSET = 8;

  public static MultiCollateralTermsUpdateParams read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var apy = getInt64LE(_data, i);
    i += 8;
    final var indices = SerDeUtil.readVector(4, CollateralTermsIndices.class, CollateralTermsIndices::read, _data, i);
    return new MultiCollateralTermsUpdateParams(apy, indices);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    putInt64LE(_data, i, apy);
    i += 8;
    i += SerDeUtil.writeVector(4, indices, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return 8 + SerDeUtil.lenVector(4, indices);
  }
}
