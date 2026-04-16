package systems.glam.sdk.idl.programs.glam.staging.external_positions.gen.types;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.core.gen.SerDe;

import static software.sava.core.accounts.PublicKey.readPubKey;

/// Full denomination spec: the discriminant plus an optional mint pubkey.
///
/// @param mint Only meaningful when `denom == Denomination::Mint`.
///             Set to `Pubkey::default()` for `Usd`.
public record DenominationSpec(Denomination denom, PublicKey mint) implements SerDe {

  public static final int BYTES = 33;

  public static final int DENOM_OFFSET = 0;
  public static final int MINT_OFFSET = 1;

  public static DenominationSpec read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var denom = Denomination.read(_data, i);
    i += denom.l();
    final var mint = readPubKey(_data, i);
    return new DenominationSpec(denom, mint);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    i += denom.write(_data, i);
    mint.write(_data, i);
    i += 32;
    return i - _offset;
  }

  @Override
  public int l() {
    return BYTES;
  }
}
