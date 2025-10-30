package systems.glam.sdk.idl.programs.glam.protocol.gen.types;

import software.sava.core.borsh.Borsh;

public enum TimeUnit implements Borsh.Enum {

  Second,
  Slot;

  public static TimeUnit read(final byte[] _data, final int _offset) {
    return Borsh.read(TimeUnit.values(), _data, _offset);
  }
}