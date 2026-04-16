package systems.glam.sdk.idl.programs.glam.staging.external_positions.gen.types;

import software.sava.idl.clients.core.gen.RustEnum;
import software.sava.idl.clients.core.gen.SerDeUtil;

/// Source of observation data.
/// Never reorder existing variants — append only.
public enum ExternalSourceType implements RustEnum {

  Trusted,
  Native;

  public static ExternalSourceType read(final byte[] _data, final int _offset) {
    return SerDeUtil.read(1, ExternalSourceType.values(), _data, _offset);
  }
}