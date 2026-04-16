package systems.glam.sdk.idl.programs.glam.staging.external_positions.gen.types;

import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

import java.math.BigInteger;

import static software.sava.core.encoding.ByteUtil.*;

/// Observation data submitted by a caller.
///
/// @param positionId The position being observed, identified by position_id.
///                   The same 32-byte id is used as the canonical AUM coverage key.
/// @param amount Signed amount in the position's denomination.
///               Positive = asset, negative = liability.
/// @param denomination Denomination of the amount.
/// @param observationTimestamp Unix timestamp of the observation.
/// @param externalShares External share count (required > 0 for Tokenized positions, 0 for Valued).
/// @param reserved Reserved for future use.
public record PositionObservationInput(byte[] positionId,
                                       BigInteger amount,
                                       DenominationSpec denomination,
                                       long observationTimestamp,
                                       long externalShares,
                                       byte[] reserved) implements SerDe {

  public static final int BYTES = 225;
  public static final int POSITION_ID_LEN = 32;
  public static final int RESERVED_LEN = 128;

  public static final int POSITION_ID_OFFSET = 0;
  public static final int AMOUNT_OFFSET = 32;
  public static final int DENOMINATION_OFFSET = 48;
  public static final int OBSERVATION_TIMESTAMP_OFFSET = 81;
  public static final int EXTERNAL_SHARES_OFFSET = 89;
  public static final int RESERVED_OFFSET = 97;

  public static PositionObservationInput read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var positionId = new byte[32];
    i += SerDeUtil.readArray(positionId, _data, i);
    final var amount = getInt128LE(_data, i);
    i += 16;
    final var denomination = DenominationSpec.read(_data, i);
    i += denomination.l();
    final var observationTimestamp = getInt64LE(_data, i);
    i += 8;
    final var externalShares = getInt64LE(_data, i);
    i += 8;
    final var reserved = new byte[128];
    SerDeUtil.readArray(reserved, _data, i);
    return new PositionObservationInput(positionId,
                                        amount,
                                        denomination,
                                        observationTimestamp,
                                        externalShares,
                                        reserved);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    i += SerDeUtil.writeArrayChecked(positionId, 32, _data, i);
    putInt128LE(_data, i, amount);
    i += 16;
    i += denomination.write(_data, i);
    putInt64LE(_data, i, observationTimestamp);
    i += 8;
    putInt64LE(_data, i, externalShares);
    i += 8;
    i += SerDeUtil.writeArrayChecked(reserved, 128, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return BYTES;
  }
}
