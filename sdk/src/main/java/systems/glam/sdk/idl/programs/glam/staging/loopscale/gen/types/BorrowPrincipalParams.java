package systems.glam.sdk.idl.programs.glam.staging.loopscale.gen.types;

import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

import static software.sava.core.encoding.ByteUtil.getInt64LE;
import static software.sava.core.encoding.ByteUtil.putInt64LE;

public record BorrowPrincipalParams(long amount,
                                    byte[] assetIndexGuidance,
                                    int duration,
                                    ExpectedLoanValues expectedLoanValues,
                                    boolean skipSolUnwrap) implements SerDe {

  public static final int AMOUNT_OFFSET = 0;
  public static final int ASSET_INDEX_GUIDANCE_OFFSET = 8;

  public static BorrowPrincipalParams read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var amount = getInt64LE(_data, i);
    i += 8;
    final var assetIndexGuidance = SerDeUtil.readbyteVector(4, _data, i);
    i += SerDeUtil.lenVector(4, assetIndexGuidance);
    final var duration = _data[i] & 0xFF;
    ++i;
    final var expectedLoanValues = ExpectedLoanValues.read(_data, i);
    i += expectedLoanValues.l();
    final var skipSolUnwrap = _data[i] == 1;
    return new BorrowPrincipalParams(amount,
                                     assetIndexGuidance,
                                     duration,
                                     expectedLoanValues,
                                     skipSolUnwrap);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    putInt64LE(_data, i, amount);
    i += 8;
    i += SerDeUtil.writeVector(4, assetIndexGuidance, _data, i);
    _data[i] = (byte) duration;
    ++i;
    i += expectedLoanValues.write(_data, i);
    _data[i] = (byte) (skipSolUnwrap ? 1 : 0);
    ++i;
    return i - _offset;
  }

  @Override
  public int l() {
    return 8
         + SerDeUtil.lenVector(4, assetIndexGuidance)
         + 1
         + expectedLoanValues.l()
         + 1;
  }
}
