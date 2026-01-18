package systems.glam.sdk.idl.programs.glam.policy.gen.types;

import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

public record AnchorExtraAccountMeta(int discriminator,
                                     byte[] addressConfig,
                                     boolean isSigner,
                                     boolean isWritable) implements SerDe {

  public static final int BYTES = 35;
  public static final int ADDRESS_CONFIG_LEN = 32;

  public static final int DISCRIMINATOR_OFFSET = 0;
  public static final int ADDRESS_CONFIG_OFFSET = 1;
  public static final int IS_SIGNER_OFFSET = 33;
  public static final int IS_WRITABLE_OFFSET = 34;

  public static AnchorExtraAccountMeta read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var discriminator = _data[i] & 0xFF;
    ++i;
    final var addressConfig = new byte[32];
    i += SerDeUtil.readArray(addressConfig, _data, i);
    final var isSigner = _data[i] == 1;
    ++i;
    final var isWritable = _data[i] == 1;
    return new AnchorExtraAccountMeta(discriminator,
                                      addressConfig,
                                      isSigner,
                                      isWritable);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    _data[i] = (byte) discriminator;
    ++i;
    i += SerDeUtil.writeArrayChecked(addressConfig, 32, _data, i);
    _data[i] = (byte) (isSigner ? 1 : 0);
    ++i;
    _data[i] = (byte) (isWritable ? 1 : 0);
    ++i;
    return i - _offset;
  }

  @Override
  public int l() {
    return BYTES;
  }
}
