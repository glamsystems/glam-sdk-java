package systems.glam.services.io;

import software.sava.core.accounts.PublicKey;
import software.sava.core.programs.Discriminator;

import java.util.function.BiFunction;

public record AccountData(byte[] data, PublicKey pubKey) {

  static final AccountData EMPTY = new AccountData(new byte[0], PublicKey.NONE);

  public static AccountData createData(final String fileName, final byte[] data) {
    final var key = fileName.substring(0, fileName.lastIndexOf('.'));
    return new AccountData(data, PublicKey.fromBase58Encoded(key));
  }

  public boolean isAccount() {
    return data.length > 0;
  }

  public boolean isAccount(final Discriminator discriminator, final int expectedLength) {
    return data.length == expectedLength && discriminator.equals(data, 0);
  }

  public <A> A read(final BiFunction<PublicKey, byte[], A> parser) {
    return parser.apply(pubKey, data);
  }
}
