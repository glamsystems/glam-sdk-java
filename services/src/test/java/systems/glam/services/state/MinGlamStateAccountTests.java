package systems.glam.services.state;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.Context;
import systems.glam.sdk.GlamAccounts;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.AccountType;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateAccount;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

final class MinGlamStateAccountTests {

  private static final PublicKey STATE_ACCOUNT_KEY = PublicKey.fromBase58Encoded("3H7XbyVaYusyzQCncfRSBx3zgvfmjGG7wrr3ARtXF1o7");

  @Test
  void minStateAccountSerialization() {
    final byte[] stateAccountData = Base64.getDecoder().decode("""
        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AAAAAAAAAAAAgFEBAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACw4AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAgFEBAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAgFEBAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACw4AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAgFEBAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==
        """.stripTrailing());
    final var stateAccount = StateAccount.read(STATE_ACCOUNT_KEY, stateAccountData);
    validateStateAccount(stateAccount);

    long slot = System.currentTimeMillis();
    var accountInfo = new AccountInfo<>(
        stateAccount._address(),
        new Context(slot, null),
        false,
        0,
        GlamAccounts.MAIN_NET.protocolProgram(),
        BigInteger.ZERO,
        0,
        stateAccountData
    );
    final var minStateAccount = MinGlamStateAccount.createRecord(accountInfo);
    assertEquals(slot, minStateAccount.slot());
    assertEquals(stateAccount.accountType(), minStateAccount.accountType());
    assertEquals(stateAccount.baseAssetMint(), minStateAccount.baseAssetMint());
    assertEquals(stateAccount.baseAssetDecimals(), minStateAccount.baseAssetDecimals());

    final var sortedAssets = Arrays.copyOf(stateAccount.assets(), stateAccount.assets().length);
    Arrays.sort(sortedAssets);
    assertArrayEquals(sortedAssets, minStateAccount.assets());

    final var sortedExternalPositions = Arrays.copyOf(stateAccount.externalPositions(), stateAccount.externalPositions().length);
    Arrays.sort(sortedExternalPositions);
    assertArrayEquals(sortedExternalPositions, minStateAccount.externalPositions());

    final byte[] serialized = minStateAccount.serialize();
    final var deserialized = MinGlamStateAccount.deserialize(serialized);
    assertEquals(minStateAccount, deserialized);

    assertEquals(slot, deserialized.slot());
    validateBaseNotChanged(minStateAccount, deserialized);
    assertArrayEquals(minStateAccount.assets(), deserialized.assets());
    assertArrayEquals(minStateAccount.assetBytes(), deserialized.assetBytes());
    assertArrayEquals(minStateAccount.externalPositions(), deserialized.externalPositions());
    assertArrayEquals(minStateAccount.externalPositionsBytes(), deserialized.externalPositionsBytes());

    accountInfo = new AccountInfo<>(
        STATE_ACCOUNT_KEY,
        new Context(slot, null),
        false,
        0,
        GlamAccounts.MAIN_NET.protocolProgram(),
        BigInteger.ZERO,
        0,
        null
    );
    assertNull(minStateAccount.createIfChanged(accountInfo));
    accountInfo = new AccountInfo<>(
        STATE_ACCOUNT_KEY,
        new Context(slot + 1, null),
        false,
        0,
        GlamAccounts.MAIN_NET.protocolProgram(),
        BigInteger.ZERO,
        0,
        stateAccountData
    );
    assertNull(minStateAccount.createIfChanged(accountInfo));

    final var changedAssets = stateAccount.assets().clone();
    changedAssets[0] = PublicKey.fromBase58Encoded("11111111111111111111111111111111");
    final var stateAccountWithChangedAssets = new StateAccount(
        stateAccount._address(),
        stateAccount.discriminator(),
        stateAccount.accountType(),
        stateAccount.enabled(),
        stateAccount.vault(),
        stateAccount.owner(),
        stateAccount.portfolioManagerName(),
        stateAccount.created(),
        stateAccount.baseAssetMint(),
        stateAccount.baseAssetDecimals(),
        stateAccount.baseAssetTokenProgram(),
        stateAccount.name(),
        stateAccount.timelockDuration(),
        stateAccount.timelockExpiresAt(),
        stateAccount.mint(),
        changedAssets,
        stateAccount.integrationAcls(),
        stateAccount.delegateAcls(),
        stateAccount.externalPositions(),
        stateAccount.pricedProtocols(),
        stateAccount.params()
    );

    byte[] data = stateAccountWithChangedAssets.write();
    ++slot;
    accountInfo = new AccountInfo<>(
        STATE_ACCOUNT_KEY,
        new Context(slot, null),
        false,
        0,
        GlamAccounts.MAIN_NET.protocolProgram(),
        BigInteger.ZERO,
        0,
        data
    );
    var changed = minStateAccount.createIfChanged(accountInfo);
    assertNotNull(changed);
    assertEquals(slot, changed.slot());
    validateBaseNotChanged(minStateAccount, deserialized);
    Arrays.sort(changedAssets);
    assertArrayEquals(changedAssets, changed.assets());
    assertArrayEquals(minStateAccount.externalPositions(), changed.externalPositions());
    assertArrayEquals(minStateAccount.externalPositionsBytes(), changed.externalPositionsBytes());

    final var changedExternalPositions = stateAccount.externalPositions().clone();
    changedExternalPositions[0] = PublicKey.fromBase58Encoded("11111111111111111111111111111111");
    final var stateAccountWithChangedPositions = new StateAccount(
        stateAccount._address(),
        stateAccount.discriminator(),
        stateAccount.accountType(),
        stateAccount.enabled(),
        stateAccount.vault(),
        stateAccount.owner(),
        stateAccount.portfolioManagerName(),
        stateAccount.created(),
        stateAccount.baseAssetMint(),
        stateAccount.baseAssetDecimals(),
        stateAccount.baseAssetTokenProgram(),
        stateAccount.name(),
        stateAccount.timelockDuration(),
        stateAccount.timelockExpiresAt(),
        stateAccount.mint(),
        stateAccount.assets(),
        stateAccount.integrationAcls(),
        stateAccount.delegateAcls(),
        changedExternalPositions,
        stateAccount.pricedProtocols(),
        stateAccount.params()
    );

    data = stateAccountWithChangedPositions.write();
    ++slot;
    accountInfo = new AccountInfo<>(
        STATE_ACCOUNT_KEY,
        new Context(slot, null),
        false,
        0,
        GlamAccounts.MAIN_NET.protocolProgram(),
        BigInteger.ZERO,
        0,
        data
    );
    changed = minStateAccount.createIfChanged(accountInfo);
    assertNotNull(changed);
    assertEquals(slot, changed.slot());
    validateBaseNotChanged(minStateAccount, deserialized);
    assertArrayEquals(minStateAccount.assets(), changed.assets());
    assertArrayEquals(minStateAccount.assetBytes(), changed.assetBytes());
    Arrays.sort(changedExternalPositions);
    assertArrayEquals(changedExternalPositions, changed.externalPositions());
  }

  private static String trimmedAscii(final byte[] bytes) {
    int len = 0;
    while (len < bytes.length && bytes[len] != 0) {
      ++len;
    }
    return new String(bytes, 0, len, StandardCharsets.UTF_8);
  }

  private void validateStateAccount(final StateAccount stateAccount) {
    assertEquals(AccountType.TokenizedVault, stateAccount.accountType());
    assertTrue(stateAccount.enabled());
    assertEquals(PublicKey.fromBase58Encoded("EMou4Rxje9ddgFubx92Grg3doP2vvKrxJiGdyiv6jxQY"), stateAccount.vault());
    assertEquals(PublicKey.fromBase58Encoded("yuru1ARL4bcmSFpufUCdCrF4joamZRui9CawdgE4ZCW"), stateAccount.owner());

    final byte[] expectedPortfolioManagerName = new byte[]{
        89, 83, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    };
    assertArrayEquals(expectedPortfolioManagerName, stateAccount.portfolioManagerName());

    final var created = stateAccount.created();
    assertNotNull(created);
    assertArrayEquals(new byte[]{(byte) 239, (byte) 191, (byte) 189, (byte) 239, (byte) 191, (byte) 189, 126, 1}, created.key());
    assertEquals(PublicKey.fromBase58Encoded("yuru1ARL4bcmSFpufUCdCrF4joamZRui9CawdgE4ZCW"), created.createdBy());
    assertEquals(1771031674L, created.createdAt());

    assertEquals(PublicKey.fromBase58Encoded("So11111111111111111111111111111111111111112"), stateAccount.baseAssetMint());
    assertEquals(9, stateAccount.baseAssetDecimals());
    assertEquals(0, stateAccount.baseAssetTokenProgram());

    assertEquals("LST Yield", trimmedAscii(stateAccount.name()));
    assertEquals(0, stateAccount.timelockDuration());
    assertEquals(0L, stateAccount.timelockExpiresAt());
    assertEquals(PublicKey.fromBase58Encoded("FNL47CVnjoso6eZPkVi2RSAdCM49HgGbh3P4UrgY3DpR"), stateAccount.mint());

    assertEquals(5, stateAccount.assets().length);
    final PublicKey[] expectedAssets = {
        PublicKey.fromBase58Encoded("So11111111111111111111111111111111111111112"),
        PublicKey.fromBase58Encoded("Dso1bDeDjCQxTrWHqUUi63oBvV7Mdm6WaobLbQ7gnPQ"),
        PublicKey.fromBase58Encoded("jupSoLaHXQiZZTSfEWMTRRgpnyFm8f6sZdosWBjx93v"),
        PublicKey.fromBase58Encoded("BonK1YhkXEGLZzwtcvRTip3gAL9nCeQD7ppZBLXhtTs"),
        PublicKey.fromBase58Encoded("he1iusmfkpAdwvxLNGV8Y1iSbj4rUy6yMhEA3fotn9A")
    };
    assertArrayEquals(expectedAssets, stateAccount.assets());

    final var integrationAcls = stateAccount.integrationAcls();
    assertEquals(6, integrationAcls.length);

    assertEquals(PublicKey.fromBase58Encoded("gstgm1M39mhgnvgyScGUDRwNn5kNVSd97hTtyow1Et5"), integrationAcls[0].integrationProgram());
    assertEquals(1, integrationAcls[0].protocolsBitmask());
    assertEquals(1, integrationAcls[0].protocolPolicies().length);
    assertEquals(1, integrationAcls[0].protocolPolicies()[0].protocolBitflag());
    assertNotNull(integrationAcls[0].protocolPolicies()[0].data());

    assertEquals(PublicKey.fromBase58Encoded("gstgptmbgJVi5f8ZmSRVZjZkDQwqKa3xWuUtD5WmJHz"), integrationAcls[1].integrationProgram());
    assertEquals(7, integrationAcls[1].protocolsBitmask());
    assertEquals(1, integrationAcls[1].protocolPolicies().length);
    assertEquals(4, integrationAcls[1].protocolPolicies()[0].protocolBitflag());
    assertNotNull(integrationAcls[1].protocolPolicies()[0].data());

    assertEquals(PublicKey.fromBase58Encoded("gstgs9nJgX8PmRHWAAEP9H7xT3ZkaPWSGPYbj3mXdTa"), integrationAcls[2].integrationProgram());
    assertEquals(1, integrationAcls[2].protocolsBitmask());
    assertEquals(1, integrationAcls[2].protocolPolicies().length);
    assertEquals(1, integrationAcls[2].protocolPolicies()[0].protocolBitflag());
    assertNotNull(integrationAcls[2].protocolPolicies()[0].data());

    assertEquals(PublicKey.fromBase58Encoded("gstgS4dNeT3BTEQa1aaTS2b8CsAUz1SmwQDGosHSPsw"), integrationAcls[3].integrationProgram());
    assertEquals(7, integrationAcls[3].protocolsBitmask());
    assertEquals(0, integrationAcls[3].protocolPolicies().length);

    assertEquals(PublicKey.fromBase58Encoded("gstgmvM2o7h7GcScvXymH1oFgWskukWWxRHC1UJJ9FJ"), integrationAcls[4].integrationProgram());
    assertEquals(1, integrationAcls[4].protocolsBitmask());
    assertEquals(0, integrationAcls[4].protocolPolicies().length);

    assertEquals(PublicKey.fromBase58Encoded("gstgKa2Gq9wf5hM3DFWx1TvUrGYzDYszyFGq3XBY9Uq"), integrationAcls[5].integrationProgram());
    assertEquals(3, integrationAcls[5].protocolsBitmask());
    assertEquals(1, integrationAcls[5].protocolPolicies().length);
    assertEquals(2, integrationAcls[5].protocolPolicies()[0].protocolBitflag());
    assertNotNull(integrationAcls[5].protocolPolicies()[0].data());

    final var delegateAcls = stateAccount.delegateAcls();
    assertEquals(2, delegateAcls.length);

    assertEquals(PublicKey.fromBase58Encoded("ema1yjj7rwZ64kx6G4z4MVMi8ARqzQgKTN894QjzzsB"), delegateAcls[0].pubkey());
    assertEquals(0L, delegateAcls[0].expiresAt());
    assertEquals(1, delegateAcls[0].integrationPermissions().length);
    assertEquals(PublicKey.fromBase58Encoded("gstgm1M39mhgnvgyScGUDRwNn5kNVSd97hTtyow1Et5"),
        delegateAcls[0].integrationPermissions()[0].integrationProgram());
    assertEquals(1, delegateAcls[0].integrationPermissions()[0].protocolPermissions().length);
    assertEquals(1, delegateAcls[0].integrationPermissions()[0].protocolPermissions()[0].protocolBitflag());
    assertEquals(32L, delegateAcls[0].integrationPermissions()[0].protocolPermissions()[0].permissionsBitmask());

    assertEquals(PublicKey.fromBase58Encoded("HVDx4ijqYDMZF8dM4yFQrQG8cqwkC6LZZ4WgwYa3eLge"), delegateAcls[1].pubkey());
    assertEquals(0L, delegateAcls[1].expiresAt());
    final var ip1 = delegateAcls[1].integrationPermissions();
    assertEquals(4, ip1.length);

    assertEquals(PublicKey.fromBase58Encoded("gstgptmbgJVi5f8ZmSRVZjZkDQwqKa3xWuUtD5WmJHz"), ip1[0].integrationProgram());
    assertEquals(3, ip1[0].protocolPermissions().length);
    assertEquals(1, ip1[0].protocolPermissions()[0].protocolBitflag());
    assertEquals(1L, ip1[0].protocolPermissions()[0].permissionsBitmask());
    assertEquals(2, ip1[0].protocolPermissions()[1].protocolBitflag());
    assertEquals(2L, ip1[0].protocolPermissions()[1].permissionsBitmask());
    assertEquals(4, ip1[0].protocolPermissions()[2].protocolBitflag());
    assertEquals(2L, ip1[0].protocolPermissions()[2].permissionsBitmask());

    assertEquals(PublicKey.fromBase58Encoded("gstgdpMFXKobURsFtStdaMLRSuwdmDUsrndov7kyu9h"), ip1[1].integrationProgram());
    assertEquals(1, ip1[1].protocolPermissions().length);
    assertEquals(1, ip1[1].protocolPermissions()[0].protocolBitflag());
    assertEquals(120L, ip1[1].protocolPermissions()[0].permissionsBitmask());

    assertEquals(PublicKey.fromBase58Encoded("gstgs9nJgX8PmRHWAAEP9H7xT3ZkaPWSGPYbj3mXdTa"), ip1[2].integrationProgram());
    assertEquals(1, ip1[2].protocolPermissions().length);
    assertEquals(1, ip1[2].protocolPermissions()[0].protocolBitflag());
    assertEquals(1L, ip1[2].protocolPermissions()[0].permissionsBitmask());

    assertEquals(PublicKey.fromBase58Encoded("gstgS4dNeT3BTEQa1aaTS2b8CsAUz1SmwQDGosHSPsw"), ip1[3].integrationProgram());
    assertEquals(3, ip1[3].protocolPermissions().length);
    assertEquals(1, ip1[3].protocolPermissions()[0].protocolBitflag());
    assertEquals(32L, ip1[3].protocolPermissions()[0].permissionsBitmask());
    assertEquals(2, ip1[3].protocolPermissions()[1].protocolBitflag());
    assertEquals(32L, ip1[3].protocolPermissions()[1].permissionsBitmask());
    assertEquals(4, ip1[3].protocolPermissions()[2].protocolBitflag());
    assertEquals(32L, ip1[3].protocolPermissions()[2].permissionsBitmask());

    assertEquals(2, stateAccount.externalPositions().length);
    final PublicKey[] expectedExternalPositions = {
        PublicKey.fromBase58Encoded("GEo563G1h7DA4byBP1mwzJDRpr3XdUdJR8LxWEqWTDWM"),
        PublicKey.fromBase58Encoded("ENyABtr2rUKYV9FEZnkcEqki3kVcGS1BvhH7o9BFBxSZ")
    };
    assertArrayEquals(expectedExternalPositions, stateAccount.externalPositions());

    assertEquals(0, stateAccount.pricedProtocols().length);

    assertEquals(4, stateAccount.params().length);
    assertEquals(1, stateAccount.params()[0].length);
    assertEquals(5, stateAccount.params()[1].length);
    assertEquals(0, stateAccount.params()[2].length);
    assertEquals(0, stateAccount.params()[3].length);
  }

  private void validateBaseNotChanged(final MinGlamStateAccount expected, final MinGlamStateAccount minStateAccount) {
    assertEquals(expected.accountType(), minStateAccount.accountType());
    assertEquals(expected.baseAssetIndex(), minStateAccount.baseAssetIndex());
    assertEquals(expected.baseAssetMint(), minStateAccount.baseAssetMint());
    assertEquals(expected.baseAssetDecimals(), minStateAccount.baseAssetDecimals());
  }
}
