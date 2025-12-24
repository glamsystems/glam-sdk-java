package systems.glam.sdk.idl.programs.glam.config.gen.types;

import software.sava.idl.clients.core.gen.RustEnum;
import software.sava.idl.clients.core.gen.SerDeUtil;

public enum OracleSource implements RustEnum {

  Pyth,
  Switchboard,
  QuoteAsset,
  Pyth1K,
  Pyth1M,
  PythStableCoin,
  Prelaunch,
  PythPull,
  Pyth1KPull,
  Pyth1MPull,
  PythStableCoinPull,
  SwitchboardOnDemand,
  PythLazer,
  PythLazer1K,
  PythLazer1M,
  PythLazerStableCoin,
  NotSet,
  LstPoolState,
  MarinadeState,
  BaseAsset,
  ChainlinkRWA;

  public static OracleSource read(final byte[] _data, final int _offset) {
    return SerDeUtil.read(1, OracleSource.values(), _data, _offset);
  }
}