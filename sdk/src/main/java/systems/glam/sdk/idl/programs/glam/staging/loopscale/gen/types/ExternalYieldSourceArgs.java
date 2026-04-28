package systems.glam.sdk.idl.programs.glam.staging.loopscale.gen.types;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.core.gen.SerDe;

import static software.sava.core.accounts.PublicKey.readPubKey;

public record ExternalYieldSourceArgs(int newExternalYieldSource, PublicKey externalYieldVault) implements SerDe {

  public static final int BYTES = 33;

  public static final int NEW_EXTERNAL_YIELD_SOURCE_OFFSET = 0;
  public static final int EXTERNAL_YIELD_VAULT_OFFSET = 1;

  public static ExternalYieldSourceArgs read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var newExternalYieldSource = _data[i] & 0xFF;
    ++i;
    final var externalYieldVault = readPubKey(_data, i);
    return new ExternalYieldSourceArgs(newExternalYieldSource, externalYieldVault);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    _data[i] = (byte) newExternalYieldSource;
    ++i;
    externalYieldVault.write(_data, i);
    i += 32;
    return i - _offset;
  }

  @Override
  public int l() {
    return BYTES;
  }
}
