package systems.glam.sdk.idl.programs.glam.staging.bridge.gen.types;

import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

public record CleanupMayanMctpBridgeLedgerArgs(byte[] randomKey) implements SerDe {

  public static final int BYTES = 32;
  public static final int RANDOM_KEY_LEN = 32;

  public static final int RANDOM_KEY_OFFSET = 0;

  public static CleanupMayanMctpBridgeLedgerArgs read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    final var randomKey = new byte[32];
    SerDeUtil.readArray(randomKey, _data, _offset);
    return new CleanupMayanMctpBridgeLedgerArgs(randomKey);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    i += SerDeUtil.writeArrayChecked(randomKey, 32, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return BYTES;
  }
}
