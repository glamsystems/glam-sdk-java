package systems.glam.sdk.idl.programs.glam.staging.phoenix.gen.types;

import software.sava.idl.clients.core.gen.SerDe;

public record TraderCapabilityUpdate(int capability, boolean enabled) implements SerDe {

  public static final int BYTES = 2;

  public static final int CAPABILITY_OFFSET = 0;
  public static final int ENABLED_OFFSET = 1;

  public static TraderCapabilityUpdate read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var capability = _data[i] & 0xFF;
    ++i;
    final var enabled = _data[i] == 1;
    return new TraderCapabilityUpdate(capability, enabled);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    _data[i] = (byte) capability;
    ++i;
    _data[i] = (byte) (enabled ? 1 : 0);
    ++i;
    return i - _offset;
  }

  @Override
  public int l() {
    return BYTES;
  }
}
