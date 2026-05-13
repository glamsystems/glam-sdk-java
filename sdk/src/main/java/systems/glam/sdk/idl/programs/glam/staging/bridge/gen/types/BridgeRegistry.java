package systems.glam.sdk.idl.programs.glam.staging.bridge.gen.types;

import software.sava.core.accounts.PublicKey;
import software.sava.core.programs.Discriminator;
import software.sava.core.rpc.Filter;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.rpc.json.http.response.AccountInfo;

import java.util.function.BiFunction;

import static software.sava.core.accounts.PublicKey.readPubKey;
import static software.sava.core.encoding.ByteUtil.getInt64LE;
import static software.sava.core.encoding.ByteUtil.putInt64LE;
import static software.sava.core.programs.Discriminator.createAnchorDiscriminator;
import static software.sava.core.programs.Discriminator.toDiscriminator;

public record BridgeRegistry(PublicKey _address,
                             Discriminator discriminator,
                             PublicKey glamState,
                             long managedTransferCount,
                             int bump) implements SerDe {

  public static final int BYTES = 49;
  public static final Filter SIZE_FILTER = Filter.createDataSizeFilter(BYTES);

  public static final Discriminator DISCRIMINATOR = toDiscriminator(178, 207, 65, 53, 51, 157, 148, 202);
  public static final Filter DISCRIMINATOR_FILTER = Filter.createMemCompFilter(0, DISCRIMINATOR.data());

  public static final int GLAM_STATE_OFFSET = 8;
  public static final int MANAGED_TRANSFER_COUNT_OFFSET = 40;
  public static final int BUMP_OFFSET = 48;

  public static Filter createGlamStateFilter(final PublicKey glamState) {
    return Filter.createMemCompFilter(GLAM_STATE_OFFSET, glamState);
  }

  public static Filter createManagedTransferCountFilter(final long managedTransferCount) {
    final byte[] _data = new byte[8];
    putInt64LE(_data, 0, managedTransferCount);
    return Filter.createMemCompFilter(MANAGED_TRANSFER_COUNT_OFFSET, _data);
  }

  public static Filter createBumpFilter(final int bump) {
    return Filter.createMemCompFilter(BUMP_OFFSET, new byte[]{(byte) bump});
  }

  public static BridgeRegistry read(final byte[] _data, final int _offset) {
    return read(null, _data, _offset);
  }

  public static BridgeRegistry read(final AccountInfo<byte[]> accountInfo) {
    return read(accountInfo.pubKey(), accountInfo.data(), 0);
  }

  public static BridgeRegistry read(final PublicKey _address, final byte[] _data) {
    return read(_address, _data, 0);
  }

  public static final BiFunction<PublicKey, byte[], BridgeRegistry> FACTORY = BridgeRegistry::read;

  public static BridgeRegistry read(final PublicKey _address, final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    final var discriminator = createAnchorDiscriminator(_data, _offset);
    int i = _offset + discriminator.length();
    final var glamState = readPubKey(_data, i);
    i += 32;
    final var managedTransferCount = getInt64LE(_data, i);
    i += 8;
    final var bump = _data[i] & 0xFF;
    return new BridgeRegistry(_address, discriminator, glamState, managedTransferCount, bump);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset + discriminator.write(_data, _offset);
    glamState.write(_data, i);
    i += 32;
    putInt64LE(_data, i, managedTransferCount);
    i += 8;
    _data[i] = (byte) bump;
    ++i;
    return i - _offset;
  }

  @Override
  public int l() {
    return BYTES;
  }
}
