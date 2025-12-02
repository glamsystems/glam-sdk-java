package systems.glam.sdk.oracles.scope;

import software.sava.idl.clients.kamino.scope.entries.Deprecated;
import software.sava.idl.clients.kamino.scope.entries.ScopeEntry;
import software.sava.idl.clients.kamino.scope.entries.Unused;
import software.sava.idl.clients.kamino.scope.gen.types.OracleType;
import systems.comodal.jsoniter.FieldBufferPredicate;
import systems.comodal.jsoniter.JsonIterator;

abstract class ScopeEntryParser implements FieldBufferPredicate {

  protected final OracleType oracleType;

  ScopeEntryParser(final OracleType oracleType) {
    this.oracleType = oracleType;
  }

  static ScopeEntry parseEntry(final JsonIterator ji) {
    final var type = ji.skipObjField().readString();
    if (type.equals("Deprecated")) {
      return new Deprecated();
    } else {
      final var oracleType = OracleType.valueOf(type);
      return switch (oracleType) {  // TODO: use singletons.
        case ChainlinkExchangeRate,
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
             SwitchboardOnDemand -> {
          final var parser = new OracleEntryParser(oracleType);
          ji.testObject(parser);
          yield parser.createEntry();
        }
        case DeprecatedPlaceholder5 -> null;
        case KTokenToTokenA -> null;
        case KTokenToTokenB -> null;
        case ScopeTwap -> null;
        case JupiterLpCompute -> null;
        case JupiterLpScope -> null;
        case PythPull -> null;
        case FixedPrice -> null;
        case Chainlink -> null;
        case DiscountToMaturity -> null;
        case MostRecentOf -> null;
        case PythLazer -> null;
        case AdrenaLp -> null;
        case Securitize -> null;
        case CappedFloored -> null;
        case ChainlinkRWA -> null;
        case ChainlinkX -> null;
        case CappedMostRecentOf -> null;
        case Unused -> new Unused();
        default -> throw new IllegalStateException("Unsupported oracle type: " + oracleType);
      };
    }
  }

  abstract ScopeEntry createEntry();

  @Override
  public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
    throw new IllegalStateException("Unsupported scope entry field: " + new String(buf, offset, len));
  }
}
