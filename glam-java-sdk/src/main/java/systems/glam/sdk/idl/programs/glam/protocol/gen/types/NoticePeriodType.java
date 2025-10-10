package systems.glam.sdk.idl.programs.glam.protocol.gen.types;

import software.sava.core.borsh.Borsh;

public enum NoticePeriodType implements Borsh.Enum {

  Hard,
  Soft;

  public static NoticePeriodType read(final byte[] _data, final int offset) {
    return Borsh.read(NoticePeriodType.values(), _data, offset);
  }
}