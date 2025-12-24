package systems.glam.sdk.idl.programs.glam.mint.gen.types;

import software.sava.idl.clients.core.gen.RustEnum;
import software.sava.idl.clients.core.gen.SerDeUtil;

public enum RequestType implements RustEnum {

  Subscription,
  Redemption;

  public static RequestType read(final byte[] _data, final int _offset) {
    return SerDeUtil.read(1, RequestType.values(), _data, _offset);
  }
}