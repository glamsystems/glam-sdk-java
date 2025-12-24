package systems.glam.sdk.idl.programs.glam.protocol.gen.types;

import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

/// Vault-specific oracle configs. If available, these configs are preferred over the global config.
///
public record OracleConfigs(short[][] maxAgesSeconds, byte[] padding) implements SerDe {

  public static final int PADDING_LEN = 12;
  public static OracleConfigs read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var maxAgesSeconds = SerDeUtil.readMultiDimensionshortVectorArray(4, 2, _data, i);
    i += SerDeUtil.lenVectorArray(4, maxAgesSeconds);
    final var padding = new byte[12];
    SerDeUtil.readArray(padding, _data, i);
    return new OracleConfigs(maxAgesSeconds, padding);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    i += SerDeUtil.writeVectorArrayChecked(4, maxAgesSeconds, 2, _data, i);
    i += SerDeUtil.writeArrayChecked(padding, 12, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return SerDeUtil.lenVectorArray(4, maxAgesSeconds) + SerDeUtil.lenArray(padding);
  }
}
