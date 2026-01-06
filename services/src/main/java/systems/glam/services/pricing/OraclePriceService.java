package systems.glam.services.pricing;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.drift.gen.types.PythLazerOracle;
import software.sava.idl.clients.oracles.pyth.receiver.gen.types.PriceUpdateV2;
import software.sava.idl.clients.oracles.switchboard.on_demand.gen.types.PullFeedAccountData;
import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.sdk.idl.programs.glam.config.gen.types.AssetMeta;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Base64;
import java.util.Map;

public class OraclePriceService implements PriceService {

  private final Map<PublicKey, AssetMeta> assetMetaByAccount;

  public OraclePriceService(final Map<PublicKey, AssetMeta> assetMetaByAccount) {
    this.assetMetaByAccount = assetMetaByAccount;
  }

  @Override
  public BigDecimal price(final AccountInfo<byte[]> accountInfo) {
    final var assetMeta = assetMetaByAccount.get(accountInfo.pubKey());
    if (assetMeta == null) {
      // TODO: Fetch Global Config, new asset added.
      return null;
    }

    final var oracleSource = assetMeta.oracleSource();
    // TODO: Do 1k/1M oracle types need to be scaled?
    final byte[] data = accountInfo.data();
    if (data.length == PythLazerOracle.BYTES && PythLazerOracle.DISCRIMINATOR.equals(data, 0)) {
      final var oracleData = PythLazerOracle.read(accountInfo);
      return scalePrice(oracleData.price(), oracleData.exponent());
    } else if (PriceUpdateV2.DISCRIMINATOR.equals(data, 0)) {
      final var priceUpdate = PriceUpdateV2.read(accountInfo);
      final var priceMessage = priceUpdate.priceMessage();
      return scalePrice(priceMessage.price(), priceMessage.exponent());
    } else if (data.length == PullFeedAccountData.BYTES && PullFeedAccountData.DISCRIMINATOR.equals(data, 0)) {
      final var pullFeedData = PullFeedAccountData.read(accountInfo);
      final var currentResult = pullFeedData.result();
      return scalePrice(currentResult.mean(), -assetMeta.decimals());
    } else {
      // TODO: Add scope support
      throw new UnsupportedOperationException(String.format(
          "TODO: Add support for %s oracle [mint=%s] [account=%s] [data=%s]",
          oracleSource,
          assetMeta.asset(),
          accountInfo.pubKey(),
          Base64.getEncoder().encodeToString(data)
      ));
    }
  }

  private static BigDecimal scalePrice(final long scaledPrice, final int exponent) {
    final var price = scaledPrice < 0
        ? new BigDecimal(Long.toUnsignedString(scaledPrice))
        : new BigDecimal(scaledPrice);
    return price.movePointRight(exponent);
  }

  private static BigDecimal scalePrice(final BigInteger scaledPrice, final int exponent) {
    return new BigDecimal(scaledPrice).movePointRight(exponent);
  }
}
