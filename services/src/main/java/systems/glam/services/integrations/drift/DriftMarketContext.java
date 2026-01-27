package systems.glam.services.integrations.drift;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.encoding.ByteUtil;
import software.sava.idl.clients.drift.gen.types.PerpMarket;
import software.sava.idl.clients.drift.gen.types.SpotMarket;
import software.sava.rpc.json.http.response.AccountInfo;

public record DriftMarketContext(int poolId,
                                 int marketIndex,
                                 AccountMeta readMarket,
                                 AccountMeta readOracle) {

  public static DriftMarketContext createContext(final int poolId,
                                                 final int marketIndex,
                                                 final PublicKey market,
                                                 final PublicKey oracle) {
    return new DriftMarketContext(poolId, marketIndex, AccountMeta.createRead(market), AccountMeta.createRead(oracle));
  }

  public static DriftMarketContext createSpotContext(final PublicKey marketKey, final byte[] data) {
    final int poolId = data[SpotMarket.POOL_ID_OFFSET] & 0xFF;
    final int marketIndex = ByteUtil.getInt16LE(data, SpotMarket.MARKET_INDEX_OFFSET);
    final var oracle = PublicKey.readPubKey(data, SpotMarket.ORACLE_OFFSET);
    return createContext(poolId, marketIndex, marketKey, oracle);
  }

  public static DriftMarketContext createSpotContext(final byte[] data) {
    final var marketKey = PublicKey.readPubKey(data, SpotMarket.PUBKEY_OFFSET);
    return createSpotContext(marketKey, data);
  }

  public static DriftMarketContext createPerpContext(final PublicKey marketKey, final byte[] data) {
    final int poolId = data[PerpMarket.POOL_ID_OFFSET] & 0xFF;
    final int marketIndex = ByteUtil.getInt16LE(data, PerpMarket.MARKET_INDEX_OFFSET);
    final var oracle = PublicKey.readPubKey(data, PerpMarket.AMM_OFFSET);
    return createContext(poolId, marketIndex, marketKey, oracle);
  }

  public static DriftMarketContext createPerpContext(final byte[] data) {
    final var marketKey = PublicKey.readPubKey(data, PerpMarket.PUBKEY_OFFSET);
    return createPerpContext(marketKey, data);
  }

  public static DriftMarketContext createSpotContext(final AccountInfo<byte[]> accountInfo) {
    final byte[] data = accountInfo.data();
    final int poolId = data[SpotMarket.POOL_ID_OFFSET] & 0xFF;
    final int marketIndex = ByteUtil.getInt16LE(data, SpotMarket.MARKET_INDEX_OFFSET);
    final var oracle = PublicKey.readPubKey(data, SpotMarket.ORACLE_OFFSET);
    return createContext(poolId, marketIndex, accountInfo.pubKey(), oracle);
  }

  public static DriftMarketContext createPerpContext(final AccountInfo<byte[]> accountInfo) {
    final byte[] data = accountInfo.data();
    final int poolId = data[PerpMarket.POOL_ID_OFFSET] & 0xFF;
    final int marketIndex = ByteUtil.getInt16LE(data, PerpMarket.MARKET_INDEX_OFFSET);
    final var oracle = PublicKey.readPubKey(data, PerpMarket.AMM_OFFSET);
    return createContext(poolId, marketIndex, accountInfo.pubKey(), oracle);
  }

  public PublicKey market() {
    return readMarket.publicKey();
  }

  public PublicKey oracle() {
    return readOracle.publicKey();
  }
}
