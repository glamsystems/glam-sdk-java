package systems.glam.sdk.idl.programs.glam.staging.loopscale.gen.types;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

public record LoopscalePolicy(PublicKey[] strategiesAllowlist) implements SerDe {

  public static final int STRATEGIES_ALLOWLIST_OFFSET = 0;

  public static LoopscalePolicy read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    final var strategiesAllowlist = SerDeUtil.readPublicKeyVector(4, _data, _offset);
    return new LoopscalePolicy(strategiesAllowlist);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    i += SerDeUtil.writeVector(4, strategiesAllowlist, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return SerDeUtil.lenVector(4, strategiesAllowlist);
  }
}
