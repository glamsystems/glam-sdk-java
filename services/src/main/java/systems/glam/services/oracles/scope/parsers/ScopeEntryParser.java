package systems.glam.services.oracles.scope.parsers;

import software.sava.idl.clients.kamino.scope.entries.Deprecated;
import software.sava.idl.clients.kamino.scope.entries.ScopeEntry;
import software.sava.idl.clients.kamino.scope.entries.Unused;
import software.sava.idl.clients.kamino.scope.gen.types.OracleType;
import systems.comodal.jsoniter.FieldBufferPredicate;
import systems.comodal.jsoniter.JsonIterator;
import systems.comodal.jsoniter.ValueType;

public abstract class ScopeEntryParser implements FieldBufferPredicate {

  protected final OracleType oracleType;

  ScopeEntryParser(final OracleType oracleType) {
    this.oracleType = oracleType;
  }

  static ScopeEntry readNestedEntryOrNull(final JsonIterator ji) {
    if (ji.whatIsNext() == ValueType.NULL) {
      ji.readNull();
      return null;
    }
    return ScopeEntryParser.parseEntry(ji);
  }

  public static ScopeEntry parseEntry(final JsonIterator ji) {
    final var type = ji.skipObjField().readString();
    if (type.equals("Deprecated")) {
      return new Deprecated(); // TODO: use singletons.
    } else {
      final var oracleType = type.equals("ScopeTwap") ? OracleType.ScopeTwap1h : OracleType.valueOf(type);
      if (oracleType == OracleType.Unused) {
        return new Unused();  // TODO: use singletons.
      }
      final var parser = switch (oracleType) {
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
        default -> throw new IllegalStateException("Unsupported oracle type: " + oracleType);
      };
      ji.testObject(parser);
      return parser.createEntry();
    }
  }

  abstract ScopeEntry createEntry();

  @Override
  public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
    throw new IllegalStateException("Unsupported scope entry field: " + new String(buf, offset, len));
  }
}
