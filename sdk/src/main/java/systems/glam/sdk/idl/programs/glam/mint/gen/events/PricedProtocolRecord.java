package systems.glam.sdk.idl.programs.glam.mint.gen.events;

import java.math.BigInteger;

import software.sava.core.programs.Discriminator;

import static software.sava.core.encoding.ByteUtil.getInt128LE;
import static software.sava.core.encoding.ByteUtil.putInt128LE;
import static software.sava.core.programs.Discriminator.createAnchorDiscriminator;
import static software.sava.core.programs.Discriminator.toDiscriminator;

public record PricedProtocolRecord(Discriminator discriminator, BigInteger baseAssetAmount) implements GlamMintEvent {

  public static final int BYTES = 24;
  public static final Discriminator DISCRIMINATOR = toDiscriminator(232, 89, 187, 82, 49, 200, 127, 132);

  public static final int BASE_ASSET_AMOUNT_OFFSET = 8;

  public static PricedProtocolRecord read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    final var discriminator = createAnchorDiscriminator(_data, _offset);
    int i = _offset + discriminator.length();
    final var baseAssetAmount = getInt128LE(_data, i);
    return new PricedProtocolRecord(discriminator, baseAssetAmount);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset + discriminator.write(_data, _offset);
    putInt128LE(_data, i, baseAssetAmount);
    i += 16;
    return i - _offset;
  }

  @Override
  public int l() {
    return BYTES;
  }
}
