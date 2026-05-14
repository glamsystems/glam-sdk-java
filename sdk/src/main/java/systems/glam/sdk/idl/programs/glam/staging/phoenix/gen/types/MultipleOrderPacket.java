package systems.glam.sdk.idl.programs.glam.staging.phoenix.gen.types;

import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

import java.math.BigInteger;

import static software.sava.core.encoding.ByteUtil.getInt128LE;

public record MultipleOrderPacket(CondensedOrder[] bids,
                                  CondensedOrder[] asks,
                                  BigInteger clientOrderId,
                                  boolean slide) implements SerDe {

  public static final int BIDS_OFFSET = 0;

  public static MultipleOrderPacket read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var bids = SerDeUtil.readVector(4, CondensedOrder.class, CondensedOrder::read, _data, i);
    i += SerDeUtil.lenVector(4, bids);
    final var asks = SerDeUtil.readVector(4, CondensedOrder.class, CondensedOrder::read, _data, i);
    i += SerDeUtil.lenVector(4, asks);
    final BigInteger clientOrderId;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      clientOrderId = null;
      ++i;
    } else {
      ++i;
      clientOrderId = getInt128LE(_data, i);
      i += 16;
    }
    final var slide = _data[i] == 1;
    return new MultipleOrderPacket(bids,
                                   asks,
                                   clientOrderId,
                                   slide);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    i += SerDeUtil.writeVector(4, bids, _data, i);
    i += SerDeUtil.writeVector(4, asks, _data, i);
    i += SerDeUtil.write128Optional(1, clientOrderId, _data, i);
    _data[i] = (byte) (slide ? 1 : 0);
    ++i;
    return i - _offset;
  }

  @Override
  public int l() {
    return SerDeUtil.lenVector(4, bids) + SerDeUtil.lenVector(4, asks) + (clientOrderId == null ? 1 : (1 + 16)) + 1;
  }
}
