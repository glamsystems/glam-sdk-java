package systems.glam.sdk.idl.programs.glam.protocol.gen.types;

import java.lang.Boolean;

import software.sava.core.accounts.PublicKey;
import software.sava.core.borsh.Borsh;

public record EmergencyAccessUpdateArgs(PublicKey[] disabledIntegrations,
                                        PublicKey[] disabledDelegates,
                                        Boolean stateEnabled) implements Borsh {

  public static EmergencyAccessUpdateArgs read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var disabledIntegrations = Borsh.readPublicKeyVector(_data, i);
    i += Borsh.lenVector(disabledIntegrations);
    final var disabledDelegates = Borsh.readPublicKeyVector(_data, i);
    i += Borsh.lenVector(disabledDelegates);
    final Boolean stateEnabled;
    if (_data[i] == 0) {
      stateEnabled = null;
    } else {
      ++i;
      stateEnabled = _data[i] == 1;
    }
    return new EmergencyAccessUpdateArgs(disabledIntegrations, disabledDelegates, stateEnabled);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    i += Borsh.writeVector(disabledIntegrations, _data, i);
    i += Borsh.writeVector(disabledDelegates, _data, i);
    i += Borsh.writeOptional(stateEnabled, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return Borsh.lenVector(disabledIntegrations) + Borsh.lenVector(disabledDelegates) + (stateEnabled == null ? 1 : (1 + 1));
  }
}
