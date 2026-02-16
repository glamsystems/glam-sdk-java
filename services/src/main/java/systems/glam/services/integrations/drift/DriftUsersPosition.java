package systems.glam.services.integrations.drift;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.encoding.ByteUtil;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.drift.DriftPDAs;
import software.sava.idl.clients.drift.gen.types.PerpPosition;
import software.sava.idl.clients.drift.gen.types.SpotPosition;
import software.sava.idl.clients.drift.gen.types.User;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.InnerInstructions;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.services.integrations.IntegrationServiceContext;
import systems.glam.services.pricing.accounting.AggregatePositionReport;
import systems.glam.services.pricing.accounting.Position;
import systems.glam.services.pricing.accounting.PositionReportNode;
import systems.glam.services.rpc.AccountFetcher;
import systems.glam.services.state.MinGlamStateAccount;

import java.math.BigDecimal;
import java.util.*;

public final class DriftUsersPosition implements Position {

  private final AccountMeta userStatsMeta;
  private final Map<PublicKey, AccountMeta> userAccounts;

  private DriftUsersPosition(final AccountMeta userStatsMeta) {
    this.userStatsMeta = userStatsMeta;
    this.userAccounts = HashMap.newHashMap(8);
  }

  public static DriftUsersPosition createPosition(final DriftMarketCache driftMarketCache,
                                                  final PublicKey glamVaultKey) {
    final var userStatsKey = DriftPDAs.deriveUserStatsAccount(driftMarketCache.driftAccounts(), glamVaultKey).publicKey();
    return new DriftUsersPosition(AccountMeta.createRead(userStatsKey));
  }

  public void addAccount(final PublicKey accountKey) {
    userAccounts.put(accountKey, AccountMeta.createRead(accountKey));
  }

  @Override
  public void removeAccount(final PublicKey account) {
    userAccounts.remove(account);
  }

  private static void addExtraAccounts(final Map<PublicKey, AccountMeta> marketAccounts,
                                       final Map<PublicKey, AccountMeta> oracleAccounts,
                                       final DriftMarketContext marketContext) {
    final var readMarket = marketContext.readMarket();
    marketAccounts.putIfAbsent(readMarket.publicKey(), readMarket);
    final var readOracle = marketContext.readOracle();
    oracleAccounts.putIfAbsent(readOracle.publicKey(), readOracle);
  }

  static int priceUser(final PublicKey userKey,
                       final DriftMarketCache driftMarketCache,
                       final Map<PublicKey, AccountInfo<byte[]>> accountMap,
                       final Map<PublicKey, AccountMeta> extraAccountsMap) {
    return priceUser(userKey, driftMarketCache, accountMap, extraAccountsMap, extraAccountsMap, extraAccountsMap);
  }

  static int priceUser(final PublicKey userKey,
                       final DriftMarketCache driftMarketCache,
                       final Map<PublicKey, AccountInfo<byte[]>> accountMap,
                       final Map<PublicKey, AccountMeta> spotMarketAccounts,
                       final Map<PublicKey, AccountMeta> perpMarketAccounts,
                       final Map<PublicKey, AccountMeta> oracleAccounts) {
    final var userAccount = accountMap.get(userKey);
    if (AccountFetcher.isNull(userAccount)) {
      return 0;
    }

    int missingMarkets = 0;
    final byte[] data = userAccount.data();
    for (int i = 0, offset = User.SPOT_POSITIONS_OFFSET; i < User.SPOT_POSITIONS_LEN; ++i, offset += SpotPosition.BYTES) {
      final long scaledBalance = ByteUtil.getInt64LE(data, offset);
      if (scaledBalance != 0) {
        final var marketIndex = ByteUtil.getInt16LE(data, offset + SpotPosition.MARKET_INDEX_OFFSET);
        final var marketContext = driftMarketCache.spotMarket(marketIndex);
        if (marketContext == null) {
          ++missingMarkets;
        } else {
          addExtraAccounts(spotMarketAccounts, oracleAccounts, marketContext);
        }
      }
    }

    for (int i = 0, offset = User.PERP_POSITIONS_OFFSET; i < User.PERP_POSITIONS_LEN; ++i, offset += PerpPosition.BYTES) {
      final long baseAssetAmount = ByteUtil.getInt64LE(data, offset + PerpPosition.BASE_ASSET_AMOUNT_OFFSET);
      if (baseAssetAmount != 0) {
        final var marketIndex = ByteUtil.getInt16LE(data, offset + PerpPosition.MARKET_INDEX_OFFSET);
        final var marketContext = driftMarketCache.perpMarket(marketIndex);
        if (marketContext == null) {
          ++missingMarkets;
        } else {
          addExtraAccounts(perpMarketAccounts, oracleAccounts, marketContext);
        }
      }
    }
    return missingMarkets;
  }

  @Override
  public boolean priceInstruction(final IntegrationServiceContext serviceContext,
                                  final GlamAccountClient glamAccountClient,
                                  final PublicKey solUSDOracleKey,
                                  final PublicKey baseAssetUSDOracleKey,
                                  final MinGlamStateAccount stateAccount,
                                  final Map<PublicKey, AccountInfo<byte[]>> accountMap,
                                  final SequencedCollection<Instruction> priceInstructions,
                                  final Set<PublicKey> returnAccounts) {
    final int numUsers = userAccounts.size();
    final var extraAccountsMap = HashMap.<PublicKey, AccountMeta>newHashMap(numUsers * (User.SPOT_POSITIONS_LEN << 1) + (User.PERP_POSITIONS_LEN << 1));
    final var driftMarketCache = serviceContext.driftMarketCache();
    int missingMarkets = 0;
    for (final var userKey : userAccounts.keySet()) {
      missingMarkets += priceUser(userKey, driftMarketCache, accountMap, extraAccountsMap);
    }

    if (missingMarkets == 0) {
      returnAccounts.add(userStatsMeta.publicKey());
      returnAccounts.addAll(userAccounts.keySet());
      returnAccounts.addAll(extraAccountsMap.keySet());

      final var extraAccounts = new ArrayList<AccountMeta>(1 + numUsers + extraAccountsMap.size());
      extraAccounts.add(userStatsMeta);
      extraAccounts.addAll(userAccounts.values());
      extraAccounts.addAll(extraAccountsMap.values());

      final var priceIx = glamAccountClient.priceDriftUsers(
          solUSDOracleKey, baseAssetUSDOracleKey,
          numUsers, true
      ).extraAccounts(extraAccounts);
      priceInstructions.add(priceIx);
      return true;
    } else {
      return false;
    }
  }


  @Override
  public int positionReport(final IntegrationServiceContext serviceContext,
                            final PublicKey mintProgram,
                            final MinGlamStateAccount stateAccount,
                            final Map<PublicKey, AccountInfo<byte[]>> returnedAccountsMap,
                            final int ixIndex,
                            final List<Instruction> priceInstructions,
                            final InnerInstructions[] innerInstructionsArray,
                            final Map<PublicKey, BigDecimal> assetPrices,
                            final List<AggregatePositionReport> positionReportsList) {
    // TODO: Calculate position value independently
    // TODO: Report all sub-positions, e.g. each spot and perp position.
    final var innerInstructions = innerInstructionsArray[ixIndex];
    final var positionAmount = Position.parseAnchorEvent(innerInstructions, mintProgram, stateAccount.baseAssetDecimals());
    final var reportNode = new PositionReportNode(positionAmount, List.of());
    positionReportsList.add(reportNode);
    return ixIndex + 1;
  }
}
