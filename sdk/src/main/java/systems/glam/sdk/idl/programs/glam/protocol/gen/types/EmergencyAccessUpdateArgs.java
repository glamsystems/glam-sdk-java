package systems.glam.sdk.idl.programs.glam.protocol.gen.types;

import java.lang.Boolean;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

public record EmergencyAccessUpdateArgs(PublicKey[] disabledIntegrations,
                                        PublicKey[] disabledDelegates,
                                        Boolean stateEnabled) implements SerDe {

  public static final int DISABLED_INTEGRATIONS_OFFSET = 0;

  public static EmergencyAccessUpdateArgs read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var disabledIntegrations = SerDeUtil.readPublicKeyVector(4, _data, i);
    i += SerDeUtil.lenVector(4, disabledIntegrations);
    final var disabledDelegates = SerDeUtil.readPublicKeyVector(4, _data, i);
    i += SerDeUtil.lenVector(4, disabledDelegates);
    final Boolean stateEnabled;
    if (SerDeUtil.isAbsent(1, _data, i)) {
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
    i += SerDeUtil.writeVector(4, disabledIntegrations, _data, i);
    i += SerDeUtil.writeVector(4, disabledDelegates, _data, i);
    i += SerDeUtil.writeOptional(1, stateEnabled, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return SerDeUtil.lenVector(4, disabledIntegrations) + SerDeUtil.lenVector(4, disabledDelegates) + (stateEnabled == null ? 1 : (1 + 1));
  }
}
