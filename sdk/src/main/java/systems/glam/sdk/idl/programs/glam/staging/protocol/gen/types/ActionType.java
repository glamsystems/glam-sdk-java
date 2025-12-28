package systems.glam.sdk.idl.programs.glam.staging.protocol.gen.types;

import software.sava.idl.clients.core.gen.RustEnum;
import software.sava.idl.clients.core.gen.SerDeUtil;

public enum ActionType implements RustEnum {

  AddExternalAccount,
  DeleteExternalAccount,
  DeleteExternalAccountIfZeroLamports,
  DeleteExternalAccountIfZeroBalance,
  AddAsset,
  DeleteAsset;

  public static ActionType read(final byte[] _data, final int _offset) {
    return SerDeUtil.read(1, ActionType.values(), _data, _offset);
  }
}