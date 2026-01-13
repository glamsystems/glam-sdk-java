package systems.glam.services.oracles.scope.parsers;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.kamino.scope.entries.ScopeEntry;
import software.sava.idl.clients.kamino.scope.gen.types.EmaType;
import software.sava.idl.clients.kamino.scope.gen.types.OracleType;
import software.sava.rpc.json.PublicKeyEncoding;
import systems.comodal.jsoniter.JsonIterator;

import java.lang.reflect.InvocationTargetException;
import java.util.EnumSet;
import java.util.Set;

class OracleEntryParser extends ScopeEntryParser {

  protected PublicKey oracle;
  private Set<EmaType> emaTypes;

  OracleEntryParser(final OracleType oracleType) {
    super(oracleType);
  }

  protected final Set<EmaType> emaTypes() {
    return emaTypes == null ? Set.of() : emaTypes;
  }

  @Override
  ScopeEntry createEntry() {
    try {
      return switch (oracleType) {
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
             SwitchboardOnDemand -> {
          final var clas = Class.forName("software.sava.idl.clients.kamino.scope.entries." + oracleType);
          final var constructor = clas.getConstructor(PublicKey.class, Set.class);
          yield (ScopeEntry) constructor.newInstance(oracle, emaTypes());
        }
        default -> throw new IllegalStateException("Unexpected oracle type: " + oracleType);
      };
    } catch (final ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException |
                   InvocationTargetException exception) {
      throw new IllegalStateException(exception);
    }
  }

  @Override
  public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
    if (JsonIterator.fieldEquals("oracle", buf, offset, len)) {
      this.oracle = PublicKeyEncoding.parseBase58Encoded(ji);
    } else if (JsonIterator.fieldEquals("emaTypes", buf, offset, len)) {
      if (ji.readArray()) {
        this.emaTypes = EnumSet.noneOf(EmaType.class);
        do {
          this.emaTypes.add(EmaType.valueOf(ji.readString()));
        } while (ji.readArray());
      } else {
        this.emaTypes = Set.of();
      }
    } else if (JsonIterator.fieldEquals("twapEnabled", buf, offset, len)) {
      this.emaTypes = ji.readBoolean() ? EnumSet.of(EmaType.Ema1h) : Set.of();
    } else {
      super.test(buf, offset, len, ji);
    }
    return true;
  }
}
