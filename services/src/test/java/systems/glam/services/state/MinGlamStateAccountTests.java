package systems.glam.services.state;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.Context;
import systems.glam.sdk.GlamAccounts;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.AccountType;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateAccount;
import systems.glam.services.tests.ResourceUtil;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

final class MinGlamStateAccountTests {

  private static final PublicKey STATE_ACCOUNT_KEY = PublicKey.fromBase58Encoded("3H7XbyVaYusyzQCncfRSBx3zgvfmjGG7wrr3ARtXF1o7");

  @Test
  void mintStateAccountSerialization() throws IOException {
    final byte[] stateAccountData = ResourceUtil.readResource("accounts/glam/min_state/3H7XbyVaYusyzQCncfRSBx3zgvfmjGG7wrr3ARtXF1o7.dat.zip");
    final var stateAccount = StateAccount.read(STATE_ACCOUNT_KEY, stateAccountData);
    assertEquals(AccountType.TokenizedVault, stateAccount.accountType());
    assertEquals(4, stateAccount.assets().length);
    assertEquals(5, stateAccount.externalPositions().length);

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

  private void validateBaseNotChanged(final MinGlamStateAccount expected, final MinGlamStateAccount minStateAccount) {
    assertEquals(expected.accountType(), minStateAccount.accountType());
    assertEquals(expected.baseAssetIndex(), minStateAccount.baseAssetIndex());
    assertEquals(expected.baseAssetMint(), minStateAccount.baseAssetMint());
    assertEquals(expected.baseAssetDecimals(), minStateAccount.baseAssetDecimals());
  }
}
