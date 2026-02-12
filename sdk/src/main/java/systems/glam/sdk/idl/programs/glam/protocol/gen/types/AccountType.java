package systems.glam.sdk.idl.programs.glam.protocol.gen.types;

import software.sava.idl.clients.core.gen.RustEnum;
import software.sava.idl.clients.core.gen.SerDeUtil;

public enum AccountType implements RustEnum {

  Vault,
  TokenizedVault,
  Mint,
  SingleAssetVault;

  public static AccountType read(final byte[] _data, final int _offset) {
    return SerDeUtil.read(1, AccountType.values(), _data, _offset);
  }
}