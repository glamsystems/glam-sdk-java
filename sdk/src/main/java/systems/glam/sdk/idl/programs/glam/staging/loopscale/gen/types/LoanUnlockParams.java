package systems.glam.sdk.idl.programs.glam.staging.loopscale.gen.types;

import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

public record LoanUnlockParams(byte[] assetIndexGuidance) implements SerDe {

  public static final int ASSET_INDEX_GUIDANCE_OFFSET = 0;

  public static LoanUnlockParams read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    final var assetIndexGuidance = SerDeUtil.readbyteVector(4, _data, _offset);
    return new LoanUnlockParams(assetIndexGuidance);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    i += SerDeUtil.writeVector(4, assetIndexGuidance, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return SerDeUtil.lenVector(4, assetIndexGuidance);
  }
}
