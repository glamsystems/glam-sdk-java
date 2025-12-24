package systems.glam.sdk.idl.programs.glam.protocol.gen.types;

import software.sava.idl.clients.core.gen.RustEnum;
import software.sava.idl.clients.core.gen.SerDeUtil;

public enum EngineFieldName implements RustEnum {

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
    return SerDeUtil.read(1, EngineFieldName.values(), _data, _offset);
  }
}