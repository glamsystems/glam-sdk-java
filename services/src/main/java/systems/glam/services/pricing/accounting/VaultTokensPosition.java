package systems.glam.services.pricing.accounting;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.InnerInstructions;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.services.integrations.IntegrationServiceContext;
import systems.glam.services.pricing.MinGlamStateAccount;
import systems.glam.services.pricing.PositionReport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class VaultTokensPosition implements Position {

  private final PublicKey baseAssetMint;
  private final Map<PublicKey, AccountMeta> vaultATAMap;

  public VaultTokensPosition(final PublicKey baseAssetMint, final int numAssets) {
    this.baseAssetMint = baseAssetMint;
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

  public PublicKey baseAssetMint() {
    return baseAssetMint;
  }

  public Map<PublicKey, AccountMeta> vaultATAMap() {
    return vaultATAMap;
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
    // Don't cache instruction without re-acting to State Account oracle priority changes and global config changes.
    final var vaultAssets = stateAccount.assets();
    final short[][] aggIndexes = new short[vaultAssets.length][NO_AGGREGATE_INDEXES.length];
    final var extraAccounts = new ArrayList<AccountMeta>((vaultAssets.length * 3));
    for (int i = 0; i < vaultAssets.length; i++) {
      final var vaultAsset = vaultAssets[i];
      final var vaultATA = vaultATAMap.get(vaultAsset);
      extraAccounts.add(vaultATA);
      final var mintContext = serviceContext.mintContext(vaultAsset);
      extraAccounts.add(mintContext.readMintMeta());

      // TODO: Add Kamino Scope Support
//        final PublicKey oracle;
//        final var oracleContext = oracleContextMap.get(vaultAsset);
//        if (oracleContext != null) {
//          aggIndexes[i] = new short[]{(short) oracleContext.index(), -1, -1, -1};
//          oracle = oracleContext.oracleKey();
//        } else {
//          aggIndexes[i] = NO_AGGREGATE_INDEXES;
//          oracle = assetMetaMap.get(mintContext.mint()).oracle();
//        }
//        extraAccounts.add(AccountMeta.createRead(oracle));

      aggIndexes[i] = NO_AGGREGATE_INDEXES;
      // TODO: Check if State overrides default oracle.
      final var assetMeta = serviceContext.globalConfigAssetMeta(mintContext.mint());
      if (assetMeta == null) {
        return null;
      }
      extraAccounts.add(AccountMeta.createRead(assetMeta.oracle()));
    }
    // Add scope aggregate keys
    //extraAccounts.addAll(this.extraAccounts);


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
                                       final Map<PublicKey, AccountInfo<byte[]>> accountMap,
                                       final InnerInstructions innerInstructions) {
    return null;
  }
}
