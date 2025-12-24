package systems.glam.sdk.idl.programs.glam.protocol.gen.types;

import software.sava.idl.clients.core.gen.SerDe;

import static software.sava.core.encoding.ByteUtil.getInt16LE;
import static software.sava.core.encoding.ByteUtil.putInt16LE;

public record ProtocolFees(int baseFeeBps, int flowFeeBps) implements SerDe {

  public static final int BYTES = 4;

  public static ProtocolFees read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var baseFeeBps = getInt16LE(_data, i);
    i += 2;
    final var flowFeeBps = getInt16LE(_data, i);
    return new ProtocolFees(baseFeeBps, flowFeeBps);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    putInt16LE(_data, i, baseFeeBps);
    i += 2;
    putInt16LE(_data, i, flowFeeBps);
    i += 2;
    return i - _offset;
  }

  @Override
  public int l() {
    return BYTES;
  }
}
