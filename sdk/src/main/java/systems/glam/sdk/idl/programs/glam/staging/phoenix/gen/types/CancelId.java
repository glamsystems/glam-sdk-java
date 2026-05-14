package systems.glam.sdk.idl.programs.glam.staging.phoenix.gen.types;

import software.sava.idl.clients.core.gen.SerDe;

import static software.sava.core.encoding.ByteUtil.getInt32LE;
import static software.sava.core.encoding.ByteUtil.putInt32LE;

public record CancelId(int nodePointer, FifoOrderId orderId) implements SerDe {

  public static final int BYTES = 20;

  public static final int NODE_POINTER_OFFSET = 0;
  public static final int ORDER_ID_OFFSET = 4;

  public static CancelId read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var nodePointer = getInt32LE(_data, i);
    i += 4;
    final var orderId = FifoOrderId.read(_data, i);
    return new CancelId(nodePointer, orderId);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    putInt32LE(_data, i, nodePointer);
    i += 4;
    i += orderId.write(_data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return BYTES;
  }
}
