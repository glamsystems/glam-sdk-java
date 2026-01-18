package systems.glam.sdk.idl.programs.glam.staging.mint.gen.types;

import software.sava.idl.clients.core.gen.SerDe;

public record EmergencyUpdateMintArgs(RequestType requestType, boolean setPaused) implements SerDe {

  public static final int BYTES = 2;

  public static final int REQUEST_TYPE_OFFSET = 0;
  public static final int SET_PAUSED_OFFSET = 1;

  public static EmergencyUpdateMintArgs read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var requestType = RequestType.read(_data, i);
    i += requestType.l();
    final var setPaused = _data[i] == 1;
    return new EmergencyUpdateMintArgs(requestType, setPaused);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    i += requestType.write(_data, i);
    _data[i] = (byte) (setPaused ? 1 : 0);
    ++i;
    return i - _offset;
  }

  @Override
  public int l() {
    return BYTES;
  }
}
