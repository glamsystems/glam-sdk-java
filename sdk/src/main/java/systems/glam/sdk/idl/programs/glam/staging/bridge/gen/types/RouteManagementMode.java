package systems.glam.sdk.idl.programs.glam.staging.bridge.gen.types;

import software.sava.idl.clients.core.gen.RustEnum;
import software.sava.idl.clients.core.gen.SerDeUtil;

public enum RouteManagementMode implements RustEnum {

  UnmanagedOnly,
  ManagedOnly,
  Either;

  public static RouteManagementMode read(final byte[] _data, final int _offset) {
    return SerDeUtil.read(1, RouteManagementMode.values(), _data, _offset);
  }
}