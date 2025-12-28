package systems.glam.sdk.idl.programs.glam.staging.protocol.gen.types;

import software.sava.idl.clients.core.gen.RustEnum;
import software.sava.idl.clients.core.gen.SerDeUtil;

public enum TimeUnit implements RustEnum {

  Second,
  Slot;

  public static TimeUnit read(final byte[] _data, final int _offset) {
    return SerDeUtil.read(1, TimeUnit.values(), _data, _offset);
  }
}