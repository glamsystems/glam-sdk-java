package systems.glam.sdk.idl.programs.glam.staging.phoenix.gen.types;

import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

import java.math.BigInteger;
import java.util.OptionalLong;

import static software.sava.core.encoding.ByteUtil.*;

public record ImmediateOrCancelOrderPacket(int side,
                                           OptionalLong priceInTicks,
                                           long numBaseLots,
                                           OptionalLong numQuoteLots,
                                           long minBaseLotsToFill,
                                           long minQuoteLotsToFill,
                                           int selfTradeBehavior,
                                           OptionalLong matchLimit,
                                           BigInteger clientOrderId,
                                           OptionalLong lastValidSlot,
                                           int orderFlags,
                                           boolean cancelExisting) implements SerDe {

  public static final int SIDE_OFFSET = 0;
  public static final int PRICE_IN_TICKS_OFFSET = 2;

  public static ImmediateOrCancelOrderPacket read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var side = _data[i] & 0xFF;
    ++i;
    final OptionalLong priceInTicks;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      priceInTicks = OptionalLong.empty();
      ++i;
    } else {
      ++i;
      priceInTicks = OptionalLong.of(getInt64LE(_data, i));
      i += 8;
    }
    final var numBaseLots = getInt64LE(_data, i);
    i += 8;
    final OptionalLong numQuoteLots;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      numQuoteLots = OptionalLong.empty();
      ++i;
    } else {
      ++i;
      numQuoteLots = OptionalLong.of(getInt64LE(_data, i));
      i += 8;
    }
    final var minBaseLotsToFill = getInt64LE(_data, i);
    i += 8;
    final var minQuoteLotsToFill = getInt64LE(_data, i);
    i += 8;
    final var selfTradeBehavior = _data[i] & 0xFF;
    ++i;
    final OptionalLong matchLimit;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      matchLimit = OptionalLong.empty();
      ++i;
    } else {
      ++i;
      matchLimit = OptionalLong.of(getInt64LE(_data, i));
      i += 8;
    }
    final var clientOrderId = getInt128LE(_data, i);
    i += 16;
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
    return new ImmediateOrCancelOrderPacket(side,
                                            priceInTicks,
                                            numBaseLots,
                                            numQuoteLots,
                                            minBaseLotsToFill,
                                            minQuoteLotsToFill,
                                            selfTradeBehavior,
                                            matchLimit,
                                            clientOrderId,
                                            lastValidSlot,
                                            orderFlags,
                                            cancelExisting);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    _data[i] = (byte) side;
    ++i;
    i += SerDeUtil.writeOptional(1, priceInTicks, _data, i);
    putInt64LE(_data, i, numBaseLots);
    i += 8;
    i += SerDeUtil.writeOptional(1, numQuoteLots, _data, i);
    putInt64LE(_data, i, minBaseLotsToFill);
    i += 8;
    putInt64LE(_data, i, minQuoteLotsToFill);
    i += 8;
    _data[i] = (byte) selfTradeBehavior;
    ++i;
    i += SerDeUtil.writeOptional(1, matchLimit, _data, i);
    putInt128LE(_data, i, clientOrderId);
    i += 16;
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
         + (priceInTicks == null || priceInTicks.isEmpty() ? 1 : (1 + 8))
         + 8
         + (numQuoteLots == null || numQuoteLots.isEmpty() ? 1 : (1 + 8))
         + 8
         + 8
         + 1
         + (matchLimit == null || matchLimit.isEmpty() ? 1 : (1 + 8))
         + 16
         + (lastValidSlot == null || lastValidSlot.isEmpty() ? 1 : (1 + 8))
         + 1
         + 1;
  }
}
