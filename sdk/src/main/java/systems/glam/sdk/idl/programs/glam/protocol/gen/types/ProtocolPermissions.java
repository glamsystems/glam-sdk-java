package systems.glam.sdk.idl.programs.glam.protocol.gen.types;

import software.sava.idl.clients.core.gen.SerDe;

import static software.sava.core.encoding.ByteUtil.getInt16LE;
import static software.sava.core.encoding.ByteUtil.getInt64LE;
import static software.sava.core.encoding.ByteUtil.putInt16LE;
import static software.sava.core.encoding.ByteUtil.putInt64LE;

/// Represents a delegate's permissions for a specific protocol
///
public record ProtocolPermissions(int protocolBitflag, long permissionsBitmask) implements SerDe {

  public static final int BYTES = 10;

  public static final int PROTOCOL_BITFLAG_OFFSET = 0;
  public static final int PERMISSIONS_BITMASK_OFFSET = 2;

  public static ProtocolPermissions read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var protocolBitflag = getInt16LE(_data, i);
    i += 2;
    final var permissionsBitmask = getInt64LE(_data, i);
    return new ProtocolPermissions(protocolBitflag, permissionsBitmask);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    putInt16LE(_data, i, protocolBitflag);
    i += 2;
    putInt64LE(_data, i, permissionsBitmask);
    i += 8;
    return i - _offset;
  }

  @Override
  public int l() {
    return BYTES;
  }
}
