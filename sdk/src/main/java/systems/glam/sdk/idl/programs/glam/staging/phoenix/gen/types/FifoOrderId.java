package systems.glam.sdk.idl.programs.glam.staging.phoenix.gen.types;

import software.sava.idl.clients.core.gen.SerDe;

import static software.sava.core.encoding.ByteUtil.getInt64LE;
import static software.sava.core.encoding.ByteUtil.putInt64LE;

public record FifoOrderId(long priceInTicks, long orderSequenceNumber) implements SerDe {

  public static final int BYTES = 16;

  public static final int PRICE_IN_TICKS_OFFSET = 0;
  public static final int ORDER_SEQUENCE_NUMBER_OFFSET = 8;

  public static FifoOrderId read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var priceInTicks = getInt64LE(_data, i);
    i += 8;
    final var orderSequenceNumber = getInt64LE(_data, i);
    return new FifoOrderId(priceInTicks, orderSequenceNumber);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    putInt64LE(_data, i, priceInTicks);
    i += 8;
    putInt64LE(_data, i, orderSequenceNumber);
    i += 8;
    return i - _offset;
  }

  @Override
  public int l() {
    return BYTES;
  }
}
