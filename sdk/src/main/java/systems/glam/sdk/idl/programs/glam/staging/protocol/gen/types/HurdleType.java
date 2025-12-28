package systems.glam.sdk.idl.programs.glam.staging.protocol.gen.types;

import software.sava.idl.clients.core.gen.RustEnum;
import software.sava.idl.clients.core.gen.SerDeUtil;

public enum HurdleType implements RustEnum {

  Hard,
  Soft;

  public static HurdleType read(final byte[] _data, final int _offset) {
    return SerDeUtil.read(1, HurdleType.values(), _data, _offset);
  }
}