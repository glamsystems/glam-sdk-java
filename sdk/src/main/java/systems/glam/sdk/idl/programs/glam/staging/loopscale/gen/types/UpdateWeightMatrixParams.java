package systems.glam.sdk.idl.programs.glam.staging.loopscale.gen.types;

import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

public record UpdateWeightMatrixParams(int collateralIndex,
                                       int[] weightMatrix,
                                       ExpectedLoanValues expectedLoanValues,
                                       byte[] assetIndexGuidance) implements SerDe {

  public static final int WEIGHT_MATRIX_LEN = 5;
  public static final int COLLATERAL_INDEX_OFFSET = 0;
  public static final int WEIGHT_MATRIX_OFFSET = 1;
  public static final int EXPECTED_LOAN_VALUES_OFFSET = 21;
  public static final int ASSET_INDEX_GUIDANCE_OFFSET = 49;

  public static UpdateWeightMatrixParams read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var collateralIndex = _data[i] & 0xFF;
    ++i;
    final var weightMatrix = new int[5];
    i += SerDeUtil.readArray(weightMatrix, _data, i);
    final var expectedLoanValues = ExpectedLoanValues.read(_data, i);
    i += expectedLoanValues.l();
    final var assetIndexGuidance = SerDeUtil.readbyteVector(4, _data, i);
    return new UpdateWeightMatrixParams(collateralIndex,
                                        weightMatrix,
                                        expectedLoanValues,
                                        assetIndexGuidance);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    _data[i] = (byte) collateralIndex;
    ++i;
    i += SerDeUtil.writeArrayChecked(weightMatrix, 5, _data, i);
    i += expectedLoanValues.write(_data, i);
    i += SerDeUtil.writeVector(4, assetIndexGuidance, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return 1 + SerDeUtil.lenArray(weightMatrix) + expectedLoanValues.l() + SerDeUtil.lenVector(4, assetIndexGuidance);
  }
}
