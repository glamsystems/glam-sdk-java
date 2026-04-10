package systems.glam.services.oracles.scope.parsers;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.kamino.scope.entries.ScopeEntry;
import software.sava.idl.clients.kamino.scope.gen.types.OracleType;
import software.sava.rpc.json.PublicKeyEncoding;
import systems.comodal.jsoniter.JsonIterator;

import java.lang.reflect.InvocationTargetException;

import static systems.comodal.jsoniter.JsonIterator.fieldEquals;

public class FunctionalEntryParser extends BaseScopeEntryParser {

  protected PublicKey oracle;

  FunctionalEntryParser(final OracleType oracleType) {
    super(oracleType);
  }

  @Override
  public ScopeEntry createEntry() {
    try {
      return switch (oracleType) {
        case SplBalance,
             SplStake,
             StakedSolBalance -> {
          final var clas = Class.forName("software.sava.idl.clients.kamino.scope.entries." + oracleType);
          final var constructor = clas.getConstructor(int.class, PublicKey.class);
          yield (ScopeEntry) constructor.newInstance(index, oracle);
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
    if (fieldEquals("priceAccount", buf, offset, len) || fieldEquals("oracle", buf, offset, len)) {
      this.oracle = PublicKeyEncoding.parseBase58Encoded(ji);
    } else if (fieldEquals("emaTypes", buf, offset, len) || fieldEquals("twapEnabled", buf, offset, len)) {
      ji.skip();
    } else {
      super.test(buf, offset, len, ji);
    }
    return true;
  }
}
