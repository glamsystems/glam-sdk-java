package systems.glam.sdk.idl.programs.glam.kamino.gen.types;

import software.sava.core.accounts.PublicKey;
import software.sava.core.borsh.Borsh;

public record VaultsPolicy(PublicKey[] vaultsAllowlist) implements Borsh {

  public static VaultsPolicy read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    final var vaultsAllowlist = Borsh.readPublicKeyVector(_data, _offset);
    return new VaultsPolicy(vaultsAllowlist);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    i += Borsh.writeVector(vaultsAllowlist, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return Borsh.lenVector(vaultsAllowlist);
  }
}
