package systems.glam.sdk.idl.programs.glam.protocol.gen.types;

import software.sava.core.borsh.Borsh;

import static software.sava.core.encoding.ByteUtil.getInt16LE;
import static software.sava.core.encoding.ByteUtil.putInt16LE;

public record PerformanceFee(int feeBps,
                             int hurdleRateBps,
                             HurdleType hurdleType) implements Borsh {

  public static final int BYTES = 5;

  public static PerformanceFee read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var feeBps = getInt16LE(_data, i);
    i += 2;
    final var hurdleRateBps = getInt16LE(_data, i);
    i += 2;
    final var hurdleType = HurdleType.read(_data, i);
    return new PerformanceFee(feeBps, hurdleRateBps, hurdleType);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    putInt16LE(_data, i, feeBps);
    i += 2;
    putInt16LE(_data, i, hurdleRateBps);
    i += 2;
    i += Borsh.write(hurdleType, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return BYTES;
  }
}
