package systems.glam.sdk.idl.programs.glam.mint.gen.types;

import software.sava.core.borsh.Borsh;

public record EmergencyUpdateMintArgs(RequestType requestType, boolean setPaused) implements Borsh {

  public static final int BYTES = 2;

  public static EmergencyUpdateMintArgs read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var requestType = RequestType.read(_data, i);
    i += Borsh.len(requestType);
    final var setPaused = _data[i] == 1;
    return new EmergencyUpdateMintArgs(requestType, setPaused);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    i += Borsh.write(requestType, _data, i);
    _data[i] = (byte) (setPaused ? 1 : 0);
    ++i;
    return i - _offset;
  }

  @Override
  public int l() {
    return BYTES;
  }
}
