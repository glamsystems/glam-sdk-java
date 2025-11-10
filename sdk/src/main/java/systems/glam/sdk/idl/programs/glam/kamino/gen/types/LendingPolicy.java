package systems.glam.sdk.idl.programs.glam.kamino.gen.types;

import software.sava.core.accounts.PublicKey;
import software.sava.core.borsh.Borsh;

public record LendingPolicy(PublicKey[] marketsAllowlist, PublicKey[] borrowAllowlist) implements Borsh {

  public static LendingPolicy read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var marketsAllowlist = Borsh.readPublicKeyVector(_data, i);
    i += Borsh.lenVector(marketsAllowlist);
    final var borrowAllowlist = Borsh.readPublicKeyVector(_data, i);
    return new LendingPolicy(marketsAllowlist, borrowAllowlist);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    i += Borsh.writeVector(marketsAllowlist, _data, i);
    i += Borsh.writeVector(borrowAllowlist, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return Borsh.lenVector(marketsAllowlist) + Borsh.lenVector(borrowAllowlist);
  }
}
