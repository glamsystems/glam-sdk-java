package systems.glam.sdk.idl.programs.glam.mint.gen.types;

import software.sava.core.borsh.Borsh;

public enum OracleSource implements Borsh.Enum {

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
    return Borsh.read(OracleSource.values(), _data, _offset);
  }
}