package systems.glam.services.oracles.scope.parsers;

import software.sava.idl.clients.kamino.scope.entries.Deprecated;
import software.sava.idl.clients.kamino.scope.entries.ScopeEntry;
import software.sava.idl.clients.kamino.scope.entries.Unused;
import software.sava.idl.clients.kamino.scope.gen.types.OracleType;
import systems.comodal.jsoniter.CharBufferFunction;
import systems.comodal.jsoniter.JsonIterator;
import systems.comodal.jsoniter.ValueType;

import static systems.comodal.jsoniter.JsonIterator.*;

public class BaseScopeEntryParser implements ScopeEntryParser {

  protected final OracleType oracleType;
  protected int index;

  protected BaseScopeEntryParser(final OracleType oracleType) {
    this.oracleType = oracleType;
  }

  static ScopeEntry readNestedEntryOrNull(final JsonIterator ji) {
    if (ji.whatIsNext() == ValueType.NULL) {
      ji.readNull();
      return null;
    }
    return ScopeEntryParser.parseEntry(ji);
  }

  static CharBufferFunction<OracleType> PARSE_ORACLE_TYPE = (buf, offset, len) -> {
    if (fieldStartsWith("PythPull", buf, offset, len)) {
      if (fieldEndsWith("EMA", buf, offset, len)) {
        return OracleType.PythPullEMA;
      } else {
        return OracleType.PythPull;
      }
    } else if (fieldEquals("PythLazer", buf, offset, len)) {
      return OracleType.PythLazer;
    } else if (fieldEquals("SwitchboardOnDemand", buf, offset, len)) {
      return OracleType.SwitchboardOnDemand;
    } else if (fieldEquals("FixedPrice", buf, offset, len)) {
      return OracleType.FixedPrice;
    } else if (fieldEquals("JitoRestaking", buf, offset, len)) {
      return OracleType.JitoRestaking;
    } else if (fieldEquals("RedStone", buf, offset, len)) {
      return OracleType.RedStone;
    } else if (fieldEquals("Securitize", buf, offset, len)) {
      return OracleType.Securitize;
    } else if (fieldEquals("FlashtradeLp", buf, offset, len)) {
      return OracleType.FlashtradeLp;
    } else if (fieldEquals("DiscountToMaturity", buf, offset, len)) {
      return OracleType.DiscountToMaturity;
    } else if (fieldEquals("SplStake", buf, offset, len)) {
      return OracleType.SplStake;
    } else if (fieldEquals("KToken", buf, offset, len)) {
      return OracleType.KToken;
    } else if (fieldEquals("MsolStake", buf, offset, len)) {
      return OracleType.MsolStake;
    } else if (fieldEquals("JupiterLpFetch", buf, offset, len)) {
      return OracleType.JupiterLpFetch;
    } else if (fieldEquals("AdrenaLp", buf, offset, len)) {
      return OracleType.AdrenaLp;
    } else if (fieldStartsWith("Chainlink", buf, offset, len)) {
      if (fieldEndsWith("ExchangeRate", buf, offset, len)) {
        return OracleType.ChainlinkExchangeRate;
      } else if (fieldEndsWith("NAV", buf, offset, len)) {
        return OracleType.ChainlinkNAV;
      } else if (fieldEndsWith("RWA", buf, offset, len)) {
        return OracleType.ChainlinkRWA;
      } else if (fieldEndsWith("X", buf, offset, len)) {
        return OracleType.ChainlinkX;
      } else {
        return OracleType.Chainlink;
      }
    } else if (fieldEquals("CappedMostRecentOf", buf, offset, len)) {
      return OracleType.CappedMostRecentOf;
    } else if (fieldEquals("MostRecentOf", buf, offset, len)) {
      return OracleType.MostRecentOf;
    } else if (fieldStartsWith("CappedFloored", buf, offset, len)) {
      return OracleType.CappedFloored;
    } else if (fieldStartsWith("ScopeTwap", buf, offset, len)) {
      if (fieldEndsWith("8h", buf, offset, len)) {
        return OracleType.ScopeTwap8h;
      } else if (fieldEndsWith("24h", buf, offset, len)) {
        return OracleType.ScopeTwap24h;
      } else {
        return OracleType.ScopeTwap1h;
      }
    } else if (fieldStartsWith("MeteoraDlmm", buf, offset, len)) {
      if (fieldEndsWith("AtoB", buf, offset, len)) {
        return OracleType.MeteoraDlmmAtoB;
      } else {
        return OracleType.MeteoraDlmmBtoA;
      }
    } else if (fieldStartsWith("OrcaWhirlpool", buf, offset, len)) {
      if (fieldEndsWith("AtoB", buf, offset, len)) {
        return OracleType.OrcaWhirlpoolAtoB;
      } else {
        return OracleType.OrcaWhirlpoolBtoA;
      }
    } else if (fieldStartsWith("RaydiumAmmV3", buf, offset, len)) {
      if (fieldEndsWith("AtoB", buf, offset, len)) {
        return OracleType.RaydiumAmmV3AtoB;
      } else {
        return OracleType.RaydiumAmmV3BtoA;
      }
    } else if (fieldEquals("Unused", buf, offset, len)) {
      return OracleType.Unused;
    } else if (fieldStartsWith("Deprecated", buf, offset, len)) {
      if (fieldEndsWith("1", buf, offset, len)) {
        return OracleType.DeprecatedPlaceholder1;
      } else if (fieldEndsWith("2", buf, offset, len)) {
        return OracleType.DeprecatedPlaceholder2;
      } else if (fieldEndsWith("3", buf, offset, len)) {
        return OracleType.DeprecatedPlaceholder3;
      } else if (fieldEndsWith("4", buf, offset, len)) {
        return OracleType.DeprecatedPlaceholder4;
      } else if (fieldEndsWith("5", buf, offset, len)) {
        return OracleType.DeprecatedPlaceholder5;
      } else if (fieldEndsWith("6", buf, offset, len)) {
        return OracleType.DeprecatedPlaceholder6;
      } else {
        return OracleType.DeprecatedPlaceholder7;
      }
    } else {
      throw new IllegalStateException("Unknown oracle type: " + new String(buf, offset, len));
    }
  };

  @Override
  public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
    if (fieldEquals("index", buf, offset, len)) {
      this.index = ji.readInt();
    } else {
      throw new IllegalStateException("Unsupported scope entry field: " + new String(buf, offset, len));
    }
    return true;
  }

  @Override
  public ScopeEntry createEntry() {
    return switch (oracleType) {
      case DeprecatedPlaceholder1, DeprecatedPlaceholder2, DeprecatedPlaceholder3,
           DeprecatedPlaceholder4, DeprecatedPlaceholder5, DeprecatedPlaceholder6,
           DeprecatedPlaceholder7 -> new Deprecated(index, oracleType);
      case Unused -> new Unused(index);
      default -> throw new IllegalStateException("Unexpected oracle type: " + oracleType);
    };
  }
}
