package systems.glam.sdk.idl.programs.glam.mint.gen.types;

import software.sava.core.accounts.PublicKey;
import software.sava.core.borsh.Borsh;

import static software.sava.core.encoding.ByteUtil.getInt32LE;
import static software.sava.core.encoding.ByteUtil.getInt64LE;
import static software.sava.core.encoding.ByteUtil.putInt32LE;
import static software.sava.core.encoding.ByteUtil.putInt64LE;

public record MintPolicy(int lockupPeriod,
                         long maxCap,
                         long minSubscription,
                         long minRedemption,
                         long reserved,
                         PublicKey[] allowlist,
                         PublicKey[] blocklist) implements Borsh {

  public static MintPolicy read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var lockupPeriod = getInt32LE(_data, i);
    i += 4;
    final var maxCap = getInt64LE(_data, i);
    i += 8;
    final var minSubscription = getInt64LE(_data, i);
    i += 8;
    final var minRedemption = getInt64LE(_data, i);
    i += 8;
    final var reserved = getInt64LE(_data, i);
    i += 8;
    final PublicKey[] allowlist;
    if (_data[i] == 0) {
      allowlist = null;
      ++i;
    } else {
      ++i;
      allowlist = Borsh.readPublicKeyVector(_data, i);
      i += Borsh.lenVector(allowlist);
    }
    final PublicKey[] blocklist;
    if (_data[i] == 0) {
      blocklist = null;
    } else {
      ++i;
      blocklist = Borsh.readPublicKeyVector(_data, i);
    }
    return new MintPolicy(lockupPeriod,
                          maxCap,
                          minSubscription,
                          minRedemption,
                          reserved,
                          allowlist,
                          blocklist);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    putInt32LE(_data, i, lockupPeriod);
    i += 4;
    putInt64LE(_data, i, maxCap);
    i += 8;
    putInt64LE(_data, i, minSubscription);
    i += 8;
    putInt64LE(_data, i, minRedemption);
    i += 8;
    putInt64LE(_data, i, reserved);
    i += 8;
    if (allowlist == null || allowlist.length == 0) {
      _data[i++] = 0;
    } else {
      _data[i++] = 1;
      i += Borsh.writeVector(allowlist, _data, i);
    }
    if (blocklist == null || blocklist.length == 0) {
      _data[i++] = 0;
    } else {
      _data[i++] = 1;
      i += Borsh.writeVector(blocklist, _data, i);
    }
    return i - _offset;
  }

  @Override
  public int l() {
    return 4
         + 8
         + 8
         + 8
         + 8
         + (allowlist == null || allowlist.length == 0 ? 1 : (1 + Borsh.lenVector(allowlist)))
         + (blocklist == null || blocklist.length == 0 ? 1 : (1 + Borsh.lenVector(blocklist)));
  }
}
