package systems.glam.services.oracles.scope.parsers;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.kamino.scope.entries.ScopeEntry;
import software.sava.idl.clients.kamino.scope.gen.types.OracleType;
import software.sava.rpc.json.PublicKeyEncoding;
import systems.comodal.jsoniter.JsonIterator;

import java.lang.reflect.InvocationTargetException;

class OracleEntryParser extends ScopeEntryParser {

  protected PublicKey oracle;
  protected boolean twapEnabled;

  OracleEntryParser(final OracleType oracleType) {
    super(oracleType);
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
          final var constructor = clas.getConstructor(PublicKey.class, boolean.class);
          yield (ScopeEntry) constructor.newInstance(oracle, twapEnabled);
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
    } else if (JsonIterator.fieldEquals("twapEnabled", buf, offset, len)) {
      this.twapEnabled = ji.readBoolean();
    } else {
      super.test(buf, offset, len, ji);
    }
    return true;
  }
}
