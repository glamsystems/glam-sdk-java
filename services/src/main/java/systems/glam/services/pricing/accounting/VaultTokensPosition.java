package systems.glam.services.pricing.accounting;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.accounts.token.TokenAccount;
import software.sava.core.encoding.ByteUtil;
import software.sava.core.tx.Instruction;
import software.sava.core.util.LamportDecimal;
import software.sava.idl.clients.core.gen.SerDeUtil;
import software.sava.idl.clients.drift.gen.types.PythLazerOracle;
import software.sava.idl.clients.kamino.scope.entries.ScopeReader;
import software.sava.idl.clients.kamino.scope.gen.types.OracleType;
import software.sava.idl.clients.kamino.scope.gen.types.StakeSystem;
import software.sava.idl.clients.kamino.scope.gen.types.ValidatorSystem;
import software.sava.idl.clients.marinade.stake_pool.MarinadeAccounts;
import software.sava.idl.clients.marinade.stake_pool.gen.types.State;
import software.sava.idl.clients.oracles.OracleUtil;
import software.sava.idl.clients.oracles.pyth.receiver.gen.types.PriceUpdateV2;
import software.sava.idl.clients.oracles.switchboard.on_demand.gen.types.PullFeedAccountData;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.InnerInstructions;
import software.sava.solana.programs.stakepool.StakePoolState;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.sdk.idl.programs.glam.mint.gen.GlamMintProgram;
import systems.glam.services.integrations.IntegrationServiceContext;
import systems.glam.services.pricing.ScopeAggregateIndexes;
import systems.glam.services.rpc.AccountFetcher;
import systems.glam.services.state.MinGlamStateAccount;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.*;

import static java.lang.System.Logger.Level.WARNING;

public final class VaultTokensPosition implements Position {

  private static final System.Logger logger = System.getLogger(VaultTokensPosition.class.getName());

  private final Map<PublicKey, AccountMeta> vaultATAMap;

  public VaultTokensPosition(final int numAssets) {
    this.vaultATAMap = HashMap.newHashMap(numAssets);
  }

  public void addVaultATA(final PublicKey tokenMint, final PublicKey vaultATA) {
    vaultATAMap.putIfAbsent(tokenMint, AccountMeta.createRead(vaultATA));
  }

  public boolean hasContext(final PublicKey tokenMint) {
    return vaultATAMap.containsKey(tokenMint);
  }

  @Override
  public void removeAccount(final PublicKey account) {
    vaultATAMap.remove(account);
  }

  public void removeOldAccounts(final MinGlamStateAccount stateAccount, final Set<PublicKey> accountsNeededSet) {
    if (vaultATAMap.size() > stateAccount.numAssets()) {
      final var iterator = vaultATAMap.entrySet().iterator();
      while (iterator.hasNext()) {
        final var entry = iterator.next();
        final var mint = entry.getKey();
        if (stateAccount.doesNotContainsAsset(mint)) {
          accountsNeededSet.remove(mint);
          accountsNeededSet.remove(entry.getValue().publicKey());
          iterator.remove();
        }
      }
    }
  }

  public void clear() {
    vaultATAMap.clear();
  }

  @Override
  public void accountsForPriceInstruction(final Set<PublicKey> keys) {
    for (final var meta : vaultATAMap.values()) {
      keys.add(meta.publicKey());
    }
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
    final var vaultAssets = stateAccount.assets();
    final short[][] aggIndexes = ScopeAggregateIndexes.createIndexesArray(vaultAssets.length);

    final var extraAccounts = new ArrayList<AccountMeta>((vaultAssets.length * 3));
    Set<AccountMeta> scopeOracleMappingAccounts = null;
    for (int i = 0; i < vaultAssets.length; i++) {
      final var vaultAsset = vaultAssets[i];
      final var vaultATA = vaultATAMap.get(vaultAsset);
      extraAccounts.add(vaultATA);
      returnAccounts.add(vaultATA.publicKey());
//      final var vaultTokenAccount = accountMap.get(vaultATA.publicKey());
//      if (AccountFetcher.isNull(vaultTokenAccount)
//          || ByteUtil.getInt64LE(vaultTokenAccount.data(), TokenAccount.AMOUNT_OFFSET) == 0) {
//        continue;
//      }

      final var stakePoolContext = serviceContext.stakePoolContextForMint(vaultAsset);
      if (stakePoolContext != null) {
        extraAccounts.add(stakePoolContext.readMint());
        final var stakePoolState = stakePoolContext.readState();
        extraAccounts.add(stakePoolState);
        returnAccounts.add(stakePoolState.publicKey());
        returnAccounts.add(solUSDOracleKey);
        continue;
      }

      final var assetMeta = serviceContext.globalConfigAssetMeta(vaultAsset);
      if (assetMeta == null) {
        logger.log(WARNING, "Missing asset meta for vault asset: {0}", vaultAsset);
        return false;
      }

      extraAccounts.add(assetMeta.readAssetMint());

      // TODO: Check if State overrides default oracle.

      switch (assetMeta.oracleSource()) {
        case NotSet, BaseAsset, QuoteAsset, Prelaunch, Pyth, Pyth1K, Pyth1M, PythStableCoin, Switchboard, LstPoolState,
             MarinadeState -> {
          final var msg = String.format("""
                  {
                   "event": "Invalid GlobalConfig OracleSource",
                   "mint": "%s",
                   "oracle": "%s",
                   "oracleSource": "%s"
                  }""",
              assetMeta.asset().toBase58(),
              assetMeta.oracle().toBase58(),
              assetMeta.oracleSource()
          );
          throw new IllegalStateException(msg);
        }
        case PythPull, Pyth1KPull, Pyth1MPull, PythStableCoinPull,
             PythLazer, PythLazer1K, PythLazer1M, PythLazerStableCoin,
             SwitchboardOnDemand -> {
          final var oracle = assetMeta.readOracle();
          extraAccounts.add(oracle);
          returnAccounts.add(oracle.publicKey());
        }
        case ChainlinkRWA -> {
          final var feedIndexes = serviceContext.scopeAggregateIndexes(vaultAsset, assetMeta.oracle(), OracleType.ChainlinkRWA);
          if (feedIndexes == null) {
            logger.log(WARNING, "Missing feed indexes for vault asset: {0}", vaultAsset);
            return false;
          } else {
            aggIndexes[i] = feedIndexes.indexes();
            final var priceFeed = feedIndexes.readPriceFeed();
            extraAccounts.add(priceFeed);
            returnAccounts.add(priceFeed.publicKey());
            final var oracleMappings = feedIndexes.readOracleMappings();
            if (scopeOracleMappingAccounts == null) {
              scopeOracleMappingAccounts = Set.of(oracleMappings);
            } else if (!scopeOracleMappingAccounts.contains(oracleMappings)) {
              if (scopeOracleMappingAccounts.size() == 1) {
                scopeOracleMappingAccounts = Set.of(scopeOracleMappingAccounts.iterator().next(), oracleMappings);
              } else {
                scopeOracleMappingAccounts = new HashSet<>(scopeOracleMappingAccounts);
                scopeOracleMappingAccounts.add(oracleMappings);
              }
            }
          }
        }
      }
    }
    if (scopeOracleMappingAccounts != null) {
      extraAccounts.addAll(scopeOracleMappingAccounts);
    }

    final var priceVaultIx = glamAccountClient.priceVaultTokens(
        solUSDOracleKey,
        baseAssetUSDOracleKey,
        aggIndexes,
        true
    ).extraAccounts(extraAccounts);
    priceInstructions.add(priceVaultIx);
    return true;
  }

  static BigDecimal parsePrice(final AccountInfo<byte[]> oracleAccountInfo) {
    final byte[] oracleData = oracleAccountInfo.data();
    if (PythLazerOracle.BYTES == oracleData.length && PythLazerOracle.DISCRIMINATOR.equals(oracleData, 0)) {
      final long price = ByteUtil.getInt64LE(oracleData, PythLazerOracle.PRICE_OFFSET);
      final int exponent = ByteUtil.getInt32LE(oracleData, PythLazerOracle.EXPONENT_OFFSET);
      return OracleUtil.scalePrice(price, exponent);
    } else if (PriceUpdateV2.DISCRIMINATOR.equals(oracleData, 0)) {
      final var priceUpdateV2 = PriceUpdateV2.read(oracleAccountInfo);
      return OracleUtil.scalePythPullPrice(priceUpdateV2);
    } else if (PullFeedAccountData.BYTES == oracleData.length && PullFeedAccountData.DISCRIMINATOR.equals(oracleData, 0)) {
      final var median = ByteUtil.getInt128LE(oracleData, PullFeedAccountData.RESULT_OFFSET);
      return OracleUtil.scalePrice(median, 18);
    } else {
      throw new IllegalStateException("Unsupported oracle account: " + oracleAccountInfo.pubKey());
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
    final var priceIx = priceInstructions.get(ixIndex);
    final var solAssetMeta = serviceContext.solAssetMeta();
    final var solOracle = solAssetMeta.oracle();
    final var solOracleAccount = returnedAccountsMap.get(solOracle);
    final BigDecimal solUSDPrice;
    if (solOracleAccount == null) {
      solUSDPrice = null;
    } else {
      solUSDPrice = parsePrice(solOracleAccount);
      assetPrices.put(PublicKey.NONE, solUSDPrice);
    }

    final var vaultAssets = stateAccount.assets();
    final var tokenReports = new ArrayList<PositionReport>(vaultAssets.length);
    for (int i = 0; i < vaultAssets.length; i++) {
      final var vaultAsset = vaultAssets[i];
      final var vaultATA = vaultATAMap.get(vaultAsset);

      final var vaultTokenAccount = returnedAccountsMap.get(vaultATA.publicKey());
      final PositionReport positionReport;
      if (AccountFetcher.isNull(vaultTokenAccount)) {
        positionReport = PositionReport.zero(vaultAsset);
      } else {
        final long amount = ByteUtil.getInt64LE(vaultTokenAccount.data(), TokenAccount.AMOUNT_OFFSET);
        if (amount == 0) {
          positionReport = PositionReport.zero(vaultAsset);
        } else {
          final var stakePoolContext = serviceContext.stakePoolContextForMint(vaultAsset);
          if (stakePoolContext != null) {
            final var decimalAmount = LamportDecimal.toBigDecimal(amount);

            final var stakePoolStateKey = stakePoolContext.stateKey();
            final var accountInfo = returnedAccountsMap.get(stakePoolStateKey);
            final byte[] data = accountInfo.data();
            final BigDecimal solPrice;
            if (accountInfo.pubKey().equals(MarinadeAccounts.MAIN_NET.stateAccount())) {
              final long delayedUnstakeCoolingDown = ByteUtil.getInt64LE(data, State.STAKE_SYSTEM_OFFSET + StakeSystem.DELAYED_UNSTAKE_COOLING_DOWN_OFFSET);
              final long totalActiveBalance = ByteUtil.getInt64LE(data, State.VALIDATOR_SYSTEM_OFFSET + ValidatorSystem.TOTAL_ACTIVE_BALANCE_OFFSET);
              final long availableReserveBalance = ByteUtil.getInt64LE(data, State.AVAILABLE_RESERVE_BALANCE_OFFSET);
              final long circulatingTicketBalance = ByteUtil.getInt64LE(data, State.CIRCULATING_TICKET_BALANCE_OFFSET);
              final long emergencyCoolingDown = ByteUtil.getInt64LE(data, State.EMERGENCY_COOLING_DOWN_OFFSET);
              final var totalVirtualStakedLamports = new BigDecimal(BigInteger.valueOf(delayedUnstakeCoolingDown)
                  .add(BigInteger.valueOf(totalActiveBalance))
                  .add(BigInteger.valueOf(availableReserveBalance))
                  .add(BigInteger.valueOf(emergencyCoolingDown))
                  .subtract(BigInteger.valueOf(circulatingTicketBalance)));
              final long msolSupply = ByteUtil.getInt64LE(data, State.MSOL_SUPPLY_OFFSET);
              solPrice = totalVirtualStakedLamports.divide(BigDecimal.valueOf(msolSupply), MathContext.DECIMAL64);
            } else {
              final long totalLamports = ByteUtil.getInt64LE(data, StakePoolState.TOTAL_LAMPORTS_OFFSET);
              final long poolTokenSupply = ByteUtil.getInt64LE(data, StakePoolState.POOL_TOKEN_SUPPLY_OFFSET);
              if (totalLamports == 0 || poolTokenSupply == 0) {
                solPrice = BigDecimal.ZERO;
              } else {
                solPrice = BigDecimal.valueOf(totalLamports)
                    .divide(BigDecimal.valueOf(poolTokenSupply), MathContext.DECIMAL64);
              }
            }

            final var price = solPrice.multiply(solUSDPrice);
            assetPrices.put(vaultAsset, price);
            // final var solAmount = decimalAmount.multiply(solPrice);
            final var value = decimalAmount.multiply(price);
            positionReport = PositionReport.create(vaultAsset, decimalAmount, value);
          } else {
            final var assetMeta = serviceContext.globalConfigAssetMeta(vaultAsset);
            final var decimalAmount = assetMeta.toDecimal(amount);
            final var oracleSource = assetMeta.oracleSource();
            positionReport = switch (oracleSource) {
              case NotSet, BaseAsset, QuoteAsset, Prelaunch, Pyth, Pyth1K, Pyth1M, PythStableCoin, Switchboard,
                   LstPoolState, MarinadeState -> {
                final var msg = String.format("""
                        {
                         "event": "Invalid GlobalConfig OracleSource",
                         "mint": "%s",
                         "oracle": "%s",
                         "oracleSource": "%s"
                        }""",
                    assetMeta.asset().toBase58(),
                    assetMeta.oracle().toBase58(),
                    assetMeta.oracleSource()
                );
                throw new IllegalStateException(msg);
              }
              case PythPull, Pyth1KPull, Pyth1MPull, PythStableCoinPull,
                   PythLazer, PythLazer1K, PythLazer1M, PythLazerStableCoin,
                   SwitchboardOnDemand -> {
                final var oracle = assetMeta.oracle();
                final var oracleAccount = returnedAccountsMap.get(oracle);
                final var price = parsePrice(oracleAccount);
                final var scaledPrice = switch (oracleSource) {
                  case Pyth1KPull, PythLazer1K -> price.movePointRight(3);
                  case Pyth1MPull, PythLazer1M -> price.movePointRight(6);
                  default -> price;
                };
                assetPrices.put(vaultAsset, scaledPrice);
                final var value = decimalAmount.multiply(scaledPrice);
                yield PositionReport.create(vaultAsset, decimalAmount, value);
              }
              case ChainlinkRWA -> {
                final var feedIndexes = serviceContext.scopeAggregateIndexes(vaultAsset, assetMeta.oracle(), OracleType.ChainlinkRWA);
                final var priceFeedAccount = returnedAccountsMap.get(feedIndexes.priceFeed());
                final short[] indexes = new short[4];
                SerDeUtil.readArray(
                    indexes,
                    priceIx.data(),
                    GlamMintProgram.PriceVaultTokensIxData.AGG_INDEXES_OFFSET + Integer.BYTES + (i * (Short.BYTES * indexes.length))
                );
                // TODO: Support complex scope entries.
                final var price = ScopeReader.scaleScopePrice(priceFeedAccount.data(), indexes[0]);
                assetPrices.put(vaultAsset, price);
                final var value = decimalAmount.multiply(price);
                yield PositionReport.create(vaultAsset, decimalAmount, value);
              }
            };
          }
        }
      }

      tokenReports.add(positionReport);
    }

    final var innerInstructions = innerInstructionsArray[ixIndex];
    final var positionAmount = Position.parseAnchorEvent(innerInstructions, mintProgram, stateAccount.baseAssetDecimals());
    final var reportNode = new PositionReportNode(positionAmount, tokenReports);
    positionReportsList.add(reportNode);
    return ixIndex + 1;
  }
}
