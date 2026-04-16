package systems.glam.sdk.idl.programs.glam.staging.external_positions.gen.types;

import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

/// Byte-backed i128 storage that keeps the containing zero-copy account 8-byte aligned.
///
public record StoredI128(byte[] bytes) implements SerDe {

  public static final int BYTES = 16;
  public static final int BYTES_LEN = 16;

  public static final int BYTES_OFFSET = 0;

  public static StoredI128 read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    final var bytes = new byte[16];
    SerDeUtil.readArray(bytes, _data, _offset);
    return new StoredI128(bytes);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    i += SerDeUtil.writeArrayChecked(bytes, 16, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return BYTES;
  }
}
