package systems.glam.sdk.idl.programs.glam.protocol.gen.types;

import software.sava.idl.clients.core.gen.SerDe;

public record EngineField(EngineFieldName name, EngineFieldValue value) implements SerDe {

  public static EngineField read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var name = EngineFieldName.read(_data, i);
    i += name.l();
    final var value = EngineFieldValue.read(_data, i);
    return new EngineField(name, value);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    i += name.write(_data, i);
    i += value.write(_data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return name.l() + value.l();
  }
}
