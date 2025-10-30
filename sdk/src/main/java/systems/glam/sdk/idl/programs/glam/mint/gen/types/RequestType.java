package systems.glam.sdk.idl.programs.glam.mint.gen.types;

import software.sava.core.borsh.Borsh;

public enum RequestType implements Borsh.Enum {

  Subscription,
  Redemption;

  public static RequestType read(final byte[] _data, final int _offset) {
    return Borsh.read(RequestType.values(), _data, _offset);
  }
}