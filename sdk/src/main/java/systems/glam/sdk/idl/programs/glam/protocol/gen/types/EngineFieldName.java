package systems.glam.sdk.idl.programs.glam.protocol.gen.types;

import software.sava.core.borsh.Borsh;

public enum EngineFieldName implements Borsh.Enum {

  Owner,
  PortfolioManagerName,
  Name,
  Uri,
  Assets,
  DelegateAcls,
  IntegrationAcls,
  TimelockDuration,
  Borrowable,
  DefaultAccountStateFrozen,
  PermanentDelegate,
  NotifyAndSettle,
  FeeStructure,
  FeeParams,
  ClaimableFees,
  ClaimedFees,
  OracleConfigs;

  public static EngineFieldName read(final byte[] _data, final int _offset) {
    return Borsh.read(EngineFieldName.values(), _data, _offset);
  }
}