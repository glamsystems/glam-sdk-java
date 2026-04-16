package systems.glam.sdk.idl.programs.glam.staging.external_positions.gen.types;

import software.sava.idl.clients.core.gen.RustEnum;
import software.sava.idl.clients.core.gen.SerDeUtil;

/// Custody kind for Native source positions.
/// Never reorder existing variants — append only.
public enum NativeCustodyKind implements RustEnum {

  SplToken,
  NativeSol;

  public static NativeCustodyKind read(final byte[] _data, final int _offset) {
    return SerDeUtil.read(1, NativeCustodyKind.values(), _data, _offset);
  }
}