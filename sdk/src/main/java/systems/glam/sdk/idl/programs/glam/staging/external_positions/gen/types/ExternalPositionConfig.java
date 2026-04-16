package systems.glam.sdk.idl.programs.glam.staging.external_positions.gen.types;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

import static software.sava.core.accounts.PublicKey.readPubKey;
import static software.sava.core.encoding.ByteUtil.getInt32LE;
import static software.sava.core.encoding.ByteUtil.putInt32LE;

/// Per-position configuration entry in the registry.
///
/// @param positionId Unique identifier for this position within the vault.
///                   
///                   Under the cleaner pricing model, this 32-byte id is also the canonical
///                   coverage key published into `glam_state.external_positions` and
///                   `PricedProtocol.positions`. For bridge-managed inflight transfers, this
///                   should be the transfer-record pubkey bytes.
/// @param positionType Type of position (Valued or Tokenized).
/// @param sourceType Source type (Trusted or Native).
/// @param denomination Denomination rules for Trusted positions.
///                     For Native positions this is ignored (denomination derived from custody).
/// @param nativeCustodyAccount Custody account for Native positions. `Pubkey::default()` for Trusted.
/// @param nativeCustodyKind Custody kind for Native positions.
/// @param enabled Whether this position is enabled for observations.
/// @param freshnessOverrideSecs Freshness override in seconds. 0 means use vault default.
/// @param submitAllowlist Per-position submit allowlist. Empty vec = use role-based access only.
/// @param validateAllowlist Per-position validate allowlist. Empty vec = use role-based access only.
/// @param configureAllowlist Per-position configure allowlist. Empty vec = use role-based access only.
///                           This is intentionally more permissive than the protocol-wide asset
///                           allowlist semantics where an empty allowlist means deny all.
public record ExternalPositionConfig(byte[] positionId,
                                     ExternalPositionType positionType,
                                     ExternalSourceType sourceType,
                                     DenominationSpec denomination,
                                     PublicKey nativeCustodyAccount,
                                     NativeCustodyKind nativeCustodyKind,
                                     boolean enabled,
                                     int freshnessOverrideSecs,
                                     PublicKey[] submitAllowlist,
                                     PublicKey[] validateAllowlist,
                                     PublicKey[] configureAllowlist) implements SerDe {

  public static final int POSITION_ID_LEN = 32;
  public static final int POSITION_ID_OFFSET = 0;
  public static final int POSITION_TYPE_OFFSET = 32;
  public static final int SOURCE_TYPE_OFFSET = 33;
  public static final int DENOMINATION_OFFSET = 34;
  public static final int NATIVE_CUSTODY_ACCOUNT_OFFSET = 67;
  public static final int NATIVE_CUSTODY_KIND_OFFSET = 99;
  public static final int ENABLED_OFFSET = 100;
  public static final int FRESHNESS_OVERRIDE_SECS_OFFSET = 101;
  public static final int SUBMIT_ALLOWLIST_OFFSET = 105;

  public static ExternalPositionConfig read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var positionId = new byte[32];
    i += SerDeUtil.readArray(positionId, _data, i);
    final var positionType = ExternalPositionType.read(_data, i);
    i += positionType.l();
    final var sourceType = ExternalSourceType.read(_data, i);
    i += sourceType.l();
    final var denomination = DenominationSpec.read(_data, i);
    i += denomination.l();
    final var nativeCustodyAccount = readPubKey(_data, i);
    i += 32;
    final var nativeCustodyKind = NativeCustodyKind.read(_data, i);
    i += nativeCustodyKind.l();
    final var enabled = _data[i] == 1;
    ++i;
    final var freshnessOverrideSecs = getInt32LE(_data, i);
    i += 4;
    final var submitAllowlist = SerDeUtil.readPublicKeyVector(4, _data, i);
    i += SerDeUtil.lenVector(4, submitAllowlist);
    final var validateAllowlist = SerDeUtil.readPublicKeyVector(4, _data, i);
    i += SerDeUtil.lenVector(4, validateAllowlist);
    final var configureAllowlist = SerDeUtil.readPublicKeyVector(4, _data, i);
    return new ExternalPositionConfig(positionId,
                                      positionType,
                                      sourceType,
                                      denomination,
                                      nativeCustodyAccount,
                                      nativeCustodyKind,
                                      enabled,
                                      freshnessOverrideSecs,
                                      submitAllowlist,
                                      validateAllowlist,
                                      configureAllowlist);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    i += SerDeUtil.writeArrayChecked(positionId, 32, _data, i);
    i += positionType.write(_data, i);
    i += sourceType.write(_data, i);
    i += denomination.write(_data, i);
    nativeCustodyAccount.write(_data, i);
    i += 32;
    i += nativeCustodyKind.write(_data, i);
    _data[i] = (byte) (enabled ? 1 : 0);
    ++i;
    putInt32LE(_data, i, freshnessOverrideSecs);
    i += 4;
    i += SerDeUtil.writeVector(4, submitAllowlist, _data, i);
    i += SerDeUtil.writeVector(4, validateAllowlist, _data, i);
    i += SerDeUtil.writeVector(4, configureAllowlist, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return SerDeUtil.lenArray(positionId)
         + positionType.l()
         + sourceType.l()
         + denomination.l()
         + 32
         + nativeCustodyKind.l()
         + 1
         + 4
         + SerDeUtil.lenVector(4, submitAllowlist)
         + SerDeUtil.lenVector(4, validateAllowlist)
         + SerDeUtil.lenVector(4, configureAllowlist);
  }
}
