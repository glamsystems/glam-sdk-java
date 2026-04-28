package systems.glam.sdk.idl.programs.glam.staging.loopscale.gen.types;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

import static software.sava.core.accounts.PublicKey.readPubKey;
import static software.sava.core.encoding.ByteUtil.getInt64LE;
import static software.sava.core.encoding.ByteUtil.putInt64LE;

public record CreateStrategyParams(PublicKey lender,
                                   long originationCap,
                                   long liquidityBuffer,
                                   long interestFee,
                                   long originationFee,
                                   long principalFee,
                                   boolean originationsEnabled,
                                   ExternalYieldSourceArgs externalYieldSourceArgs) implements SerDe {

  public static final int LENDER_OFFSET = 0;
  public static final int ORIGINATION_CAP_OFFSET = 32;
  public static final int LIQUIDITY_BUFFER_OFFSET = 40;
  public static final int INTEREST_FEE_OFFSET = 48;
  public static final int ORIGINATION_FEE_OFFSET = 56;
  public static final int PRINCIPAL_FEE_OFFSET = 64;
  public static final int ORIGINATIONS_ENABLED_OFFSET = 72;
  public static final int EXTERNAL_YIELD_SOURCE_ARGS_OFFSET = 74;

  public static CreateStrategyParams read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var lender = readPubKey(_data, i);
    i += 32;
    final var originationCap = getInt64LE(_data, i);
    i += 8;
    final var liquidityBuffer = getInt64LE(_data, i);
    i += 8;
    final var interestFee = getInt64LE(_data, i);
    i += 8;
    final var originationFee = getInt64LE(_data, i);
    i += 8;
    final var principalFee = getInt64LE(_data, i);
    i += 8;
    final var originationsEnabled = _data[i] == 1;
    ++i;
    final ExternalYieldSourceArgs externalYieldSourceArgs;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      externalYieldSourceArgs = null;
    } else {
      ++i;
      externalYieldSourceArgs = ExternalYieldSourceArgs.read(_data, i);
    }
    return new CreateStrategyParams(lender,
                                    originationCap,
                                    liquidityBuffer,
                                    interestFee,
                                    originationFee,
                                    principalFee,
                                    originationsEnabled,
                                    externalYieldSourceArgs);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    lender.write(_data, i);
    i += 32;
    putInt64LE(_data, i, originationCap);
    i += 8;
    putInt64LE(_data, i, liquidityBuffer);
    i += 8;
    putInt64LE(_data, i, interestFee);
    i += 8;
    putInt64LE(_data, i, originationFee);
    i += 8;
    putInt64LE(_data, i, principalFee);
    i += 8;
    _data[i] = (byte) (originationsEnabled ? 1 : 0);
    ++i;
    i += SerDeUtil.writeOptional(1, externalYieldSourceArgs, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return 32
         + 8
         + 8
         + 8
         + 8
         + 8
         + 1
         + (externalYieldSourceArgs == null ? 1 : (1 + externalYieldSourceArgs.l()));
  }
}
