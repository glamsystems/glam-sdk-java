package systems.glam.services.state;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import systems.glam.sdk.GlamEnv;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.AccountType;

import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

/// `equals` decides whether a re-fetched state account is a change worth
/// propagating, so every component it compares needs a case that differs in
/// exactly that component — otherwise a dropped comparison silently reports
/// "unchanged" and listeners never see the update.
final class MinGlamStateAccountEqualityTests {

  private static PublicKey key(final int id) {
    final byte[] bytes = new byte[PublicKey.PUBLIC_KEY_LENGTH];
    bytes[0] = (byte) id;
    bytes[31] = (byte) id;
    return PublicKey.createPubKey(bytes);
  }

  private record Fields(long slot,
                        byte[] data,
                        GlamEnv glamEnv,
                        AccountType accountType,
                        boolean enabled,
                        int baseAssetIndex,
                        int baseAssetDecimals,
                        int baseAssetTokenProgram,
                        PublicKey[] assets,
                        ProtocolIntegration[] protocolIntegrations,
                        PublicKey[] delegates,
                        PublicKey[] externalPositions) {

    MinGlamStateAccount create() {
      return new MinGlamStateAccount(
          slot, data, glamEnv, accountType, enabled,
          baseAssetIndex, baseAssetDecimals, baseAssetTokenProgram,
          assets, protocolIntegrations, delegates, externalPositions
      );
    }
  }

  private static Fields base() {
    return new Fields(
        100L, new byte[]{1, 2, 3},
        GlamEnv.PRODUCTION, AccountType.TokenizedVault, true,
        1, 6, 0,
        new PublicKey[]{key(1), key(2)},
        new ProtocolIntegration[]{new ProtocolIntegration(key(3), 7)},
        new PublicKey[]{key(4)},
        new PublicKey[]{key(5)}
    );
  }

  private static void assertDiffers(final String component, final UnaryOperator<Fields> change) {
    final var account = base().create();
    final var other = change.apply(base()).create();
    assertNotEquals(account, other, component);
    assertNotEquals(other, account, component + " (symmetric)");
    // the hash has to mix every compared component too, or these accounts
    // collide in every map that caches them
    assertNotEquals(account.hashCode(), other.hashCode(), component + " hashCode");
  }

  @Test
  void everyComparedComponentIsObserved() {
    assertDiffers("glamEnv", f -> new Fields(f.slot(), f.data(), GlamEnv.STAGING, f.accountType(), f.enabled(),
        f.baseAssetIndex(), f.baseAssetDecimals(), f.baseAssetTokenProgram(),
        f.assets(), f.protocolIntegrations(), f.delegates(), f.externalPositions()));
    assertDiffers("accountType", f -> new Fields(f.slot(), f.data(), f.glamEnv(), AccountType.Vault, f.enabled(),
        f.baseAssetIndex(), f.baseAssetDecimals(), f.baseAssetTokenProgram(),
        f.assets(), f.protocolIntegrations(), f.delegates(), f.externalPositions()));
    assertDiffers("enabled", f -> new Fields(f.slot(), f.data(), f.glamEnv(), f.accountType(), false,
        f.baseAssetIndex(), f.baseAssetDecimals(), f.baseAssetTokenProgram(),
        f.assets(), f.protocolIntegrations(), f.delegates(), f.externalPositions()));
    assertDiffers("baseAssetIndex", f -> new Fields(f.slot(), f.data(), f.glamEnv(), f.accountType(), f.enabled(),
        99, f.baseAssetDecimals(), f.baseAssetTokenProgram(),
        f.assets(), f.protocolIntegrations(), f.delegates(), f.externalPositions()));
    assertDiffers("baseAssetDecimals", f -> new Fields(f.slot(), f.data(), f.glamEnv(), f.accountType(), f.enabled(),
        f.baseAssetIndex(), 9, f.baseAssetTokenProgram(),
        f.assets(), f.protocolIntegrations(), f.delegates(), f.externalPositions()));
    assertDiffers("baseAssetTokenProgram", f -> new Fields(f.slot(), f.data(), f.glamEnv(), f.accountType(), f.enabled(),
        f.baseAssetIndex(), f.baseAssetDecimals(), 1,
        f.assets(), f.protocolIntegrations(), f.delegates(), f.externalPositions()));
    assertDiffers("assets", f -> new Fields(f.slot(), f.data(), f.glamEnv(), f.accountType(), f.enabled(),
        f.baseAssetIndex(), f.baseAssetDecimals(), f.baseAssetTokenProgram(),
        new PublicKey[]{key(1), key(9)}, f.protocolIntegrations(), f.delegates(), f.externalPositions()));
    assertDiffers("protocolIntegrations", f -> new Fields(f.slot(), f.data(), f.glamEnv(), f.accountType(), f.enabled(),
        f.baseAssetIndex(), f.baseAssetDecimals(), f.baseAssetTokenProgram(),
        f.assets(), new ProtocolIntegration[]{new ProtocolIntegration(key(3), 8)}, f.delegates(), f.externalPositions()));
    assertDiffers("delegates", f -> new Fields(f.slot(), f.data(), f.glamEnv(), f.accountType(), f.enabled(),
        f.baseAssetIndex(), f.baseAssetDecimals(), f.baseAssetTokenProgram(),
        f.assets(), f.protocolIntegrations(), new PublicKey[]{key(8)}, f.externalPositions()));
    assertDiffers("externalPositions", f -> new Fields(f.slot(), f.data(), f.glamEnv(), f.accountType(), f.enabled(),
        f.baseAssetIndex(), f.baseAssetDecimals(), f.baseAssetTokenProgram(),
        f.assets(), f.protocolIntegrations(), f.delegates(), new PublicKey[]{key(7)}));
  }

  @Test
  void slotAndDataAreDeliberatelyExcluded() {
    // the same state re-fetched at a later slot is not a change: only the
    // decoded fields decide, so listeners are not woken for a no-op refresh
    final var account = base().create();
    final var later = new Fields(
        999L, new byte[]{9, 9}, GlamEnv.PRODUCTION, AccountType.TokenizedVault, true,
        1, 6, 0,
        new PublicKey[]{key(1), key(2)},
        new ProtocolIntegration[]{new ProtocolIntegration(key(3), 7)},
        new PublicKey[]{key(4)},
        new PublicKey[]{key(5)}
    ).create();
    assertEquals(account, later);
    assertEquals(account.hashCode(), later.hashCode());
  }

  @Test
  void equalsHandlesItselfAndForeignTypes() {
    final var account = base().create();
    assertEquals(account, account);
    assertNotEquals(account, new Object());
    assertNotEquals(account, null);
  }
}
