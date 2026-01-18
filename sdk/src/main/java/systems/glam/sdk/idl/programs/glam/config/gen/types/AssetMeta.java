package systems.glam.sdk.idl.programs.glam.config.gen.types;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

import static software.sava.core.accounts.PublicKey.readPubKey;
import static software.sava.core.encoding.ByteUtil.getInt16LE;
import static software.sava.core.encoding.ByteUtil.putInt16LE;

public record AssetMeta(PublicKey asset,
                        int decimals,
                        PublicKey oracle,
                        OracleSource oracleSource,
                        int maxAgeSeconds,
                        int priority,
                        byte[] padding) implements SerDe {

  public static final int BYTES = 72;
  public static final int PADDING_LEN = 3;

  public static final int ASSET_OFFSET = 0;
  public static final int DECIMALS_OFFSET = 32;
  public static final int ORACLE_OFFSET = 33;
  public static final int ORACLE_SOURCE_OFFSET = 65;
  public static final int MAX_AGE_SECONDS_OFFSET = 66;
  public static final int PRIORITY_OFFSET = 68;
  public static final int PADDING_OFFSET = 69;

  public static AssetMeta read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var asset = readPubKey(_data, i);
    i += 32;
    final var decimals = _data[i] & 0xFF;
    ++i;
    final var oracle = readPubKey(_data, i);
    i += 32;
    final var oracleSource = OracleSource.read(_data, i);
    i += oracleSource.l();
    final var maxAgeSeconds = getInt16LE(_data, i);
    i += 2;
    final var priority = _data[i] & 0xFF;
    ++i;
    final var padding = new byte[3];
    SerDeUtil.readArray(padding, _data, i);
    return new AssetMeta(asset,
                         decimals,
                         oracle,
                         oracleSource,
                         maxAgeSeconds,
                         priority,
                         padding);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    asset.write(_data, i);
    i += 32;
    _data[i] = (byte) decimals;
    ++i;
    oracle.write(_data, i);
    i += 32;
    i += oracleSource.write(_data, i);
    putInt16LE(_data, i, maxAgeSeconds);
    i += 2;
    _data[i] = (byte) priority;
    ++i;
    i += SerDeUtil.writeArrayChecked(padding, 3, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return BYTES;
  }
}
