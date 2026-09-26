package systems.glam.sdk.mapping;

import org.junit.jupiter.api.Test;
import systems.glam.sdk.idl.programs.glam.config.gen.types.OracleSource;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static systems.glam.sdk.idl.programs.glam.config.gen.types.OracleSource.*;

final class OracleDenominationTests {

  private static final Set<OracleSource> USD_SOURCES = EnumSet.of(
      PythPull, PythLazer, PythLazer1M, PythLazer1K,
      PythLazerStableCoin, PythStableCoinPull,
      SwitchboardOnDemand, ChainlinkRWA, ChainlinkX, KaminoReserve
  );
  private static final Set<OracleSource> SOL_SOURCES = EnumSet.of(LstPoolState, MarinadeState);

  /// The protocol's `get_oracle_price` prices the Pyth pull and Lazer feeds, Switchboard on demand, the
  /// Scope-read Chainlink feeds and a Kamino reserve in USD, a stake pool's or Marinade's state in SOL,
  /// and errors on every other source, the legacy Pyth push feeds included (its dispatch names them but
  /// the reader refuses them): those have no denomination here.
  @Test
  void everySourceHasTheDenominationTheProtocolPricesItIn() {
    for (final var source : OracleSource.values()) {
      final var expected = USD_SOURCES.contains(source) ? OracleDenomination.USD
          : SOL_SOURCES.contains(source) ? OracleDenomination.SOL
          : null;
      assertEquals(expected, OracleDenomination.of(source), source.name());
    }
    assertEquals(23, OracleSource.values().length, "the table covers the enum; a new source must be placed");
    assertEquals(11, OracleSource.values().length - USD_SOURCES.size() - SOL_SOURCES.size(), "the sources the protocol does not price");
    assertNull(OracleDenomination.of(Pyth), "a legacy push feed is refused by the reader");
    assertNull(OracleDenomination.of(PythStableCoin));
    assertNull(OracleDenomination.of(null));
  }
}
