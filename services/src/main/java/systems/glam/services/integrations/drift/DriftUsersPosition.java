package systems.glam.services.integrations.drift;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.core.gen.SerDeUtil;
import software.sava.idl.clients.drift.DriftPDAs;
import software.sava.idl.clients.drift.gen.types.PerpPosition;
import software.sava.idl.clients.drift.gen.types.SpotPosition;
import software.sava.idl.clients.drift.gen.types.User;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.InnerInstructions;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.services.integrations.IntegrationServiceContext;
import systems.glam.services.pricing.MinGlamStateAccount;
import systems.glam.services.pricing.PositionReport;
import systems.glam.services.pricing.PositionReportRecord;
import systems.glam.services.pricing.accounting.Position;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class DriftUsersPosition implements Position {

  private final DriftMarketCache driftMarketCache;
  private final AccountMeta userStatsMeta;
  private final Map<PublicKey, AccountMeta> userAccounts;

  private DriftUsersPosition(final DriftMarketCache driftMarketCache, final AccountMeta userStatsMeta) {
    this.driftMarketCache = driftMarketCache;
    this.userStatsMeta = userStatsMeta;
    this.userAccounts = HashMap.newHashMap(8);
  }

  public static DriftUsersPosition create(final DriftMarketCache driftMarketCache, final PublicKey glamVaultKey) {
    final var userStatsKey = DriftPDAs.deriveUserStatsAccount(driftMarketCache.driftAccounts(), glamVaultKey).publicKey();
    return new DriftUsersPosition(driftMarketCache, AccountMeta.createRead(userStatsKey));
  }

  public void addAccount(final PublicKey accountKey) {
    userAccounts.put(accountKey, AccountMeta.createRead(accountKey));
  }

  @Override
  public void removeAccount(final PublicKey account) {
    userAccounts.remove(account);
  }

  @Override
  public void accountsForPriceInstruction(final Set<PublicKey> keys) {
    keys.addAll(userAccounts.keySet());
  }

  private static void addExtraAccounts(final Map<PublicKey, AccountMeta> extraAccounts,
                                       final DriftMarketContext marketContext) {
    final var readOracle = marketContext.readOracle();
    extraAccounts.put(readOracle.publicKey(), readOracle);
    final var readMarket = marketContext.readMarket();
    extraAccounts.put(readMarket.publicKey(), readMarket);
  }

  @Override
  public Instruction priceInstruction(final IntegrationServiceContext serviceContext,
                                      final GlamAccountClient glamAccountClient,
                                      final PublicKey solUSDOracleKey,
                                      final PublicKey baseAssetUSDOracleKey,
                                      final MinGlamStateAccount stateAccount,
                                      final Map<PublicKey, AccountInfo<byte[]>> accountMap,
                                      final Set<PublicKey> returnAccounts) {
    int missingMarkets = 0;

    final int numUsers = userAccounts.size();
    final var extraAccountsMap = HashMap.<PublicKey, AccountMeta>newHashMap(numUsers * (User.SPOT_POSITIONS_LEN << 1) + (User.PERP_POSITIONS_LEN << 1));
    final var spotPositions = new SpotPosition[User.SPOT_POSITIONS_LEN];
    final var perpPositions = new PerpPosition[User.PERP_POSITIONS_LEN];

    for (final var userKey : userAccounts.keySet()) {
      final var userAccount = accountMap.get(userKey);
      final byte[] data = userAccount.data();

      SerDeUtil.readArray(spotPositions, SpotPosition::read, data, User.SPOT_POSITIONS_OFFSET);
      for (final var position : spotPositions) {
        if (position.scaledBalance() != 0) {
          final var marketContext = driftMarketCache.spotMarket(position.marketIndex());
          if (marketContext == null) {
            ++missingMarkets;
          } else {
            addExtraAccounts(extraAccountsMap, marketContext);
          }
        }
      }

      SerDeUtil.readArray(perpPositions, PerpPosition::read, data, User.PERP_POSITIONS_OFFSET);
      for (final var position : perpPositions) {
        if (position.baseAssetAmount() != 0) {
          final var marketContext = driftMarketCache.perpMarket(position.marketIndex());
          if (marketContext == null) {
            ++missingMarkets;
          } else {
            addExtraAccounts(extraAccountsMap, marketContext);
          }
        }
      }
    }

    if (missingMarkets == 0) {
      returnAccounts.add(userStatsMeta.publicKey());
      returnAccounts.addAll(userAccounts.keySet());
      returnAccounts.addAll(extraAccountsMap.keySet());

      final var extraAccounts = new ArrayList<AccountMeta>(1 + numUsers + extraAccountsMap.size());
      extraAccounts.add(userStatsMeta);
      extraAccounts.addAll(userAccounts.values());
      extraAccounts.addAll(extraAccountsMap.values());

      return glamAccountClient.priceDriftUsers(
          solUSDOracleKey, baseAssetUSDOracleKey,
          numUsers, true
      ).extraAccounts(extraAccounts);
    } else {
      return null;
    }
  }


  @Override
  public PositionReport positionReport(final PublicKey mintProgram,
                                       final int baseAssetDecimals,
                                       final Map<PublicKey, AccountInfo<byte[]>> accountMap,
                                       final InnerInstructions innerInstructions) {
    final var positionAmount = Position.parseAnchorEvent(innerInstructions, mintProgram, baseAssetDecimals);
    // TODO: Calculate position value independently
    // TODO: Report all sub-positions, e.g. each spot and perp position.
    return new PositionReportRecord(positionAmount);
  }
}
