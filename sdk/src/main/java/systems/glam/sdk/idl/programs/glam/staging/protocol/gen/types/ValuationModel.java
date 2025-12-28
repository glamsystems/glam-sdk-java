package systems.glam.sdk.idl.programs.glam.staging.protocol.gen.types;

import software.sava.idl.clients.core.gen.RustEnum;
import software.sava.idl.clients.core.gen.SerDeUtil;

public enum ValuationModel implements RustEnum {

  Continuous,
  Periodic;

  public static ValuationModel read(final byte[] _data, final int _offset) {
    return SerDeUtil.read(1, ValuationModel.values(), _data, _offset);
  }
}