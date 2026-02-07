package systems.glam.services.pricing.accounting;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.kamino.scope.gen.types.OracleType;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.InnerInstructions;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.services.integrations.IntegrationServiceContext;
import systems.glam.services.pricing.ScopeAggregateIndexes;
import systems.glam.services.state.MinGlamStateAccount;

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
    for (int i = 0; i < vaultAssets.length; i++) {
      final var vaultAsset = vaultAssets[i];
      final var vaultATA = vaultATAMap.get(vaultAsset);
//      final var vaultTokenAccount = accountMap.get(vaultATA.publicKey());
//      if (AccountFetcher.isNull(vaultTokenAccount)) {
////          || ByteUtil.getInt64LE(vaultTokenAccount.data(), TokenAccount.AMOUNT_OFFSET) == 0) {
//        continue;
//      }

      extraAccounts.add(vaultATA);

      final var assetMeta = serviceContext.globalConfigAssetMeta(vaultAsset);
      if (assetMeta == null) {
        final var stakePoolContext = serviceContext.stakePoolContextForMint(vaultAsset);
        if (stakePoolContext == null) {
          logger.log(WARNING, "Missing asset meta for vault asset: {0}", vaultAsset);
          return false;
        } else {
          extraAccounts.add(stakePoolContext.readState());
          continue;
        }
      }

      // TODO: Check if State overrides default oracle.

      extraAccounts.add(assetMeta.readAssetMint());
      switch (assetMeta.oracleSource()) {
        case NotSet, BaseAsset, QuoteAsset, Prelaunch, Pyth, Pyth1K, Pyth1M, PythStableCoin, Switchboard -> {
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
        case PythPull, Pyth1KPull, Pyth1MPull, PythStableCoinPull, SwitchboardOnDemand, PythLazer, PythLazer1K,
             PythLazer1M, PythLazerStableCoin, LstPoolState, MarinadeState -> extraAccounts.add(assetMeta.readOracle());
        case ChainlinkRWA -> {
          final var feedIndexes = serviceContext.scopeAggregateIndexes(vaultAsset, assetMeta.oracle(), OracleType.ChainlinkRWA);
          if (feedIndexes == null) {
            logger.log(WARNING, "Missing feed indexes for vault asset: {0}", vaultAsset);
            return false;
          } else {
            extraAccounts.add(feedIndexes.readPriceFeed());
            aggIndexes[i] = feedIndexes.indexes();
          }
        }
      }
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

  @Override
  public PositionReport positionReport(final PublicKey mintProgram,
                                       final int baseAssetDecimals,
                                       final Map<PublicKey, AccountInfo<byte[]>> returnedAccountsMap,
                                       final InnerInstructions innerInstructions) {
    return null;
  }
}
