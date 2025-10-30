package systems.glam.sdk.idl.programs.glam.protocol.gen.types;

import software.sava.core.borsh.Borsh;

public enum ValuationModel implements Borsh.Enum {

  Continuous,
  Periodic;

  public static ValuationModel read(final byte[] _data, final int _offset) {
    return Borsh.read(ValuationModel.values(), _data, _offset);
  }
}