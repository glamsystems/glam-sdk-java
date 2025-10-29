package systems.glam.sdk.idl.programs.glam.protocol.gen.types;

import software.sava.core.borsh.Borsh;

public enum ActionType implements Borsh.Enum {

  AddExternalAccount,
  DeleteExternalAccount,
  DeleteExternalAccountIfZeroLamports,
  DeleteExternalAccountIfZeroBalance,
  AddAsset,
  DeleteAsset;

  public static ActionType read(final byte[] _data, final int _offset) {
    return Borsh.read(ActionType.values(), _data, _offset);
  }
}