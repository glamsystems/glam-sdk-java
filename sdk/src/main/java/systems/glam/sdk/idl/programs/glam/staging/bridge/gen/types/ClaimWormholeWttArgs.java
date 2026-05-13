package systems.glam.sdk.idl.programs.glam.staging.bridge.gen.types;

import software.sava.idl.clients.core.gen.SerDe;

import static software.sava.core.encoding.ByteUtil.getInt16LE;
import static software.sava.core.encoding.ByteUtil.putInt16LE;

public record ClaimWormholeWttArgs(int providerInstructionCount) implements SerDe {

  public static final int BYTES = 2;

  public static final int PROVIDER_INSTRUCTION_COUNT_OFFSET = 0;

  public static ClaimWormholeWttArgs read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    final var providerInstructionCount = getInt16LE(_data, _offset);
    return new ClaimWormholeWttArgs(providerInstructionCount);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    putInt16LE(_data, i, providerInstructionCount);
    i += 2;
    return i - _offset;
  }

  @Override
  public int l() {
    return BYTES;
  }
}
