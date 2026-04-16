package systems.glam.sdk.idl.programs.glam.staging.external_positions.gen.types;

import software.sava.idl.clients.core.gen.RustEnum;
import software.sava.idl.clients.core.gen.SerDeUtil;

/// Denomination of an observation amount.
/// Never reorder existing variants — append only.
public enum Denomination implements RustEnum {

  Usd,
  Mint;

  public static Denomination read(final byte[] _data, final int _offset) {
    return SerDeUtil.read(1, Denomination.values(), _data, _offset);
  }
}