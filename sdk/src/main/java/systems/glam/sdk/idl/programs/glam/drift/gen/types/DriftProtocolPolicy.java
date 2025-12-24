package systems.glam.sdk.idl.programs.glam.drift.gen.types;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

public record DriftProtocolPolicy(short[] spotMarketsAllowlist,
                                  short[] perpMarketsAllowlist,
                                  PublicKey[] borrowAllowlist) implements SerDe {

  public static DriftProtocolPolicy read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var spotMarketsAllowlist = SerDeUtil.readshortVector(4, _data, i);
    i += SerDeUtil.lenVector(4, spotMarketsAllowlist);
    final var perpMarketsAllowlist = SerDeUtil.readshortVector(4, _data, i);
    i += SerDeUtil.lenVector(4, perpMarketsAllowlist);
    final var borrowAllowlist = SerDeUtil.readPublicKeyVector(4, _data, i);
    return new DriftProtocolPolicy(spotMarketsAllowlist, perpMarketsAllowlist, borrowAllowlist);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    i += SerDeUtil.writeVector(4, spotMarketsAllowlist, _data, i);
    i += SerDeUtil.writeVector(4, perpMarketsAllowlist, _data, i);
    i += SerDeUtil.writeVector(4, borrowAllowlist, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return SerDeUtil.lenVector(4, spotMarketsAllowlist) + SerDeUtil.lenVector(4, perpMarketsAllowlist) + SerDeUtil.lenVector(4, borrowAllowlist);
  }
}
