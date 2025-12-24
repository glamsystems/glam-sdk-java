package systems.glam.sdk.idl.programs.glam.kamino.gen.types;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

public record VaultsPolicy(PublicKey[] vaultsAllowlist) implements SerDe {

  public static VaultsPolicy read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    final var vaultsAllowlist = SerDeUtil.readPublicKeyVector(4, _data, _offset);
    return new VaultsPolicy(vaultsAllowlist);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    i += SerDeUtil.writeVector(4, vaultsAllowlist, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return SerDeUtil.lenVector(4, vaultsAllowlist);
  }
}
