package systems.glam.sdk.idl.programs.glam.staging.phoenix.gen.types;

import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

import java.math.BigInteger;
import java.util.OptionalLong;

import static software.sava.core.encoding.ByteUtil.*;

public record PostOnlyOrderPacket(int side,
                                  long priceInTicks,
                                  long numBaseLots,
                                  BigInteger clientOrderId,
                                  boolean slide,
                                  OptionalLong lastValidSlot,
                                  int orderFlags,
                                  boolean cancelExisting) implements SerDe {

  public static final int SIDE_OFFSET = 0;
  public static final int PRICE_IN_TICKS_OFFSET = 1;
  public static final int NUM_BASE_LOTS_OFFSET = 9;
  public static final int CLIENT_ORDER_ID_OFFSET = 17;
  public static final int SLIDE_OFFSET = 33;
  public static final int LAST_VALID_SLOT_OFFSET = 35;

  public static PostOnlyOrderPacket read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var side = _data[i] & 0xFF;
    ++i;
    final var priceInTicks = getInt64LE(_data, i);
    i += 8;
    final var numBaseLots = getInt64LE(_data, i);
    i += 8;
    final var clientOrderId = getInt128LE(_data, i);
    i += 16;
    final var slide = _data[i] == 1;
    ++i;
    final OptionalLong lastValidSlot;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      lastValidSlot = OptionalLong.empty();
      ++i;
    } else {
      ++i;
      lastValidSlot = OptionalLong.of(getInt64LE(_data, i));
      i += 8;
    }
    final var orderFlags = _data[i] & 0xFF;
    ++i;
    final var cancelExisting = _data[i] == 1;
    return new PostOnlyOrderPacket(side,
                                   priceInTicks,
                                   numBaseLots,
                                   clientOrderId,
                                   slide,
                                   lastValidSlot,
                                   orderFlags,
                                   cancelExisting);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    _data[i] = (byte) side;
    ++i;
    putInt64LE(_data, i, priceInTicks);
    i += 8;
    putInt64LE(_data, i, numBaseLots);
    i += 8;
    putInt128LE(_data, i, clientOrderId);
    i += 16;
    _data[i] = (byte) (slide ? 1 : 0);
    ++i;
    i += SerDeUtil.writeOptional(1, lastValidSlot, _data, i);
    _data[i] = (byte) orderFlags;
    ++i;
    _data[i] = (byte) (cancelExisting ? 1 : 0);
    ++i;
    return i - _offset;
  }

  @Override
  public int l() {
    return 1
         + 8
         + 8
         + 16
         + 1
         + (lastValidSlot == null || lastValidSlot.isEmpty() ? 1 : (1 + 8))
         + 1
         + 1;
  }
}
