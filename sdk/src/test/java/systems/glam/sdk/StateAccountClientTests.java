package systems.glam.sdk;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.Context;
import systems.glam.sdk.idl.programs.glam.mint.gen.GlamMintPDAs;
import systems.glam.sdk.idl.programs.glam.protocol.gen.GlamProtocolConstants;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.*;
import systems.glam.sdk.idl.programs.glam.spl.gen.ExtSplConstants;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Map;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;

final class StateAccountClientTests {

  private static final PublicKey FEE_PAYER = fromBase58Encoded("F1oQY1jbdiJyxxeeuMBF2NsUckboyWo6TSXNqzJbrhxs");
  private static final PublicKey STATE_KEY = fromBase58Encoded("9fkan2jCsS7Xq3fLqgxgZT5pDCbj2MhQ5MAoEKSHrcAT");
  private static final PublicKey DELEGATE = fromBase58Encoded("ema1yjj7rwZ64kx6G4z4MVMi8ARqzQgKTN894QjzzsB");
  private static final PublicKey BASE_ASSET_MINT = fromBase58Encoded("So11111111111111111111111111111111111111112");

  private static final PublicKey MINT_PROGRAM = GlamAccounts.MAIN_NET.mintProgram();
  private static final PublicKey PROTOCOL_PROGRAM = GlamAccounts.MAIN_NET.protocolProgram();
  private static final PublicKey SPL_PROGRAM = GlamAccounts.MAIN_NET.splIntegrationProgram();
  private static final PublicKey KAMINO_PROGRAM = GlamAccounts.MAIN_NET.kaminoIntegrationProgram();

  private static byte[] fixLength(final String value) {
    return Arrays.copyOf(value.getBytes(US_ASCII), StateAccount.NAME_LEN);
  }

  private static NotifyAndSettle notifyAndSettle(final NoticePeriodType redeemType, final TimeUnit timeUnit) {
    return new NotifyAndSettle(
        ValuationModel.Continuous, false,
        NoticePeriodType.Hard, 11L, 12L, 13L,
        redeemType, 21L, 22L, 23L,
        timeUnit, new byte[NotifyAndSettle.PADDING_LEN]
    );
  }

  private static StateAccount stateAccount(final int baseAssetTokenProgram,
                                           final IntegrationAcl[] integrationAcls,
                                           final DelegateAcl[] delegateAcls,
                                           final NotifyAndSettle notifyAndSettle) {
    final var mintPDA = GlamAccounts.MAIN_NET.mintPDA(STATE_KEY, 0).publicKey();
    return new StateAccount(
        STATE_KEY,
        StateAccount.DISCRIMINATOR,
        AccountType.TokenizedVault,
        true,
        fromBase58Encoded("EMou4Rxje9ddgFubx92Grg3doP2vvKrxJiGdyiv6jxQY"),
        fromBase58Encoded("yuru1ARL4bcmSFpufUCdCrF4joamZRui9CawdgE4ZCW"),
        fixLength("pm"),
        new CreatedModel(new byte[8], FEE_PAYER, 1_650_000_000L),
        BASE_ASSET_MINT,
        9,
        baseAssetTokenProgram,
        fixLength("Test Vault"),
        0L,
        0L,
        mintPDA,
        new PublicKey[]{BASE_ASSET_MINT},
        integrationAcls,
        delegateAcls,
        new PublicKey[]{FEE_PAYER},
        new PricedProtocol[0],
        new EngineField[][]{{
            // a non-NotifyAndSettle field first: the params scan must filter
            // by value type, not blindly take the first entry
            new EngineField(EngineFieldName.TimelockDuration, new EngineFieldValue.U64(1_000_000L)),
            new EngineField(EngineFieldName.NotifyAndSettle, new EngineFieldValue.NotifyAndSettle(notifyAndSettle))
        }}
    );
  }

  private static IntegrationAcl acl(final PublicKey program, final int bitmask) {
    return new IntegrationAcl(program, bitmask, new ProtocolPolicy[0]);
  }

  // the same-package ProtocolPermissions record shadows the generated type's
  // simple name; alias the generated one
  private static IntegrationPermissions grant(final PublicKey integrationProgram, final long... flagMaskPairs) {
    final var permissions = new systems.glam.sdk.idl.programs.glam.protocol.gen.types.ProtocolPermissions[flagMaskPairs.length >> 1];
    for (int i = 0; i < permissions.length; ++i) {
      permissions[i] = new systems.glam.sdk.idl.programs.glam.protocol.gen.types.ProtocolPermissions(
          (int) flagMaskPairs[i << 1], flagMaskPairs[(i << 1) + 1]
      );
    }
    return new IntegrationPermissions(integrationProgram, permissions);
  }

  private static DelegateAcl delegateAcl(final PublicKey delegate, final IntegrationPermissions... permissions) {
    return new DelegateAcl(delegate, permissions, Long.MAX_VALUE);
  }

  private static StateAccountClient createClient(final StateAccount stateAccount) {
    return StateAccountClient.createClient(stateAccount, FEE_PAYER);
  }

  private static StateAccount defaultState() {
    return stateAccount(
        0,
        new IntegrationAcl[]{
            acl(KAMINO_PROGRAM, ExtKaminoBitmask.LENDING),
            acl(PROTOCOL_PROGRAM, GlamProtocolConstants.PROTO_JUPITER_SWAP)
        },
        new DelegateAcl[]{
            delegateAcl(
                DELEGATE,
                grant(MINT_PROGRAM, Protocol.MINT.protocolBitFlag(), 32L),
                grant(
                    PROTOCOL_PROGRAM,
                    Protocol.JUPITER_SWAP.protocolBitFlag(), 1L,
                    Protocol.STAKE.protocolBitFlag(), 2L
                ),
                grant(SPL_PROGRAM, ExtSplConstants.PROTO_TOKEN, 1L),
                grant(KAMINO_PROGRAM, Protocol.KAMINO_LENDING.protocolBitFlag(), 40L)
            )
        },
        notifyAndSettle(NoticePeriodType.Soft, TimeUnit.Second)
    );
  }

  // local alias so the kamino bitmask reads as what it is
  private static final class ExtKaminoBitmask {
    static final int LENDING = Protocol.KAMINO_LENDING.protocolBitFlag();
  }

  @Test
  void nullStateAccountsCreateNullClients() {
    assertNull(StateAccountClient.createClient(null, FEE_PAYER));
    assertNull(StateAccountClient.createClient(null, GlamAccountClient.createClient(FEE_PAYER, STATE_KEY)));
  }

  @Test
  void clientReflectsStateAccount() {
    final var client = createClient(defaultState());
    assertEquals("Test Vault", client.name());
    assertEquals(GlamAccounts.MAIN_NET.mintPDA(STATE_KEY, 0).publicKey(), client.mint());
    assertEquals(BASE_ASSET_MINT, client.baseAssetMint());
    assertEquals(9, client.baseAssetDecimals());
    assertArrayEquals(new PublicKey[]{BASE_ASSET_MINT}, client.assets());
    assertArrayEquals(new PublicKey[]{FEE_PAYER}, client.externalPositions());
    assertEquals(STATE_KEY, client.accountClient().vaultAccounts().glamStateKey());

    final var expectedEscrow = GlamMintPDAs.glamEscrowPDA(MINT_PROGRAM, client.mint());
    assertEquals(expectedEscrow.publicKey(), client.escrowAccount().publicKey());
  }

  @Test
  void baseAssetTokenProgramSelection() {
    final var legacy = createClient(defaultState());
    assertEquals(
        legacy.accountClient().solanaAccounts().tokenProgram(),
        legacy.baseAssetTokenProgram()
    );
    final var token2022State = stateAccount(
        1, new IntegrationAcl[0], new DelegateAcl[0],
        notifyAndSettle(NoticePeriodType.Soft, TimeUnit.Second)
    );
    final var token2022 = createClient(token2022State);
    assertEquals(
        token2022.accountClient().solanaAccounts().token2022Program(),
        token2022.baseAssetTokenProgram()
    );
  }

  @Test
  void redeemParametersComeFromNotifyAndSettle() {
    final var client = createClient(defaultState());
    assertEquals(21L, client.redeemNoticePeriod());
    assertEquals(22L, client.redeemSettlementPeriod());
    assertEquals(23L, client.redeemCancellationWindow());
    assertTrue(client.redeemWindowInSeconds());
    assertTrue(client.softRedeem());

    final var hardSlots = createClient(stateAccount(
        0, new IntegrationAcl[0], new DelegateAcl[0],
        notifyAndSettle(NoticePeriodType.Hard, TimeUnit.Slot)
    ));
    assertFalse(hardSlots.redeemWindowInSeconds());
    assertFalse(hardSlots.softRedeem());
  }

  @Test
  void integrationEnabledChecksTheProgramBitmask() {
    final var client = createClient(defaultState());
    assertTrue(client.kaminoLendEnabled());
    // kamino ACL grants lending only
    assertFalse(client.kaminoVaultsEnabled());
    assertTrue(client.jupiterSwapEnabled());
    // an integration with no ACL contributes an empty bitmask
    assertFalse(client.integrationEnabled(SPL_PROGRAM, ExtSplConstants.PROTO_TOKEN));

    // and each helper must go false without its ACL, and vaults true with it
    final var vaultsOnly = createClient(stateAccount(
        0,
        new IntegrationAcl[]{acl(KAMINO_PROGRAM, Protocol.KAMINO_VAULTS.protocolBitFlag())},
        new DelegateAcl[0],
        notifyAndSettle(NoticePeriodType.Soft, TimeUnit.Second)
    ));
    assertTrue(vaultsOnly.kaminoVaultsEnabled());
    assertFalse(vaultsOnly.kaminoLendEnabled());
    assertFalse(vaultsOnly.jupiterSwapEnabled());
  }

  @Test
  void protocolConstantsClasses() {
    assertSame(GlamProtocolConstants.class, Protocol.SYSTEM.constantsClass());
    assertSame(GlamProtocolConstants.class, Protocol.JUPITER_SWAP.constantsClass());
  }

  @Test
  void delegatePermissions() {
    final var client = createClient(defaultState());
    final var unknownDelegate = fromBase58Encoded("HVDx4ijqYDMZF8dM4yFQrQG8cqwkC6LZZ4WgwYa3eLge");
    assertFalse(client.delegateHasPermissions(
        unknownDelegate, Map.of(MINT_PROGRAM, Protocol.MINT.permissions(32L))
    ));

    // exact grant across every adapted integration family
    assertTrue(client.delegateHasPermissions(
        DELEGATE, Map.of(MINT_PROGRAM, Protocol.MINT.permissions(32L))
    ));
    assertTrue(client.delegateHasPermissions(
        DELEGATE, Map.of(PROTOCOL_PROGRAM, Protocol.STAKE.permissions(2L))
    ));
    assertTrue(client.delegateHasPermissions(
        DELEGATE, Map.of(SPL_PROGRAM, Protocol.TOKEN.permissions(1L))
    ));
    // required ⊆ granted passes: kamino grant is 40 (= 32 | 8)
    assertTrue(client.delegateHasPermissions(
        DELEGATE, Map.of(KAMINO_PROGRAM, Protocol.KAMINO_LENDING.permissions(32L))
    ));
    assertTrue(client.delegateHasPermissions(
        DELEGATE, Map.of(KAMINO_PROGRAM, Protocol.KAMINO_LENDING.permissions(8L, 32L))
    ));
    // and multiple requirements at once
    assertTrue(client.delegateHasPermissions(
        DELEGATE,
        Map.of(
            MINT_PROGRAM, Protocol.MINT.permissions(32L),
            KAMINO_PROGRAM, Protocol.KAMINO_LENDING.permissions(32L)
        )
    ));

    // any required bit outside the grant fails, even alongside granted bits
    assertFalse(client.delegateHasPermissions(
        DELEGATE, Map.of(MINT_PROGRAM, Protocol.MINT.permissions(64L))
    ));
    assertFalse(client.delegateHasPermissions(
        DELEGATE, Map.of(KAMINO_PROGRAM, Protocol.KAMINO_LENDING.permissions(32L, 16L))
    ));

    // misses return false rather than throwing: no entry for the integration,
    // and no entry for the protocol within a granted integration
    assertFalse(client.delegateHasPermissions(
        DELEGATE,
        Map.of(fromBase58Encoded("dRiftyHA39MWEi3m9aunc5MzRF1JYuBsbn6VPcn33UH"), Protocol.MINT.permissions(1L))
    ));
    assertFalse(client.delegateHasPermissions(
        DELEGATE, Map.of(KAMINO_PROGRAM, Protocol.KAMINO_VAULTS.permissions(32L))
    ));
  }

  @Test
  void unknownDelegateIntegrationProgramsAreSkipped() {
    // a real staging account carries a drift ACL although drift support was
    // dropped; stale on-chain ACLs must not fail client construction
    final var driftProgram = fromBase58Encoded("dRiftyHA39MWEi3m9aunc5MzRF1JYuBsbn6VPcn33UH");
    final var state = stateAccount(
        0,
        new IntegrationAcl[0],
        new DelegateAcl[]{
            delegateAcl(
                DELEGATE,
                grant(driftProgram, 1L, 1L),
                grant(MINT_PROGRAM, Protocol.MINT.protocolBitFlag(), 32L)
            )
        },
        notifyAndSettle(NoticePeriodType.Soft, TimeUnit.Second)
    );
    final var client = createClient(state);
    // known grants on the same delegate survive; the unknown one reads absent
    assertTrue(client.delegateHasPermissions(DELEGATE, Map.of(MINT_PROGRAM, Protocol.MINT.permissions(32L))));
    assertFalse(client.delegateHasPermissions(DELEGATE, Map.of(driftProgram, Protocol.MINT.permissions(1L))));
    // the drift grant used kamino's bitflag value: it must not have been
    // adapted as a kamino permission either
    assertFalse(client.delegateHasPermissions(
        DELEGATE, Map.of(driftProgram, Protocol.KAMINO_LENDING.permissions(1L))
    ));
  }

  @Test
  void isDelegated() {
    final var state = defaultState();
    assertTrue(GlamAccountClient.isDelegated(state, DELEGATE));
    assertFalse(GlamAccountClient.isDelegated(state, FEE_PAYER));
  }

  @Test
  void createStateAccountClientFromAccountInfo() {
    final var state = defaultState();
    final var accountInfo = new AccountInfo<>(
        STATE_KEY,
        new Context(123L, null),
        false,
        0,
        PROTOCOL_PROGRAM,
        BigInteger.ZERO,
        0,
        state.write()
    );
    final var accountClient = GlamAccountClient.createClient(FEE_PAYER, STATE_KEY);
    final var client = accountClient.createStateAccountClient(accountInfo);
    assertEquals("Test Vault", client.name());
    assertSame(accountClient, client.accountClient());
    assertTrue(client.kaminoLendEnabled());
  }
}
