package systems.glam.sdk.idl.programs.glam.staging.loopscale.gen.types;

import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

import static software.sava.core.encoding.ByteUtil.getInt64LE;
import static software.sava.core.encoding.ByteUtil.putInt64LE;

public record ExpectedLoanValues(long expectedApy, int[] expectedLqt) implements SerDe {

  public static final int BYTES = 28;
  public static final int EXPECTED_LQT_LEN = 5;

  public static final int EXPECTED_APY_OFFSET = 0;
  public static final int EXPECTED_LQT_OFFSET = 8;

  public static ExpectedLoanValues read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var expectedApy = getInt64LE(_data, i);
    i += 8;
    final var expectedLqt = new int[5];
    SerDeUtil.readArray(expectedLqt, _data, i);
    return new ExpectedLoanValues(expectedApy, expectedLqt);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    putInt64LE(_data, i, expectedApy);
    i += 8;
    i += SerDeUtil.writeArrayChecked(expectedLqt, 5, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return BYTES;
  }
}
