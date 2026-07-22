package systems.glam.sdk;

import org.junit.jupiter.api.Test;
import systems.glam.sdk.idl.programs.glam.kamino.gen.ExtKaminoConstants;
import systems.glam.sdk.idl.programs.glam.mint.gen.GlamMintConstants;
import systems.glam.sdk.idl.programs.glam.protocol.gen.GlamProtocolConstants;
import systems.glam.sdk.idl.programs.glam.spl.gen.ExtSplConstants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

final class ProtocolTests {

  @Test
  void glamProtocolBitFlags() {
    for (final var expected : new Protocol[]{Protocol.JUPITER_SWAP, Protocol.STAKE, Protocol.SYSTEM}) {
      final var permissions = Protocol.fromGlamProtocolBitFlag(expected.protocolBitFlag(), 21);
      assertSame(expected, permissions.protocol());
      assertEquals(21, permissions.permissionMask());
    }
    assertEquals(GlamProtocolConstants.PROTO_JUPITER_SWAP, Protocol.JUPITER_SWAP.protocolBitFlag());
    assertEquals(GlamProtocolConstants.PROTO_STAKE, Protocol.STAKE.protocolBitFlag());
    assertEquals(GlamProtocolConstants.PROTO_SYSTEM, Protocol.SYSTEM.protocolBitFlag());
  }

  @Test
  void mintProtocolBitFlags() {
    final var permissions = Protocol.fromMintProtocolBitFlag(GlamMintConstants.PROTO_MINT, 8);
    assertSame(Protocol.MINT, permissions.protocol());
    assertEquals(8, permissions.permissionMask());
  }

  @Test
  void splProtocolBitFlags() {
    final var permissions = Protocol.fromSplProtocolBitFlag(ExtSplConstants.PROTO_TOKEN, 3);
    assertSame(Protocol.TOKEN, permissions.protocol());
    assertEquals(3, permissions.permissionMask());
  }

  @Test
  void kaminoProtocolBitFlags() {
    for (final var expected : new Protocol[]{Protocol.KAMINO_LENDING, Protocol.KAMINO_VAULTS, Protocol.KAMINO_FARMS}) {
      final var permissions = Protocol.fromKaminoProtocolBitFlag(expected.protocolBitFlag(), 7);
      assertSame(expected, permissions.protocol());
      assertEquals(7, permissions.permissionMask());
    }
    assertEquals(ExtKaminoConstants.PROTO_KAMINO_LENDING, Protocol.KAMINO_LENDING.protocolBitFlag());
    assertEquals(ExtKaminoConstants.PROTO_KAMINO_VAULTS, Protocol.KAMINO_VAULTS.protocolBitFlag());
    assertEquals(ExtKaminoConstants.PROTO_KAMINO_FARMS, Protocol.KAMINO_FARMS.protocolBitFlag());
  }

  @Test
  void permissionsUnionsFlags() {
    final var none = Protocol.SYSTEM.permissions();
    assertSame(Protocol.SYSTEM, none.protocol());
    assertEquals(0L, none.permissionMask());

    final var single = Protocol.TOKEN.permissions(4L);
    assertSame(Protocol.TOKEN, single.protocol());
    assertEquals(4L, single.permissionMask());

    // 6 | 12 == 14: a union, not a sum (18) — overlapping bits collapse
    final var overlapping = Protocol.MINT.permissions(6L, 12L);
    assertEquals(14L, overlapping.permissionMask());
  }
}
