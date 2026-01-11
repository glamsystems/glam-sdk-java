package systems.glam.services.fulfillment.drfit;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.idl.clients.core.gen.SerDeUtil;
import software.sava.idl.clients.drift.DriftAccounts;
import software.sava.idl.clients.drift.DriftProgramClient;
import software.sava.idl.clients.drift.gen.types.PerpPosition;
import software.sava.idl.clients.drift.gen.types.SpotPosition;
import software.sava.idl.clients.drift.gen.types.User;
import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.services.pricing.accounting.Position;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class DriftUserPosition implements Position {

  private final DriftMarketCache driftMarketCache;
  private final DriftProgramClient driftClient;
  private final DriftAccounts driftAccounts;
  private final PublicKey userKey;
  private final SpotPosition[] spotPositions;
  private final PerpPosition[] perpPositions;
  private final Map<PublicKey, AccountMeta> oracleMetas;
  private final Map<PublicKey, AccountMeta> spotMarketMetas;
  private final Map<PublicKey, AccountMeta> perpMarketMetas;

  public DriftUserPosition(final DriftMarketCache driftMarketCache,
                           final DriftProgramClient driftClient,
                           final PublicKey userKey) {
    this.driftMarketCache = driftMarketCache;
    this.driftClient = driftClient;
    this.driftAccounts = driftClient.driftAccounts();
    this.userKey = userKey;
    this.spotPositions = new SpotPosition[User.SPOT_POSITIONS_LEN];
    this.perpPositions = new PerpPosition[User.PERP_POSITIONS_OFFSET];
    this.oracleMetas = HashMap.newHashMap(8);
    this.spotMarketMetas = HashMap.newHashMap(8);
    this.perpMarketMetas = HashMap.newHashMap(8);
  }

  @Override
  public void accountsNeeded(final Set<PublicKey> keys) {
    keys.add(userKey);
  }

  private void addAccounts(final Set<PublicKey> returnAccounts,
                           final Map<PublicKey, AccountMeta> marketAccounts,
                           final DriftMarketContext marketContext) {
    final var readMarket = marketContext.readMarket();
    final var marketKey = readMarket.publicKey();
    marketAccounts.put(marketKey, readMarket);
    returnAccounts.add(marketKey);
    final var readOracle = marketContext.readOracle();
    final var oracleKey = readOracle.publicKey();
    oracleMetas.put(oracleKey, readOracle);
    returnAccounts.add(oracleKey);
  }

  public boolean returnAccounts(final Map<PublicKey, AccountInfo<byte[]>> accountMap,
                                final Set<PublicKey> returnAccounts) {
    final var userAccount = accountMap.get(userKey);
    final byte[] data = userAccount.data();

    SerDeUtil.readArray(spotPositions, SpotPosition::read, data, User.SPOT_POSITIONS_OFFSET);
    for (final var position : spotPositions) {
      if (position.scaledBalance() != 0) {
        final var marketContext = driftMarketCache.spotMarket(position.marketIndex());
        if (marketContext == null) {
          return false;
        }
        addAccounts(returnAccounts, spotMarketMetas, marketContext);
      }
    }

    SerDeUtil.readArray(perpPositions, PerpPosition::read, data, User.PERP_POSITIONS_OFFSET);
    for (final var position : perpPositions) {
      if (position.baseAssetAmount() != 0) {
        final var marketContext = driftMarketCache.perpMarket(position.marketIndex());
        if (marketContext == null) {
          return false;
        }
        addAccounts(returnAccounts, perpMarketMetas, marketContext);
      }
    }
    return true;
  }
}
