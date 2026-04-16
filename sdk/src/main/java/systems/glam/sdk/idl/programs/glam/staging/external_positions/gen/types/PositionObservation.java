package systems.glam.sdk.idl.programs.glam.staging.external_positions.gen.types;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

import static software.sava.core.accounts.PublicKey.readPubKey;
import static software.sava.core.encoding.ByteUtil.getInt64LE;
import static software.sava.core.encoding.ByteUtil.putInt64LE;

/// Observation data for a single position, stored inline in `ObservationState`.
///
/// @param positionId The position_id this entry tracks.
/// @param hasPending Whether a pending observation exists.
/// @param padPending Alignment padding after has_pending (to 8-byte boundary for Observation).
/// @param pendingObservation The pending observation (only valid when `has_pending == true`).
/// @param hasValidated Whether a validated observation exists.
/// @param padValidated Alignment padding after has_validated (to 8-byte boundary for Observation).
/// @param lastValidatedObservation The last validated observation (only valid when `has_validated == true`).
/// @param validatedBy Signer who last validated.
/// @param validatedAtSlot Slot at which last validation occurred.
/// @param validatedBaseAssetAmount Base-asset-normalized amount from the last validated observation.
///                                 This is the value that contributes to the aggregate priced protocol.
public record PositionObservation(byte[] positionId,
                                  boolean hasPending,
                                  byte[] padPending,
                                  Observation pendingObservation,
                                  boolean hasValidated,
                                  byte[] padValidated,
                                  Observation lastValidatedObservation,
                                  PublicKey validatedBy,
                                  long validatedAtSlot,
                                  StoredI128 validatedBaseAssetAmount) implements SerDe {

  public static final int BYTES = 328;
  public static final int POSITION_ID_LEN = 32;
  public static final int PAD_PENDING_LEN = 7;
  public static final int PAD_VALIDATED_LEN = 7;

  public static final int POSITION_ID_OFFSET = 0;
  public static final int HAS_PENDING_OFFSET = 32;
  public static final int PAD_PENDING_OFFSET = 33;
  public static final int PENDING_OBSERVATION_OFFSET = 40;
  public static final int HAS_VALIDATED_OFFSET = 152;
  public static final int PAD_VALIDATED_OFFSET = 153;
  public static final int LAST_VALIDATED_OBSERVATION_OFFSET = 160;
  public static final int VALIDATED_BY_OFFSET = 272;
  public static final int VALIDATED_AT_SLOT_OFFSET = 304;
  public static final int VALIDATED_BASE_ASSET_AMOUNT_OFFSET = 312;

  public static PositionObservation read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var positionId = new byte[32];
    i += SerDeUtil.readArray(positionId, _data, i);
    final var hasPending = _data[i] == 1;
    ++i;
    final var padPending = new byte[7];
    i += SerDeUtil.readArray(padPending, _data, i);
    final var pendingObservation = Observation.read(_data, i);
    i += pendingObservation.l();
    final var hasValidated = _data[i] == 1;
    ++i;
    final var padValidated = new byte[7];
    i += SerDeUtil.readArray(padValidated, _data, i);
    final var lastValidatedObservation = Observation.read(_data, i);
    i += lastValidatedObservation.l();
    final var validatedBy = readPubKey(_data, i);
    i += 32;
    final var validatedAtSlot = getInt64LE(_data, i);
    i += 8;
    final var validatedBaseAssetAmount = StoredI128.read(_data, i);
    return new PositionObservation(positionId,
                                   hasPending,
                                   padPending,
                                   pendingObservation,
                                   hasValidated,
                                   padValidated,
                                   lastValidatedObservation,
                                   validatedBy,
                                   validatedAtSlot,
                                   validatedBaseAssetAmount);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    i += SerDeUtil.writeArrayChecked(positionId, 32, _data, i);
    _data[i] = (byte) (hasPending ? 1 : 0);
    ++i;
    i += SerDeUtil.writeArrayChecked(padPending, 7, _data, i);
    i += pendingObservation.write(_data, i);
    _data[i] = (byte) (hasValidated ? 1 : 0);
    ++i;
    i += SerDeUtil.writeArrayChecked(padValidated, 7, _data, i);
    i += lastValidatedObservation.write(_data, i);
    validatedBy.write(_data, i);
    i += 32;
    putInt64LE(_data, i, validatedAtSlot);
    i += 8;
    i += validatedBaseAssetAmount.write(_data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return BYTES;
  }
}
