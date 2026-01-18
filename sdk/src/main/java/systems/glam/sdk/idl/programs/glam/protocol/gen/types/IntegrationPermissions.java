package systems.glam.sdk.idl.programs.glam.protocol.gen.types;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

import static software.sava.core.accounts.PublicKey.readPubKey;

/// Stores delegate permissions for an integration program.
///
public record IntegrationPermissions(PublicKey integrationProgram, ProtocolPermissions[] protocolPermissions) implements SerDe {

  public static final int INTEGRATION_PROGRAM_OFFSET = 0;
  public static final int PROTOCOL_PERMISSIONS_OFFSET = 32;

  public static IntegrationPermissions read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var integrationProgram = readPubKey(_data, i);
    i += 32;
    final var protocolPermissions = SerDeUtil.readVector(4, ProtocolPermissions.class, ProtocolPermissions::read, _data, i);
    return new IntegrationPermissions(integrationProgram, protocolPermissions);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    integrationProgram.write(_data, i);
    i += 32;
    i += SerDeUtil.writeVector(4, protocolPermissions, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return 32 + SerDeUtil.lenVector(4, protocolPermissions);
  }
}
