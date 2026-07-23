package systems.glam.services.io;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.programs.Discriminator;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

final class AccountDataTests {

  private static final Discriminator DISCRIMINATOR = Discriminator.toDiscriminator(1, 2, 3, 4, 5, 6, 7, 8);
  private static final PublicKey KEY = PublicKey.createPubKey(new byte[PublicKey.PUBLIC_KEY_LENGTH]);

  private static AccountData data(final int length, final boolean rightDiscriminator) {
    final byte[] data = new byte[length];
    if (rightDiscriminator) {
      System.arraycopy(DISCRIMINATOR.data(), 0, data, 0, 8);
    } else {
      Arrays.fill(data, 0, Math.min(8, length), (byte) 9);
    }
    return new AccountData(data, KEY);
  }

  @Test
  void exactRequiresTheLengthAndTheDiscriminator() {
    assertTrue(data(64, true).isAccountExact(DISCRIMINATOR, 64));
    assertFalse(data(65, true).isAccountExact(DISCRIMINATOR, 64));
    assertFalse(data(63, true).isAccountExact(DISCRIMINATOR, 64));
    assertFalse(data(64, false).isAccountExact(DISCRIMINATOR, 64));
  }

  @Test
  void atLeastAcceptsLongerButNeverShorterData() {
    assertTrue(data(64, true).isAccountAtLeast(DISCRIMINATOR, 64));
    assertTrue(data(65, true).isAccountAtLeast(DISCRIMINATOR, 64));
    assertFalse(data(63, true).isAccountAtLeast(DISCRIMINATOR, 64));
    assertFalse(data(64, false).isAccountAtLeast(DISCRIMINATOR, 64));
  }

  @Test
  void createDataParsesTheKeyFromTheFileName() {
    final var key = PublicKey.fromBase58Encoded("So11111111111111111111111111111111111111112");
    final var accountData = AccountData.createData(key.toBase58() + ".dat", new byte[]{1});
    assertEquals(key, accountData.pubKey());
    assertTrue(accountData.isAccount());
    assertFalse(AccountData.EMPTY.isAccount());
  }
}
