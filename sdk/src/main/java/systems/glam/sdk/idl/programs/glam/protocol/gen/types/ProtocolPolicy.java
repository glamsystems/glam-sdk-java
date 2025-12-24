package systems.glam.sdk.idl.programs.glam.protocol.gen.types;

import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

import static software.sava.core.encoding.ByteUtil.getInt16LE;
import static software.sava.core.encoding.ByteUtil.putInt16LE;

/// Stores policy data for an integrated protocol.
/// Integration programs serialize/deserialize this data.
///
public record ProtocolPolicy(int protocolBitflag, byte[] data) implements SerDe {

  public static ProtocolPolicy read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var protocolBitflag = getInt16LE(_data, i);
    i += 2;
    final var data = SerDeUtil.readbyteVector(4, _data, i);
    return new ProtocolPolicy(protocolBitflag, data);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    putInt16LE(_data, i, protocolBitflag);
    i += 2;
    i += SerDeUtil.writeVector(4, data, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return 2 + SerDeUtil.lenVector(4, data);
  }
}
