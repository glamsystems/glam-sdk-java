package systems.glam.sdk;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import systems.glam.sdk.idl.programs.glam.bridge.gen.ExtBridgeConstants;
import systems.glam.sdk.idl.programs.glam.bridge.gen.ExtBridgeProgram;
import systems.glam.sdk.idl.programs.glam.bridge.gen.types.CctpDepositForBurnArgs;
import systems.glam.sdk.idl.programs.glam.bridge.gen.types.DepositForBurnParams;
import systems.glam.sdk.idl.programs.glam.staging.cctp.gen.ExtCctpConstants;

import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;

final class BridgeCctpMigrationTests {

  @Test
  void mainNetAccountsExposeBridgeAndStandaloneCctpPrograms() {
    final var accounts = GlamAccounts.MAIN_NET;

    assertEquals(
        fromBase58Encoded("G1NTbnLcjMex9Tjo8ocmNK9S2zBCiGVuxKUUNGhYZztx"),
        accounts.bridgeIntegrationProgram()
    );
    assertEquals(
        fromBase58Encoded("G1NTcMDYgNLpDwgnrpSZvoSKQuR9NXG7S3DmtNQCDmrK"),
        accounts.cctpIntegrationProgram()
    );
    assertNotEquals(accounts.bridgeIntegrationProgram(), accounts.cctpIntegrationProgram());

    assertEquals(
        fromBase58Encoded("gstgxS9yTioViNKdsM4DC33k1TU9un2VCYDQK8fAeSA"),
        GlamAccounts.MAIN_NET_STAGING.bridgeIntegrationProgram()
    );
    assertEquals(
        fromBase58Encoded("gstgcuRwiX2FpmtigowB1TnVi3fPkZC9TEmVnc5sdxW"),
        GlamAccounts.MAIN_NET_STAGING.cctpIntegrationProgram()
    );
  }

  @Test
  void bridgeAndStandaloneCctpPermissionsRemainProgramScoped() {
    final long bridgePermissionMask =
        ExtBridgeConstants.PROTO_BRIDGE_PERM_SEND | ExtBridgeConstants.PROTO_BRIDGE_PERM_VALIDATE;
    assertEquals(
        new ProtocolPermissions(Protocol.BRIDGE_CCTP, bridgePermissionMask),
        Protocol.fromBridgeProtocolBitFlag(
            ExtBridgeConstants.PROTO_CCTP,
            bridgePermissionMask
        )
    );
    final long oftPermissionMask =
        ExtBridgeConstants.PROTO_BRIDGE_PERM_SEND | ExtBridgeConstants.PROTO_BRIDGE_PERM_SETTLE;
    assertEquals(
        new ProtocolPermissions(Protocol.BRIDGE_LAYERZERO_OFT, oftPermissionMask),
        GlamAccountClientImpl.adaptPermissions(
            GlamAccounts.MAIN_NET,
            GlamAccounts.MAIN_NET.bridgeIntegrationProgram(),
            ExtBridgeConstants.PROTO_LAYERZERO_OFT,
            oftPermissionMask
        )
    );
    assertEquals(
        new ProtocolPermissions(Protocol.BRIDGE_CCTP, bridgePermissionMask),
        GlamAccountClientImpl.adaptPermissions(
            GlamAccounts.MAIN_NET,
            GlamAccounts.MAIN_NET.bridgeIntegrationProgram(),
            ExtBridgeConstants.PROTO_CCTP,
            bridgePermissionMask
        )
    );
    assertEquals(
        new ProtocolPermissions(Protocol.CCTP, ExtCctpConstants.PROTO_CCTP_PERM_TRANSFER),
        Protocol.fromCctpProtocolBitFlag(
            ExtCctpConstants.PROTO_CCTP,
            ExtCctpConstants.PROTO_CCTP_PERM_TRANSFER
        )
    );
    assertEquals(
        new ProtocolPermissions(Protocol.CCTP, ExtCctpConstants.PROTO_CCTP_PERM_TRANSFER),
        GlamAccountClientImpl.adaptPermissions(
            GlamAccounts.MAIN_NET,
            GlamAccounts.MAIN_NET.cctpIntegrationProgram(),
            ExtCctpConstants.PROTO_CCTP,
            ExtCctpConstants.PROTO_CCTP_PERM_TRANSFER
        )
    );
  }

  @Test
  void unmanagedCctpDepositForBurnHasStableBridgeWireEncoding() {
    final var params = new DepositForBurnParams(
        1,
        3,
        PublicKey.NONE,
        PublicKey.NONE,
        2,
        1_000
    );
    final var instruction = ExtBridgeProgram.cctpDepositForBurn(
        GlamAccounts.MAIN_NET.invokedBridgeIntegrationProgram(),
        List.of(),
        new CctpDepositForBurnArgs(params)
    );
    final var expectedData = HexFormat.of().parseHex("""
        845a242367c58f5b
        0100000000000000
        03000000
        0000000000000000000000000000000000000000000000000000000000000000
        0000000000000000000000000000000000000000000000000000000000000000
        0200000000000000
        e8030000
        """.replaceAll("\\s", ""));

    assertArrayEquals(expectedData, instruction.data());
    assertEquals(
        params,
        ExtBridgeProgram.CctpDepositForBurnIxData.read(instruction).args().params()
    );
  }
}
