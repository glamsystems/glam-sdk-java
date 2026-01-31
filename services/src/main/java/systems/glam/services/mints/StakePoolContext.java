package systems.glam.services.mints;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.idl.clients.core.gen.SerDe;

public record StakePoolContext(PublicKey program, AccountMeta readState, PublicKey mintKey) implements SerDe {

  public PublicKey stateKey() {
    return readState.publicKey();
  }

  public static final int BYTES = PublicKey.PUBLIC_KEY_LENGTH << 1;

  static StakePoolContext createContext(final PublicKey program, final PublicKey stateKey, final PublicKey mintKey) {
    return new StakePoolContext(program, AccountMeta.createRead(stateKey), mintKey);
  }

  static StakePoolContext read(final PublicKey program, final byte[] data, final int offset) {
    return createContext(
        program,
        PublicKey.readPubKey(data, offset),
        PublicKey.readPubKey(data, offset + PublicKey.PUBLIC_KEY_LENGTH)
    );
  }

  @Override
  public int l() {
    return BYTES;
  }

  @Override
  public int write(final byte[] bytes, final int i) {
    stateKey().write(bytes, i);
    mintKey.write(bytes, i + PublicKey.PUBLIC_KEY_LENGTH);
    return BYTES;
  }
}
