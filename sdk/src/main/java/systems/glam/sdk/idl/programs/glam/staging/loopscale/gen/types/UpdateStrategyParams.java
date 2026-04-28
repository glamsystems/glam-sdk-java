package systems.glam.sdk.idl.programs.glam.staging.loopscale.gen.types;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

import java.util.OptionalLong;

import static software.sava.core.accounts.PublicKey.readPubKey;
import static software.sava.core.encoding.ByteUtil.getInt64LE;

public record UpdateStrategyParams(Boolean originationsEnabled,
                                   OptionalLong liquidityBuffer,
                                   OptionalLong interestFee,
                                   OptionalLong originationFee,
                                   OptionalLong principalFee,
                                   OptionalLong originationCap,
                                   PublicKey marketInformation,
                                   ExternalYieldSourceArgs externalYieldSourceArgs) implements SerDe {

  public static final int ORIGINATIONS_ENABLED_OFFSET = 1;

  public static UpdateStrategyParams read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final Boolean originationsEnabled;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      originationsEnabled = null;
      ++i;
    } else {
      ++i;
      originationsEnabled = _data[i] == 1;
      ++i;
    }
    final OptionalLong liquidityBuffer;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      liquidityBuffer = OptionalLong.empty();
      ++i;
    } else {
      ++i;
      liquidityBuffer = OptionalLong.of(getInt64LE(_data, i));
      i += 8;
    }
    final OptionalLong interestFee;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      interestFee = OptionalLong.empty();
      ++i;
    } else {
      ++i;
      interestFee = OptionalLong.of(getInt64LE(_data, i));
      i += 8;
    }
    final OptionalLong originationFee;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      originationFee = OptionalLong.empty();
      ++i;
    } else {
      ++i;
      originationFee = OptionalLong.of(getInt64LE(_data, i));
      i += 8;
    }
    final OptionalLong principalFee;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      principalFee = OptionalLong.empty();
      ++i;
    } else {
      ++i;
      principalFee = OptionalLong.of(getInt64LE(_data, i));
      i += 8;
    }
    final OptionalLong originationCap;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      originationCap = OptionalLong.empty();
      ++i;
    } else {
      ++i;
      originationCap = OptionalLong.of(getInt64LE(_data, i));
      i += 8;
    }
    final PublicKey marketInformation;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      marketInformation = null;
      ++i;
    } else {
      ++i;
      marketInformation = readPubKey(_data, i);
      i += 32;
    }
    final ExternalYieldSourceArgs externalYieldSourceArgs;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      externalYieldSourceArgs = null;
    } else {
      ++i;
      externalYieldSourceArgs = ExternalYieldSourceArgs.read(_data, i);
    }
    return new UpdateStrategyParams(originationsEnabled,
                                    liquidityBuffer,
                                    interestFee,
                                    originationFee,
                                    principalFee,
                                    originationCap,
                                    marketInformation,
                                    externalYieldSourceArgs);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    i += SerDeUtil.writeOptional(1, originationsEnabled, _data, i);
    i += SerDeUtil.writeOptional(1, liquidityBuffer, _data, i);
    i += SerDeUtil.writeOptional(1, interestFee, _data, i);
    i += SerDeUtil.writeOptional(1, originationFee, _data, i);
    i += SerDeUtil.writeOptional(1, principalFee, _data, i);
    i += SerDeUtil.writeOptional(1, originationCap, _data, i);
    i += SerDeUtil.writeOptional(1, marketInformation, _data, i);
    i += SerDeUtil.writeOptional(1, externalYieldSourceArgs, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return (originationsEnabled == null ? 1 : (1 + 1))
         + (liquidityBuffer == null || liquidityBuffer.isEmpty() ? 1 : (1 + 8))
         + (interestFee == null || interestFee.isEmpty() ? 1 : (1 + 8))
         + (originationFee == null || originationFee.isEmpty() ? 1 : (1 + 8))
         + (principalFee == null || principalFee.isEmpty() ? 1 : (1 + 8))
         + (originationCap == null || originationCap.isEmpty() ? 1 : (1 + 8))
         + (marketInformation == null ? 1 : (1 + 32))
         + (externalYieldSourceArgs == null ? 1 : (1 + externalYieldSourceArgs.l()));
  }
}
