package systems.glam.sdk.idl.programs.glam.staging.protocol.gen.types;

import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

import static software.sava.core.encoding.ByteUtil.getInt64LE;
import static software.sava.core.encoding.ByteUtil.putInt64LE;

public record NotifyAndSettle(ValuationModel model,
                              boolean permissionlessFulfillment,
                              NoticePeriodType subscribeNoticePeriodType,
                              long subscribeNoticePeriod,
                              long subscribeSettlementPeriod,
                              long subscribeCancellationWindow,
                              NoticePeriodType redeemNoticePeriodType,
                              long redeemNoticePeriod,
                              long redeemSettlementPeriod,
                              long redeemCancellationWindow,
                              TimeUnit timeUnit,
                              byte[] padding) implements SerDe {

  public static final int BYTES = 56;
  public static final int PADDING_LEN = 3;

  public static NotifyAndSettle read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var model = ValuationModel.read(_data, i);
    i += model.l();
    final var permissionlessFulfillment = _data[i] == 1;
    ++i;
    final var subscribeNoticePeriodType = NoticePeriodType.read(_data, i);
    i += subscribeNoticePeriodType.l();
    final var subscribeNoticePeriod = getInt64LE(_data, i);
    i += 8;
    final var subscribeSettlementPeriod = getInt64LE(_data, i);
    i += 8;
    final var subscribeCancellationWindow = getInt64LE(_data, i);
    i += 8;
    final var redeemNoticePeriodType = NoticePeriodType.read(_data, i);
    i += redeemNoticePeriodType.l();
    final var redeemNoticePeriod = getInt64LE(_data, i);
    i += 8;
    final var redeemSettlementPeriod = getInt64LE(_data, i);
    i += 8;
    final var redeemCancellationWindow = getInt64LE(_data, i);
    i += 8;
    final var timeUnit = TimeUnit.read(_data, i);
    i += timeUnit.l();
    final var padding = new byte[3];
    SerDeUtil.readArray(padding, _data, i);
    return new NotifyAndSettle(model,
                               permissionlessFulfillment,
                               subscribeNoticePeriodType,
                               subscribeNoticePeriod,
                               subscribeSettlementPeriod,
                               subscribeCancellationWindow,
                               redeemNoticePeriodType,
                               redeemNoticePeriod,
                               redeemSettlementPeriod,
                               redeemCancellationWindow,
                               timeUnit,
                               padding);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    i += model.write(_data, i);
    _data[i] = (byte) (permissionlessFulfillment ? 1 : 0);
    ++i;
    i += subscribeNoticePeriodType.write(_data, i);
    putInt64LE(_data, i, subscribeNoticePeriod);
    i += 8;
    putInt64LE(_data, i, subscribeSettlementPeriod);
    i += 8;
    putInt64LE(_data, i, subscribeCancellationWindow);
    i += 8;
    i += redeemNoticePeriodType.write(_data, i);
    putInt64LE(_data, i, redeemNoticePeriod);
    i += 8;
    putInt64LE(_data, i, redeemSettlementPeriod);
    i += 8;
    putInt64LE(_data, i, redeemCancellationWindow);
    i += 8;
    i += timeUnit.write(_data, i);
    i += SerDeUtil.writeArrayChecked(padding, 3, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return BYTES;
  }
}
