package systems.glam.services.fulfillment.drfit;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.idl.clients.drift.DriftAccounts;
import software.sava.idl.clients.drift.DriftPDAs;
import software.sava.idl.clients.drift.DriftProgramClient;
import software.sava.idl.clients.drift.gen.types.User;
import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.services.pricing.accounting.Position;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class DriftUserPosition implements Position {

  private static final PublicKey[] SPOT_MARKETS = new PublicKey[512];
  private static final PublicKey[] PERP_MARKETS = new PublicKey[512];

  private final DriftProgramClient driftClient;
  private final DriftAccounts driftAccounts;
  private final PublicKey userKey;
  private final Map<PublicKey, AccountMeta> oracleMetas;
  private final Map<PublicKey, AccountMeta> spotMarketMetas;
  private final Map<PublicKey, AccountMeta> perpMarketMetas;

  public DriftUserPosition(final DriftProgramClient driftClient, final PublicKey userKey) {
    this.driftClient = driftClient;
    this.driftAccounts = driftClient.driftAccounts();
    this.userKey = userKey;
    this.oracleMetas = HashMap.newHashMap(8);
    this.spotMarketMetas = HashMap.newHashMap(8);
    this.perpMarketMetas = HashMap.newHashMap(8);
  }

  public PublicKey userKey() {
    return userKey;
  }

  @Override
  public void accountsNeeded(final Set<PublicKey> keys) {
    keys.add(userKey);
  }

  public void returnAccounts(final Map<PublicKey, AccountInfo<byte[]>> accountMap,
                             final Set<PublicKey> returnAccounts) {
    final var user = User.read(accountMap.get(userKey));
    for (final var position : user.spotPositions()) {
      if (position.scaledBalance() != 0) {
        var market = SPOT_MARKETS[position.marketIndex()];
        if (market == null) {
          market = DriftPDAs.deriveSpotMarketAccount(driftAccounts, position.marketIndex()).publicKey();
          SPOT_MARKETS[position.marketIndex()] = market;
        }
        returnAccounts.add(market);
        // TODO: Cache market accounts to lookup oracle key.
      }
    }
    for (final var position : user.perpPositions()) {
      if (position.baseAssetAmount() != 0) {
        var market = SPOT_MARKETS[position.marketIndex()];
        if (market == null) {
          market = DriftPDAs.derivePerpMarketAccount(driftAccounts, position.marketIndex()).publicKey();
          PERP_MARKETS[position.marketIndex()] = market;
        }
        returnAccounts.add(market);
        // TODO: Cache market accounts to lookup oracle key. PerpMarket -> AMM -> oracle
      }
    }
  }
}
