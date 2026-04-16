package systems.glam.sdk.idl.programs.glam.staging.external_positions.gen.types;

import software.sava.core.accounts.PublicKey;
import software.sava.core.programs.Discriminator;
import software.sava.core.rpc.Filter;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;
import software.sava.rpc.json.http.response.AccountInfo;

import java.util.function.BiFunction;

import static software.sava.core.accounts.PublicKey.readPubKey;
import static software.sava.core.programs.Discriminator.createAnchorDiscriminator;
import static software.sava.core.programs.Discriminator.toDiscriminator;

/// Single PDA per vault that tracks all external position observations.
/// Seeds: SEED_OBSERVATION_STATE, glam_state.key()
///
/// @param glamState The glam_state this observation state belongs to.
/// @param bump PDA bump.
/// @param positionsLen Number of active positions stored in `positions`.
/// @param positions Per-position observation entries.
public record ObservationState(PublicKey _address,
                               Discriminator discriminator,
                               PublicKey glamState,
                               int bump,
                               int positionsLen,
                               byte[] reserved,
                               PositionObservation[] positions) implements SerDe {

  public static final int BYTES = 5296;
  public static final int RESERVED_LEN = 6;
  public static final int POSITIONS_LEN = 16;
  public static final Filter SIZE_FILTER = Filter.createDataSizeFilter(BYTES);

  public static final Discriminator DISCRIMINATOR = toDiscriminator(122, 174, 197, 53, 129, 9, 165, 132);
  public static final Filter DISCRIMINATOR_FILTER = Filter.createMemCompFilter(0, DISCRIMINATOR.data());

  public static final int GLAM_STATE_OFFSET = 8;
  public static final int BUMP_OFFSET = 40;
  public static final int POSITIONS_LEN_OFFSET = 41;
  public static final int RESERVED_OFFSET = 42;
  public static final int POSITIONS_OFFSET = 48;

  public static Filter createGlamStateFilter(final PublicKey glamState) {
    return Filter.createMemCompFilter(GLAM_STATE_OFFSET, glamState);
  }

  public static Filter createBumpFilter(final int bump) {
    return Filter.createMemCompFilter(BUMP_OFFSET, new byte[]{(byte) bump});
  }

  public static Filter createPositionsLenFilter(final int positionsLen) {
    return Filter.createMemCompFilter(POSITIONS_LEN_OFFSET, new byte[]{(byte) positionsLen});
  }

  public static ObservationState read(final byte[] _data, final int _offset) {
    return read(null, _data, _offset);
  }

  public static ObservationState read(final AccountInfo<byte[]> accountInfo) {
    return read(accountInfo.pubKey(), accountInfo.data(), 0);
  }

  public static ObservationState read(final PublicKey _address, final byte[] _data) {
    return read(_address, _data, 0);
  }

  public static final BiFunction<PublicKey, byte[], ObservationState> FACTORY = ObservationState::read;

  public static ObservationState read(final PublicKey _address, final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    final var discriminator = createAnchorDiscriminator(_data, _offset);
    int i = _offset + discriminator.length();
    final var glamState = readPubKey(_data, i);
    i += 32;
    final var bump = _data[i] & 0xFF;
    ++i;
    final var positionsLen = _data[i] & 0xFF;
    ++i;
    final var reserved = new byte[6];
    i += SerDeUtil.readArray(reserved, _data, i);
    final var positions = new PositionObservation[16];
    SerDeUtil.readArray(positions, PositionObservation::read, _data, i);
    return new ObservationState(_address,
                                discriminator,
                                glamState,
                                bump,
                                positionsLen,
                                reserved,
                                positions);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset + discriminator.write(_data, _offset);
    glamState.write(_data, i);
    i += 32;
    _data[i] = (byte) bump;
    ++i;
    _data[i] = (byte) positionsLen;
    ++i;
    i += SerDeUtil.writeArrayChecked(reserved, 6, _data, i);
    i += SerDeUtil.writeArrayChecked(positions, 16, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return BYTES;
  }
}
