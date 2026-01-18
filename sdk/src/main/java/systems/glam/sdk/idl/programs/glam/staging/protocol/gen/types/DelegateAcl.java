package systems.glam.sdk.idl.programs.glam.staging.protocol.gen.types;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

import static software.sava.core.accounts.PublicKey.readPubKey;
import static software.sava.core.encoding.ByteUtil.getInt64LE;
import static software.sava.core.encoding.ByteUtil.putInt64LE;

public record DelegateAcl(PublicKey pubkey,
                          IntegrationPermissions[] integrationPermissions,
                          long expiresAt) implements SerDe {

  public static final int PUBKEY_OFFSET = 0;
  public static final int INTEGRATION_PERMISSIONS_OFFSET = 32;

  public static DelegateAcl read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var pubkey = readPubKey(_data, i);
    i += 32;
    final var integrationPermissions = SerDeUtil.readVector(4, IntegrationPermissions.class, IntegrationPermissions::read, _data, i);
    i += SerDeUtil.lenVector(4, integrationPermissions);
    final var expiresAt = getInt64LE(_data, i);
    return new DelegateAcl(pubkey, integrationPermissions, expiresAt);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    pubkey.write(_data, i);
    i += 32;
    i += SerDeUtil.writeVector(4, integrationPermissions, _data, i);
    putInt64LE(_data, i, expiresAt);
    i += 8;
    return i - _offset;
  }

  @Override
  public int l() {
    return 32 + SerDeUtil.lenVector(4, integrationPermissions) + 8;
  }
}
