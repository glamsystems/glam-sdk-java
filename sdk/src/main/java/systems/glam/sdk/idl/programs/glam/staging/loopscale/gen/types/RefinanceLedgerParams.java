package systems.glam.sdk.idl.programs.glam.staging.loopscale.gen.types;

import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

public record RefinanceLedgerParams(int ledgerIndex,
                                    int durationIndex,
                                    byte[] assetIndexGuidance) implements SerDe {

  public static final int LEDGER_INDEX_OFFSET = 0;
  public static final int DURATION_INDEX_OFFSET = 1;
  public static final int ASSET_INDEX_GUIDANCE_OFFSET = 2;

  public static RefinanceLedgerParams read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var ledgerIndex = _data[i] & 0xFF;
    ++i;
    final var durationIndex = _data[i] & 0xFF;
    ++i;
    final var assetIndexGuidance = SerDeUtil.readbyteVector(4, _data, i);
    return new RefinanceLedgerParams(ledgerIndex, durationIndex, assetIndexGuidance);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    _data[i] = (byte) ledgerIndex;
    ++i;
    _data[i] = (byte) durationIndex;
    ++i;
    i += SerDeUtil.writeVector(4, assetIndexGuidance, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return 1 + 1 + SerDeUtil.lenVector(4, assetIndexGuidance);
  }
}
