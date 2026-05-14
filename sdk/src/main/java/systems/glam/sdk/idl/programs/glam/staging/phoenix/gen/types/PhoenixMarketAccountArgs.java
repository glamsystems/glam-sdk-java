package systems.glam.sdk.idl.programs.glam.staging.phoenix.gen.types;

import software.sava.idl.clients.core.gen.SerDe;

public record PhoenixMarketAccountArgs(int traderPdaIndex,
                                       int subaccountIndex,
                                       int globalTraderIndexLen,
                                       int activeTraderBufferLen) implements SerDe {

  public static final int BYTES = 4;

  public static final int TRADER_PDA_INDEX_OFFSET = 0;
  public static final int SUBACCOUNT_INDEX_OFFSET = 1;
  public static final int GLOBAL_TRADER_INDEX_LEN_OFFSET = 2;
  public static final int ACTIVE_TRADER_BUFFER_LEN_OFFSET = 3;

  public static PhoenixMarketAccountArgs read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var traderPdaIndex = _data[i] & 0xFF;
    ++i;
    final var subaccountIndex = _data[i] & 0xFF;
    ++i;
    final var globalTraderIndexLen = _data[i] & 0xFF;
    ++i;
    final var activeTraderBufferLen = _data[i] & 0xFF;
    return new PhoenixMarketAccountArgs(traderPdaIndex,
                                        subaccountIndex,
                                        globalTraderIndexLen,
                                        activeTraderBufferLen);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    _data[i] = (byte) traderPdaIndex;
    ++i;
    _data[i] = (byte) subaccountIndex;
    ++i;
    _data[i] = (byte) globalTraderIndexLen;
    ++i;
    _data[i] = (byte) activeTraderBufferLen;
    ++i;
    return i - _offset;
  }

  @Override
  public int l() {
    return BYTES;
  }
}
