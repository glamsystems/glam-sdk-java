package systems.glam.services.pricing.accounting;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.InnerInstructions;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.services.integrations.IntegrationServiceContext;
import systems.glam.services.state.MinGlamStateAccount;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
    final var iterator = vaultATAMap.entrySet().iterator();
    while (iterator.hasNext()) {
      final var entry = iterator.next();
      if (!stateAccount.containsAsset(entry.getKey())) {
        accountsNeededSet.remove(entry.getValue().publicKey());
        iterator.remove();
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

  private static final short[] NO_AGGREGATE_INDEXES = new short[]{-1, -1, -1, -1};

  @Override
  public Instruction priceInstruction(final IntegrationServiceContext serviceContext,
                                      final GlamAccountClient glamAccountClient,
                                      final PublicKey solUSDOracleKey,
                                      final PublicKey baseAssetUSDOracleKey,
                                      final MinGlamStateAccount stateAccount,
                                      final Map<PublicKey, AccountInfo<byte[]>> accountMap,
                                      final Set<PublicKey> returnAccounts) {
    final var vaultAssets = stateAccount.assets();
    final short[][] aggIndexes = new short[vaultAssets.length][NO_AGGREGATE_INDEXES.length];
    final var extraAccounts = new ArrayList<AccountMeta>((vaultAssets.length * 3));
    for (int i = 0; i < vaultAssets.length; i++) {
      final var vaultAsset = vaultAssets[i];

      final var vaultATA = vaultATAMap.get(vaultAsset);
      extraAccounts.add(vaultATA);

      final var assetMeta = serviceContext.globalConfigAssetMeta(vaultAsset);
      if (assetMeta == null) {
        final var stakePoolContext = serviceContext.stakePoolContextForMint(vaultAsset);
        if (stakePoolContext == null) {
          logger.log(WARNING, "Missing asset meta for vault asset: {0}", vaultAsset);
          return null;
        } else {
          aggIndexes[i] = NO_AGGREGATE_INDEXES;
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
             PythLazer1M, PythLazerStableCoin, LstPoolState, MarinadeState -> {
          aggIndexes[i] = NO_AGGREGATE_INDEXES;
          extraAccounts.add(assetMeta.readOracle());
        }
        case ChainlinkRWA -> {
          // TODO: Lookup potential indexes in scope aggregate account.
          //       Set oracle to corresponding aggregate account.
//          aggIndexes[i] = new short[]{(short) oracleContext.index(), -1, -1, -1};
        }
      }
    }

    final var priceVaultIx = glamAccountClient.priceVaultTokens(
        solUSDOracleKey,
        baseAssetUSDOracleKey,
        aggIndexes,
        true
    );
    return priceVaultIx.extraAccounts(extraAccounts);
  }

  @Override
  public PositionReport positionReport(final PublicKey mintProgram,
                                       final int baseAssetDecimals,
                                       final Map<PublicKey, AccountInfo<byte[]>> returnedAccountsMap,
                                       final InnerInstructions innerInstructions) {
    return null;
  }
}
