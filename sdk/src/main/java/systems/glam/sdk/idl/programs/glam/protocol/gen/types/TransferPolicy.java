package systems.glam.sdk.idl.programs.glam.protocol.gen.types;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

public record TransferPolicy(PublicKey[] allowlist) implements SerDe {

  public static final int ALLOWLIST_OFFSET = 0;

  public static TransferPolicy read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    final var allowlist = SerDeUtil.readPublicKeyVector(4, _data, _offset);
    return new TransferPolicy(allowlist);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    i += SerDeUtil.writeVector(4, allowlist, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return SerDeUtil.lenVector(4, allowlist);
  }
}
