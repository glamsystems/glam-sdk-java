package systems.glam.sdk.idl.programs.glam.staging.phoenix.gen.types;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.core.gen.SerDe;
import software.sava.idl.clients.core.gen.SerDeUtil;

import java.util.OptionalLong;

import static software.sava.core.accounts.PublicKey.readPubKey;
import static software.sava.core.encoding.ByteUtil.getInt64LE;

public record EmberPolicy(PublicKey usdcMint,
                          PublicKey canonicalCollateralMint,
                          OptionalLong maxConversionAmount) implements SerDe {

  public static final int USDC_MINT_OFFSET = 0;
  public static final int CANONICAL_COLLATERAL_MINT_OFFSET = 32;
  public static final int MAX_CONVERSION_AMOUNT_OFFSET = 65;

  public static EmberPolicy read(final byte[] _data, final int _offset) {
    if (_data == null || _data.length == 0) {
      return null;
    }
    int i = _offset;
    final var usdcMint = readPubKey(_data, i);
    i += 32;
    final var canonicalCollateralMint = readPubKey(_data, i);
    i += 32;
    final OptionalLong maxConversionAmount;
    if (SerDeUtil.isAbsent(1, _data, i)) {
      maxConversionAmount = OptionalLong.empty();
    } else {
      ++i;
      maxConversionAmount = OptionalLong.of(getInt64LE(_data, i));
    }
    return new EmberPolicy(usdcMint, canonicalCollateralMint, maxConversionAmount);
  }

  @Override
  public int write(final byte[] _data, final int _offset) {
    int i = _offset;
    usdcMint.write(_data, i);
    i += 32;
    canonicalCollateralMint.write(_data, i);
    i += 32;
    i += SerDeUtil.writeOptional(1, maxConversionAmount, _data, i);
    return i - _offset;
  }

  @Override
  public int l() {
    return 32 + 32 + (maxConversionAmount == null || maxConversionAmount.isEmpty() ? 1 : (1 + 8));
  }
}
