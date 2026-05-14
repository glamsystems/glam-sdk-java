package systems.glam.sdk.idl.programs.glam.staging.phoenix.gen.types;

import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

import java.util.OptionalLong;

import static software.sava.core.encoding.ByteUtil.getInt64LE;

public record CancelUpToArgs(int side,
                             OptionalLong numOrdersToCancel,
                             OptionalLong tickLimit) implements SerDe {

  public static final int SIDE_OFFSET = 0;
  public static final int NUM_ORDERS_TO_CANCEL_OFFSET = 2;

  public static CancelUpToArgs read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var side = _data[i] & 0xFF;
    ++i;
    final OptionalLong numOrdersToCancel;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      numOrdersToCancel = OptionalLong.empty();
      ++i;
    } else {
      ++i;
      numOrdersToCancel = OptionalLong.of(getInt64LE(_data, i));
      i += 8;
    }
    final OptionalLong tickLimit;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      tickLimit = OptionalLong.empty();
    } else {
      ++i;
      tickLimit = OptionalLong.of(getInt64LE(_data, i));
    }
    return new CancelUpToArgs(side, numOrdersToCancel, tickLimit);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    _data[i] = (byte) side;
    ++i;
    i += SerDeUtil.writeOptional(1, numOrdersToCancel, _data, i);
    i += SerDeUtil.writeOptional(1, tickLimit, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return 1 + (numOrdersToCancel == null || numOrdersToCancel.isEmpty() ? 1 : (1 + 8)) + (tickLimit == null || tickLimit.isEmpty() ? 1 : (1 + 8));
  }
}
