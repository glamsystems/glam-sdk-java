package systems.glam.sdk.idl.programs.glam.staging.phoenix.gen.types;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

import static software.sava.core.encoding.ByteUtil.*;

public record PhoenixPolicy(PublicKey[] marketsAllowlist,
                            long maxOrderBaseLots,
                            long maxOrderQuoteLots,
                            int maxPriceDeviationBps,
                            boolean requireReduceOnlyOrders,
                            boolean allowRiskIncreasingOrders,
                            int maxReferencePriceAgeSecs) implements SerDe {

  public static final int MARKETS_ALLOWLIST_OFFSET = 0;

  public static PhoenixPolicy read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var marketsAllowlist = SerDeUtil.readPublicKeyVector(4, _data, i);
    i += SerDeUtil.lenVector(4, marketsAllowlist);
    final var maxOrderBaseLots = getInt64LE(_data, i);
    i += 8;
    final var maxOrderQuoteLots = getInt64LE(_data, i);
    i += 8;
    final var maxPriceDeviationBps = getInt16LE(_data, i);
    i += 2;
    final var requireReduceOnlyOrders = _data[i] == 1;
    ++i;
    final var allowRiskIncreasingOrders = _data[i] == 1;
    ++i;
    final var maxReferencePriceAgeSecs = getInt32LE(_data, i);
    return new PhoenixPolicy(marketsAllowlist,
                             maxOrderBaseLots,
                             maxOrderQuoteLots,
                             maxPriceDeviationBps,
                             requireReduceOnlyOrders,
                             allowRiskIncreasingOrders,
                             maxReferencePriceAgeSecs);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    i += SerDeUtil.writeVector(4, marketsAllowlist, _data, i);
    putInt64LE(_data, i, maxOrderBaseLots);
    i += 8;
    putInt64LE(_data, i, maxOrderQuoteLots);
    i += 8;
    putInt16LE(_data, i, maxPriceDeviationBps);
    i += 2;
    _data[i] = (byte) (requireReduceOnlyOrders ? 1 : 0);
    ++i;
    _data[i] = (byte) (allowRiskIncreasingOrders ? 1 : 0);
    ++i;
    putInt32LE(_data, i, maxReferencePriceAgeSecs);
    i += 4;
    return i - _offset;
  }

  @Override
  public int l() {
    return SerDeUtil.lenVector(4, marketsAllowlist)
         + 8
         + 8
         + 2
         + 1
         + 1
         + 4;
  }
}
