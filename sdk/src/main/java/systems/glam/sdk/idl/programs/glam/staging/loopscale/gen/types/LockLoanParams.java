package systems.glam.sdk.idl.programs.glam.staging.loopscale.gen.types;

import software.sava.idl.clients.core.gen.SerDe;

public record LockLoanParams(int unlockIdx) implements SerDe {

  public static final int BYTES = 1;

  public static final int UNLOCK_IDX_OFFSET = 0;

  public static LockLoanParams read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    final var unlockIdx = _data[_offset] & 0xFF;
    return new LockLoanParams(unlockIdx);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    _data[i] = (byte) unlockIdx;
    ++i;
    return i - _offset;
  }

  @Override
  public int l() {
    return BYTES;
  }
}
