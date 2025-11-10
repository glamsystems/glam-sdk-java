package systems.glam.sdk.idl.programs.glam.drift.gen.types;

import software.sava.core.accounts.PublicKey;
import software.sava.core.borsh.Borsh;

public record DriftProtocolPolicy(short[] spotMarketsAllowlist,
                                  short[] perpMarketsAllowlist,
                                  PublicKey[] borrowAllowlist) implements Borsh {

  public static DriftProtocolPolicy read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var spotMarketsAllowlist = Borsh.readshortVector(_data, i);
    i += Borsh.lenVector(spotMarketsAllowlist);
    final var perpMarketsAllowlist = Borsh.readshortVector(_data, i);
    i += Borsh.lenVector(perpMarketsAllowlist);
    final var borrowAllowlist = Borsh.readPublicKeyVector(_data, i);
    return new DriftProtocolPolicy(spotMarketsAllowlist, perpMarketsAllowlist, borrowAllowlist);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    i += Borsh.writeVector(spotMarketsAllowlist, _data, i);
    i += Borsh.writeVector(perpMarketsAllowlist, _data, i);
    i += Borsh.writeVector(borrowAllowlist, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return Borsh.lenVector(spotMarketsAllowlist) + Borsh.lenVector(perpMarketsAllowlist) + Borsh.lenVector(borrowAllowlist);
  }
}
