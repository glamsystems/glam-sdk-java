package systems.glam.services.oracles.scope.parsers;

import software.sava.idl.clients.kamino.scope.entries.ScopeEntry;
import systems.comodal.jsoniter.FieldBufferPredicate;
import systems.comodal.jsoniter.JsonIterator;

import static systems.glam.services.oracles.scope.parsers.BaseScopeEntryParser.PARSE_ORACLE_TYPE;

public interface ScopeEntryParser extends FieldBufferPredicate {

  static ScopeEntry parseEntry(JsonIterator ji) {
    final var oracleType = ji.skipObjField().applyChars(PARSE_ORACLE_TYPE);
    final var parser = switch (oracleType) {
      case DeprecatedPlaceholder1, DeprecatedPlaceholder2, DeprecatedPlaceholder3,
           DeprecatedPlaceholder4, DeprecatedPlaceholder5, DeprecatedPlaceholder6,
           DeprecatedPlaceholder7, Unused -> new BaseScopeEntryParser(oracleType);
      case AdrenaLp,
           ChainlinkExchangeRate,
           ChainlinkNAV,
           FlashtradeLp,
           JitoRestaking,
           JupiterLpFetch,
           KToken,
           MeteoraDlmmAtoB,
           MeteoraDlmmBtoA,
           MsolStake,
           OrcaWhirlpoolAtoB,
           OrcaWhirlpoolBtoA,
           PythPullEMA,
           RaydiumAmmV3AtoB,
           RaydiumAmmV3BtoA,
           RedStone,
           SplStake,
           SwitchboardOnDemand -> new OracleEntryParser(oracleType);
      case Chainlink -> new ChainlinkParser(oracleType);
      case ChainlinkRWA, ChainlinkX -> new ChainlinkStatusEntryParser(oracleType);
      case PythPull, Securitize -> new ReferencesEntryParser(oracleType);
      case PythLazer -> new PythLazerParser(oracleType);
      case ScopeTwap1h, ScopeTwap8h, ScopeTwap24h -> new ScopeTwapParser(oracleType);
      case FixedPrice -> new FixedPriceParser(oracleType);
      case DiscountToMaturity -> new DiscountToMaturityParser(oracleType);
      case CappedFloored -> new CappedFlooredParser(oracleType);
      case CappedMostRecentOf, MostRecentOf -> new MostRecentOfParser(oracleType);
      case KTokenToTokenA, KTokenToTokenB -> throw new IllegalStateException("Unsupported oracle type: " + oracleType);
    };
    ji.testObject(parser);
    return parser.createEntry();
  }

  ScopeEntry createEntry();
}
