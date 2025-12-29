package systems.glam.sdk;

import systems.glam.sdk.idl.programs.glam.drift.gen.ExtDriftConstants;
import systems.glam.sdk.idl.programs.glam.kamino.gen.ExtKaminoConstants;
import systems.glam.sdk.idl.programs.glam.mint.gen.GlamMintConstants;
import systems.glam.sdk.idl.programs.glam.protocol.gen.GlamProtocolConstants;
import systems.glam.sdk.idl.programs.glam.spl.gen.ExtSplConstants;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum Protocol {

  DRIFT(ExtDriftConstants.class, ExtDriftConstants.PROTO_DRIFT_PROTOCOL),
  DRIFT_VAULTS(ExtDriftConstants.class, ExtDriftConstants.PROTO_DRIFT_VAULTS),
  JUPITER_SWAP(GlamProtocolConstants.class, GlamProtocolConstants.PROTO_JUPITER_SWAP),
  KAMINO_LENDING(ExtKaminoConstants.class, ExtKaminoConstants.PROTO_KAMINO_LENDING),
  KAMINO_VAULTS(ExtKaminoConstants.class, ExtKaminoConstants.PROTO_KAMINO_VAULTS),
  KAMINO_FARMS(ExtKaminoConstants.class, ExtKaminoConstants.PROTO_KAMINO_FARMS),
  MINT(GlamMintConstants.class, GlamMintConstants.PROTO_MINT),
  STAKE(GlamProtocolConstants.class, GlamProtocolConstants.PROTO_STAKE),
  SYSTEM(GlamProtocolConstants.class, GlamProtocolConstants.PROTO_SYSTEM),
  TOKEN(ExtSplConstants.class, ExtSplConstants.PROTO_TOKEN);

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

  public static ProtocolPermissions fromDriftProtocolBitFlag(final int protocolBitFlag, final long permissionMask) {
    return fromProtocolBitFlag(ExtDriftConstants.class, protocolBitFlag, permissionMask);
  }

  public static ProtocolPermissions fromKaminoProtocolBitFlag(final int protocolBitFlag, final long permissionMask) {
    return fromProtocolBitFlag(ExtKaminoConstants.class, protocolBitFlag, permissionMask);
  }
}
