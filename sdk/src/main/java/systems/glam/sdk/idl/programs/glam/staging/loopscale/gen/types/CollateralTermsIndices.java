package systems.glam.sdk.idl.programs.glam.staging.loopscale.gen.types;

import software.sava.idl.clients.core.gen.SerDe;

public record CollateralTermsIndices(int collateralIndex, int durationIndex) implements SerDe {

  public static final int BYTES = 2;

  public static final int COLLATERAL_INDEX_OFFSET = 0;
  public static final int DURATION_INDEX_OFFSET = 1;

  public static CollateralTermsIndices read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var collateralIndex = _data[i] & 0xFF;
    ++i;
    final var durationIndex = _data[i] & 0xFF;
    return new CollateralTermsIndices(collateralIndex, durationIndex);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    _data[i] = (byte) collateralIndex;
    ++i;
    _data[i] = (byte) durationIndex;
    ++i;
    return i - _offset;
  }

  @Override
  public int l() {
    return BYTES;
  }
}
