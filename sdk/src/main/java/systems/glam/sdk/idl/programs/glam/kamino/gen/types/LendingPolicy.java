package systems.glam.sdk.idl.programs.glam.kamino.gen.types;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

public record LendingPolicy(PublicKey[] marketsAllowlist, PublicKey[] borrowAllowlist) implements SerDe {

  public static final int MARKETS_ALLOWLIST_OFFSET = 0;

  public static LendingPolicy read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var marketsAllowlist = SerDeUtil.readPublicKeyVector(4, _data, i);
    i += SerDeUtil.lenVector(4, marketsAllowlist);
    final var borrowAllowlist = SerDeUtil.readPublicKeyVector(4, _data, i);
    return new LendingPolicy(marketsAllowlist, borrowAllowlist);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    i += SerDeUtil.writeVector(4, marketsAllowlist, _data, i);
    i += SerDeUtil.writeVector(4, borrowAllowlist, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return SerDeUtil.lenVector(4, marketsAllowlist) + SerDeUtil.lenVector(4, borrowAllowlist);
  }
}
