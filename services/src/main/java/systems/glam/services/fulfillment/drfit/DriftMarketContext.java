package systems.glam.services.fulfillment.drfit;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.encoding.ByteUtil;
import software.sava.idl.clients.drift.gen.types.PerpMarket;
import software.sava.idl.clients.drift.gen.types.SpotMarket;
import software.sava.rpc.json.http.response.AccountInfo;

public record DriftMarketContext(int marketIndex, AccountMeta readMarket, AccountMeta readOracle) {

  public static DriftMarketContext createContext(final int marketIndex,
                                                 final PublicKey market,
                                                 final PublicKey oracle) {
    return new DriftMarketContext(marketIndex, AccountMeta.createRead(market), AccountMeta.createRead(oracle));
  }

  public static DriftMarketContext createSpotContext(final AccountInfo<byte[]> accountInfo) {
    final int marketIndex = ByteUtil.getInt16LE(accountInfo.data(), SpotMarket.MARKET_INDEX_OFFSET);
    final var oracle = PublicKey.readPubKey(accountInfo.data(), SpotMarket.ORACLE_OFFSET);
    return createContext(marketIndex, accountInfo.pubKey(), oracle);
  }

  public static DriftMarketContext createPerpContext(final AccountInfo<byte[]> accountInfo) {
    final int marketIndex = ByteUtil.getInt16LE(accountInfo.data(), PerpMarket.MARKET_INDEX_OFFSET);
    final var oracle = PublicKey.readPubKey(accountInfo.data(), PerpMarket.AMM_OFFSET);
    return createContext(marketIndex, accountInfo.pubKey(), oracle);
  }

  public PublicKey market() {
    return readMarket.publicKey();
  }

  public PublicKey oracle() {
    return readOracle.publicKey();
  }
}
