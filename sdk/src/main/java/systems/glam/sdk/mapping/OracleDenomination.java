package systems.glam.sdk.mapping;

import systems.glam.sdk.idl.programs.glam.config.gen.types.OracleSource;

/// The unit an oracle source prices in, as the protocol's `get_oracle_price` reads each source: the Pyth
/// pull and Lazer feeds, Switchboard on demand, the Scope-read Chainlink feeds and a Kamino reserve in USD;
/// a stake pool's or Marinade's state in SOL. A source the protocol does not price has no denomination,
/// and [#of] returns null for it: the sources its dispatch does not name, and the legacy Pyth push feeds
/// (`Pyth`, `PythStableCoin`), which the dispatch names but the reader refuses. A handler that must
/// convert between the two units cannot place such an oracle, so a caller refuses rather than guesses.
public enum OracleDenomination {

  SOL,
  USD;

  /// The denomination of `source`, or null when the protocol prices no oracle of that source.
  public static OracleDenomination of(final OracleSource source) {
    if (source == null) {
      return null;
    }
    return switch (source) {
      case PythPull, PythLazer, PythLazer1M, PythLazer1K,
           PythLazerStableCoin, PythStableCoinPull,
           SwitchboardOnDemand, ChainlinkRWA, ChainlinkX, KaminoReserve -> USD;
      case LstPoolState, MarinadeState -> SOL;
      // the push feeds the reader refuses, and the sources the dispatch does not name
      case Pyth, PythStableCoin, Pyth1K, Pyth1M, Pyth1KPull, Pyth1MPull,
           Switchboard, QuoteAsset, Prelaunch, NotSet, BaseAsset -> null;
    };
  }
}
