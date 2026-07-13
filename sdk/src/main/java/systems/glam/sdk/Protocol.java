package systems.glam.sdk;

import systems.glam.sdk.idl.programs.glam.bridge.gen.ExtBridgeConstants;
import systems.glam.sdk.idl.programs.glam.kamino.gen.ExtKaminoConstants;
import systems.glam.sdk.idl.programs.glam.mint.gen.GlamMintConstants;
import systems.glam.sdk.idl.programs.glam.protocol.gen.GlamProtocolConstants;
import systems.glam.sdk.idl.programs.glam.spl.gen.ExtSplConstants;
import systems.glam.sdk.idl.programs.glam.staging.cctp.gen.ExtCctpConstants;
import systems.glam.sdk.idl.programs.glam.staging.registered_positions.gen.ExtRpiConstants;
import systems.glam.sdk.idl.programs.glam.staging.jupiter.gen.ExtJupiterConstants;
import systems.glam.sdk.idl.programs.glam.staging.loopscale.gen.ExtLoopscaleConstants;
import systems.glam.sdk.idl.programs.glam.staging.marginfi.gen.ExtMarginfiConstants;
import systems.glam.sdk.idl.programs.glam.staging.nt.gen.ExtNeutralConstants;
import systems.glam.sdk.idl.programs.glam.staging.orca.gen.ExtOrcaConstants;
import systems.glam.sdk.idl.programs.glam.staging.phoenix.gen.ExtPhoenixConstants;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum Protocol {

  CCTP(ExtBridgeConstants.class, ExtBridgeConstants.PROTO_CCTP),
  RPI(ExtRpiConstants.class, ExtRpiConstants.PROTO_RPI),
  JUPITER_SWAP(GlamProtocolConstants.class, GlamProtocolConstants.PROTO_JUPITER_SWAP),
  JUPITER_BORROW(ExtJupiterConstants.class, ExtJupiterConstants.PROTO_JUPITER_BORROW),
  JUPITER_EARN(ExtJupiterConstants.class, ExtJupiterConstants.PROTO_JUPITER_EARN),
  KAMINO_LENDING(ExtKaminoConstants.class, ExtKaminoConstants.PROTO_KAMINO_LENDING),
  KAMINO_VAULTS(ExtKaminoConstants.class, ExtKaminoConstants.PROTO_KAMINO_VAULTS),
  KAMINO_FARMS(ExtKaminoConstants.class, ExtKaminoConstants.PROTO_KAMINO_FARMS),
  LOOPSCALE_BORROW(ExtLoopscaleConstants.class, ExtLoopscaleConstants.PROTO_LOOPSCALE_BORROW),
  LOOPSCALE_LENDING(ExtLoopscaleConstants.class, ExtLoopscaleConstants.PROTO_LOOPSCALE_LENDING),
  LOOPSCALE_VAULTS(ExtLoopscaleConstants.class, ExtLoopscaleConstants.PROTO_LOOPSCALE_VAULT),
  MARGINFI(ExtMarginfiConstants.class, ExtMarginfiConstants.PROTO_MARGINFI),
  NEUTRAL(ExtNeutralConstants.class, ExtNeutralConstants.PROTO_NEUTRAL),
  ORCA_WHIRLPOOLS(ExtOrcaConstants.class, ExtOrcaConstants.PROTO_ORCA_WHIRLPOOLS),
  PHOENIX(ExtPhoenixConstants.class, ExtPhoenixConstants.PROTO_PHOENIX),
  MINT(GlamMintConstants.class, GlamMintConstants.PROTO_MINT),
  STAKE(GlamProtocolConstants.class, GlamProtocolConstants.PROTO_STAKE),
  SYSTEM(GlamProtocolConstants.class, GlamProtocolConstants.PROTO_SYSTEM),
  TOKEN(ExtSplConstants.class, ExtSplConstants.PROTO_TOKEN),
  @Deprecated(forRemoval = false)
  LEGACY_CCTP(ExtCctpConstants.class, ExtCctpConstants.PROTO_CCTP);

  private static final Map<Class<?>, Map<Integer, Protocol>> protocolMap = Arrays.stream(values())
      .collect(Collectors.groupingBy(Protocol::constantsClass, Collectors.toUnmodifiableMap(Protocol::protocolBitFlag, Function.identity())));

  private final Class<?> constantsClass;
  private final int protocolBitFlag;

  Protocol(final Class<?> constantsClass, final int protocolBitFlag) {
    this.constantsClass = constantsClass;
    this.protocolBitFlag = protocolBitFlag;
  }

  Class<?> constantsClass() {
    return constantsClass;
  }

  public int protocolBitFlag() {
    return protocolBitFlag;
  }

  public ProtocolPermissions permissions(final long... permissionFlags) {
    long permissionMask = 0;
    for (long permissionFlag : permissionFlags) {
      permissionMask |= permissionFlag;
    }
    return new ProtocolPermissions(this, permissionMask);
  }

  private static Protocol fromProtocolBitFlag(final Class<?> constantsClass, final int protocolBitFlag) {
    return protocolMap.get(constantsClass).get(protocolBitFlag);
  }

  private static ProtocolPermissions fromProtocolBitFlag(final Class<?> constantsClass,
                                                         final int protocolBitFlag,
                                                         final long permissionMask) {
    return new ProtocolPermissions(fromProtocolBitFlag(constantsClass, protocolBitFlag), permissionMask);
  }

  public static ProtocolPermissions fromGlamProtocolBitFlag(final int protocolBitFlag, final long permissionMask) {
    return fromProtocolBitFlag(GlamProtocolConstants.class, protocolBitFlag, permissionMask);
  }

  public static ProtocolPermissions fromMintProtocolBitFlag(final int protocolBitFlag, final long permissionMask) {
    return fromProtocolBitFlag(GlamMintConstants.class, protocolBitFlag, permissionMask);
  }

  public static ProtocolPermissions fromSplProtocolBitFlag(final int protocolBitFlag, final long permissionMask) {
    return fromProtocolBitFlag(ExtSplConstants.class, protocolBitFlag, permissionMask);
  }

  public static ProtocolPermissions fromKaminoProtocolBitFlag(final int protocolBitFlag, final long permissionMask) {
    return fromProtocolBitFlag(ExtKaminoConstants.class, protocolBitFlag, permissionMask);
  }

  public static ProtocolPermissions fromBridgeProtocolBitFlag(final int protocolBitFlag, final long permissionMask) {
    return fromProtocolBitFlag(ExtBridgeConstants.class, protocolBitFlag, permissionMask);
  }

  /**
   * Decodes permissions for the standalone legacy CCTP program.
   *
   * @deprecated New CCTP permissions belong to the bridge program and should be decoded with
   * {@link #fromBridgeProtocolBitFlag(int, long)}.
   */
  @Deprecated(forRemoval = false)
  public static ProtocolPermissions fromLegacyCctpProtocolBitFlag(final int protocolBitFlag,
                                                                  final long permissionMask) {
    return fromProtocolBitFlag(ExtCctpConstants.class, protocolBitFlag, permissionMask);
  }
}
