package systems.glam.sdk.idl.programs.glam.protocol.gen.types;

import software.sava.core.borsh.Borsh;

public enum AccountType implements Borsh.Enum {

  Vault,
  TokenizedVault,
  Mint;

  public static AccountType read(final byte[] _data, final int _offset) {
    return Borsh.read(AccountType.values(), _data, _offset);
  }
}