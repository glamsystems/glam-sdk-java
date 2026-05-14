package systems.glam.sdk.idl.programs.glam.staging.phoenix.gen.types;

import software.sava.idl.clients.core.gen.SerDe;

public record PhoenixTransferAccountArgs(int srcTraderPdaIndex,
                                         int srcSubaccountIndex,
                                         int dstTraderPdaIndex,
                                         int dstSubaccountIndex,
                                         int globalTraderIndexLen,
                                         int activeTraderBufferLen) implements SerDe {

  public static final int BYTES = 6;

  public static final int SRC_TRADER_PDA_INDEX_OFFSET = 0;
  public static final int SRC_SUBACCOUNT_INDEX_OFFSET = 1;
  public static final int DST_TRADER_PDA_INDEX_OFFSET = 2;
  public static final int DST_SUBACCOUNT_INDEX_OFFSET = 3;
  public static final int GLOBAL_TRADER_INDEX_LEN_OFFSET = 4;
  public static final int ACTIVE_TRADER_BUFFER_LEN_OFFSET = 5;

  public static PhoenixTransferAccountArgs read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var srcTraderPdaIndex = _data[i] & 0xFF;
    ++i;
    final var srcSubaccountIndex = _data[i] & 0xFF;
    ++i;
    final var dstTraderPdaIndex = _data[i] & 0xFF;
    ++i;
    final var dstSubaccountIndex = _data[i] & 0xFF;
    ++i;
    final var globalTraderIndexLen = _data[i] & 0xFF;
    ++i;
    final var activeTraderBufferLen = _data[i] & 0xFF;
    return new PhoenixTransferAccountArgs(srcTraderPdaIndex,
                                          srcSubaccountIndex,
                                          dstTraderPdaIndex,
                                          dstSubaccountIndex,
                                          globalTraderIndexLen,
                                          activeTraderBufferLen);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    _data[i] = (byte) srcTraderPdaIndex;
    ++i;
    _data[i] = (byte) srcSubaccountIndex;
    ++i;
    _data[i] = (byte) dstTraderPdaIndex;
    ++i;
    _data[i] = (byte) dstSubaccountIndex;
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
