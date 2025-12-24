package systems.glam.sdk.idl.programs.glam.protocol.gen.types;

import software.sava.idl.clients.core.gen.RustEnum;
import software.sava.idl.clients.core.gen.SerDeUtil;

public enum NoticePeriodType implements RustEnum {

  Hard,
  Soft;

  public static NoticePeriodType read(final byte[] _data, final int _offset) {
    return SerDeUtil.read(1, NoticePeriodType.values(), _data, _offset);
  }
}