package systems.glam.sdk.idl.programs.glam.staging.loopscale.gen.types;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

public record LoopscalePolicy(PublicKey[] depositAllowlist,
                              PublicKey[] borrowAllowlist,
                              PublicKey[] marketsAllowlist) implements SerDe {

  public static final int DEPOSIT_ALLOWLIST_OFFSET = 0;

  public static LoopscalePolicy read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var depositAllowlist = SerDeUtil.readPublicKeyVector(4, _data, i);
    i += SerDeUtil.lenVector(4, depositAllowlist);
    final var borrowAllowlist = SerDeUtil.readPublicKeyVector(4, _data, i);
    i += SerDeUtil.lenVector(4, borrowAllowlist);
    final var marketsAllowlist = SerDeUtil.readPublicKeyVector(4, _data, i);
    return new LoopscalePolicy(depositAllowlist, borrowAllowlist, marketsAllowlist);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    i += SerDeUtil.writeVector(4, depositAllowlist, _data, i);
    i += SerDeUtil.writeVector(4, borrowAllowlist, _data, i);
    i += SerDeUtil.writeVector(4, marketsAllowlist, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return SerDeUtil.lenVector(4, depositAllowlist) + SerDeUtil.lenVector(4, borrowAllowlist) + SerDeUtil.lenVector(4, marketsAllowlist);
  }
}
