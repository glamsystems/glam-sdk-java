package systems.glam.sdk.idl.programs.glam.staging.external_positions.gen.types;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

import static software.sava.core.accounts.PublicKey.readPubKey;
import static software.sava.core.encoding.ByteUtil.getInt64LE;
import static software.sava.core.encoding.ByteUtil.putInt64LE;

/// A single observation snapshot.
///
/// @param amount Signed amount in the observation's denomination.
/// @param denomination Denomination of the amount.
/// @param padDenom Alignment padding after denomination (to 8-byte boundary for i64).
/// @param observationTimestamp Unix timestamp of the observation.
/// @param externalShares External share count (for Tokenized positions).
/// @param submittedBy Signer who submitted this observation.
/// @param submittedAtSlot Slot at which this observation was submitted.
public record Observation(StoredI128 amount,
                          DenominationSpec denomination,
                          byte[] padDenom,
                          long observationTimestamp,
                          long externalShares,
                          PublicKey submittedBy,
                          long submittedAtSlot) implements SerDe {

  public static final int BYTES = 112;
  public static final int PAD_DENOM_LEN = 7;

  public static final int AMOUNT_OFFSET = 0;
  public static final int DENOMINATION_OFFSET = 16;
  public static final int PAD_DENOM_OFFSET = 49;
  public static final int OBSERVATION_TIMESTAMP_OFFSET = 56;
  public static final int EXTERNAL_SHARES_OFFSET = 64;
  public static final int SUBMITTED_BY_OFFSET = 72;
  public static final int SUBMITTED_AT_SLOT_OFFSET = 104;

  public static Observation read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var amount = StoredI128.read(_data, i);
    i += amount.l();
    final var denomination = DenominationSpec.read(_data, i);
    i += denomination.l();
    final var padDenom = new byte[7];
    i += SerDeUtil.readArray(padDenom, _data, i);
    final var observationTimestamp = getInt64LE(_data, i);
    i += 8;
    final var externalShares = getInt64LE(_data, i);
    i += 8;
    final var submittedBy = readPubKey(_data, i);
    i += 32;
    final var submittedAtSlot = getInt64LE(_data, i);
    return new Observation(amount,
                           denomination,
                           padDenom,
                           observationTimestamp,
                           externalShares,
                           submittedBy,
                           submittedAtSlot);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    i += amount.write(_data, i);
    i += denomination.write(_data, i);
    i += SerDeUtil.writeArrayChecked(padDenom, 7, _data, i);
    putInt64LE(_data, i, observationTimestamp);
    i += 8;
    putInt64LE(_data, i, externalShares);
    i += 8;
    submittedBy.write(_data, i);
    i += 32;
    putInt64LE(_data, i, submittedAtSlot);
    i += 8;
    return i - _offset;
  }

  @Override
  public int l() {
    return BYTES;
  }
}
