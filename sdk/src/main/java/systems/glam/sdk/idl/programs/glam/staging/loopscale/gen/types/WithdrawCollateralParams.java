package systems.glam.sdk.idl.programs.glam.staging.loopscale.gen.types;

import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

import static software.sava.core.encoding.ByteUtil.getInt64LE;
import static software.sava.core.encoding.ByteUtil.putInt64LE;

public record WithdrawCollateralParams(long amount,
                                       int collateralIndex,
                                       byte[] assetIndexGuidance,
                                       ExpectedLoanValues expectedLoanValues,
                                       boolean closeIfEligible,
                                       boolean withdrawAll) implements SerDe {

  public static final int AMOUNT_OFFSET = 0;
  public static final int COLLATERAL_INDEX_OFFSET = 8;
  public static final int ASSET_INDEX_GUIDANCE_OFFSET = 9;

  public static WithdrawCollateralParams read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var amount = getInt64LE(_data, i);
    i += 8;
    final var collateralIndex = _data[i] & 0xFF;
    ++i;
    final var assetIndexGuidance = SerDeUtil.readbyteVector(4, _data, i);
    i += SerDeUtil.lenVector(4, assetIndexGuidance);
    final var expectedLoanValues = ExpectedLoanValues.read(_data, i);
    i += expectedLoanValues.l();
    final var closeIfEligible = _data[i] == 1;
    ++i;
    final var withdrawAll = _data[i] == 1;
    return new WithdrawCollateralParams(amount,
                                        collateralIndex,
                                        assetIndexGuidance,
                                        expectedLoanValues,
                                        closeIfEligible,
                                        withdrawAll);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    putInt64LE(_data, i, amount);
    i += 8;
    _data[i] = (byte) collateralIndex;
    ++i;
    i += SerDeUtil.writeVector(4, assetIndexGuidance, _data, i);
    i += expectedLoanValues.write(_data, i);
    _data[i] = (byte) (closeIfEligible ? 1 : 0);
    ++i;
    _data[i] = (byte) (withdrawAll ? 1 : 0);
    ++i;
    return i - _offset;
  }

  @Override
  public int l() {
    return 8
         + 1
         + SerDeUtil.lenVector(4, assetIndexGuidance)
         + expectedLoanValues.l()
         + 1
         + 1;
  }
}
