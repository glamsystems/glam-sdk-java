package systems.glam.sdk;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

final class GlamEnvTests {

  @Test
  void fromProtocolProgram() {
    assertSame(GlamEnv.PRODUCTION, GlamEnv.from(GlamAccounts.MAIN_NET.protocolProgram()));
    assertSame(GlamEnv.STAGING, GlamEnv.from(GlamAccounts.MAIN_NET_STAGING.protocolProgram()));
    // anything unrecognized falls through to STAGING
    assertSame(GlamEnv.STAGING, GlamEnv.from(PublicKey.NONE));
  }

  @Test
  void accessorsReflectWrappedAccounts() {
    assertSame(GlamAccounts.MAIN_NET, GlamEnv.PRODUCTION.glamAccounts());
    assertSame(GlamAccounts.MAIN_NET_STAGING, GlamEnv.STAGING.glamAccounts());
    assertEquals(
        PublicKey.fromBase58Encoded("GLAMpaME8wdTEzxtiYEAa5yD8fZbxZiz2hNtV58RZiEz"),
        GlamEnv.PRODUCTION.protocolProgram()
    );
    assertEquals(
        PublicKey.fromBase58Encoded("gstgptmbgJVi5f8ZmSRVZjZkDQwqKa3xWuUtD5WmJHz"),
        GlamEnv.STAGING.protocolProgram()
    );
  }
}
