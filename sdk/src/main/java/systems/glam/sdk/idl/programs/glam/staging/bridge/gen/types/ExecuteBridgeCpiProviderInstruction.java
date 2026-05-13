package systems.glam.sdk.idl.programs.glam.staging.bridge.gen.types;

import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

import static software.sava.core.encoding.ByteUtil.getInt16LE;
import static software.sava.core.encoding.ByteUtil.putInt16LE;

public record ExecuteBridgeCpiProviderInstruction(byte[] data, int accountCount) implements SerDe {

  public static final int DATA_OFFSET = 0;

  public static ExecuteBridgeCpiProviderInstruction read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var data = SerDeUtil.readbyteVector(4, _data, i);
    i += SerDeUtil.lenVector(4, data);
    final var accountCount = getInt16LE(_data, i);
    return new ExecuteBridgeCpiProviderInstruction(data, accountCount);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    i += SerDeUtil.writeVector(4, data, _data, i);
    putInt16LE(_data, i, accountCount);
    i += 2;
    return i - _offset;
  }

  @Override
  public int l() {
    return SerDeUtil.lenVector(4, data) + 2;
  }
}
