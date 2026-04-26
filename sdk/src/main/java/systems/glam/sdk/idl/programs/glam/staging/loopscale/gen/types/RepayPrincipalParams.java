package systems.glam.sdk.idl.programs.glam.staging.loopscale.gen.types;

import software.sava.idl.clients.core.gen.SerDe;

import static software.sava.core.encoding.ByteUtil.getInt64LE;
import static software.sava.core.encoding.ByteUtil.putInt64LE;

public record RepayPrincipalParams(long amount,
                                   int ledgerIndex,
                                   boolean repayAll) implements SerDe {

  public static final int BYTES = 10;

  public static final int AMOUNT_OFFSET = 0;
  public static final int LEDGER_INDEX_OFFSET = 8;
  public static final int REPAY_ALL_OFFSET = 9;

  public static RepayPrincipalParams read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var amount = getInt64LE(_data, i);
    i += 8;
    final var ledgerIndex = _data[i] & 0xFF;
    ++i;
    final var repayAll = _data[i] == 1;
    return new RepayPrincipalParams(amount, ledgerIndex, repayAll);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    putInt64LE(_data, i, amount);
    i += 8;
    _data[i] = (byte) ledgerIndex;
    ++i;
    _data[i] = (byte) (repayAll ? 1 : 0);
    ++i;
    return i - _offset;
  }

  @Override
  public int l() {
    return BYTES;
  }
}
