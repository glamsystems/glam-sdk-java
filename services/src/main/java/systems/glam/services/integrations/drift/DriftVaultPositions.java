package systems.glam.services.integrations.drift;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.drift.DriftAccounts;
import software.sava.idl.clients.drift.DriftPDAs;
import software.sava.idl.clients.drift.gen.types.User;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.InnerInstructions;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.services.integrations.IntegrationServiceContext;
import systems.glam.services.pricing.accounting.AggregatePositionReport;
import systems.glam.services.pricing.accounting.Position;
import systems.glam.services.pricing.accounting.PositionReportNode;
import systems.glam.services.state.MinGlamStateAccount;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static systems.glam.services.integrations.drift.DriftUsersPosition.priceUser;

public final class DriftVaultPositions implements Position {

  private static final Map<PublicKey, VaultContext> VAULT_META_MAP = new ConcurrentHashMap<>();

  private record VaultContext(AccountMeta readVault, AccountMeta readVaultUser) {

    static VaultContext create(final DriftAccounts driftAccounts, final PublicKey vault) {
      return new VaultContext(
          AccountMeta.createRead(vault),
          AccountMeta.createRead(DriftPDAs.deriveMainUserAccount(driftAccounts, vault).publicKey())
      );
    }
  }

  private final Map<AccountMeta, VaultContext> depositorVaultContextMap;

  public DriftVaultPositions() {
    this.depositorVaultContextMap = new HashMap<>();
  }

  public PublicKey addDepositor(final DriftAccounts driftAccounts, final PublicKey depositor, final PublicKey vault) {
    final var vaultContext = VAULT_META_MAP.computeIfAbsent(vault, _ -> VaultContext.create(driftAccounts, vault));
    depositorVaultContextMap.put(AccountMeta.createRead(depositor), vaultContext);
    return vaultContext.readVaultUser().publicKey();
  }

  @Override
  public void removeAccount(final PublicKey vaultDepositor) {
    final var iterator = depositorVaultContextMap.entrySet().iterator();
    while (iterator.hasNext()) {
      if (iterator.next().getKey().publicKey().equals(vaultDepositor)) {
        iterator.remove();
        return;
      }
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
    final int numVaults = depositorVaultContextMap.size();
    final var spotMarkets = HashMap.<PublicKey, AccountMeta>newHashMap(numVaults * User.SPOT_POSITIONS_LEN);
    final var perpMarkets = HashMap.<PublicKey, AccountMeta>newHashMap(numVaults * User.PERP_POSITIONS_LEN);
    final var oracleAccounts = HashMap.<PublicKey, AccountMeta>newHashMap(numVaults * (User.SPOT_POSITIONS_LEN + User.PERP_POSITIONS_LEN));
    final var driftMarketCache = serviceContext.driftMarketCache();
    int missingMarkets = 0;
    for (final var entry : depositorVaultContextMap.entrySet()) {
      missingMarkets += priceUser(
          entry.getValue().readVaultUser().publicKey(),
          driftMarketCache,
          accountMap,
          spotMarkets, perpMarkets, oracleAccounts
      );
    }

    if (missingMarkets != 0) {
      return false;
    } else {
      final int numSpotMarkets = spotMarkets.size();
      final int numPerpMarkets = perpMarkets.size();
      final var extraAccounts = new ArrayList<AccountMeta>((numVaults * 3) + numSpotMarkets + numPerpMarkets + oracleAccounts.size());
      for (final var entry : depositorVaultContextMap.entrySet()) {
        extraAccounts.add(entry.getKey());
        final var vaultContext = entry.getValue();
        extraAccounts.add(vaultContext.readVault());
        extraAccounts.add(vaultContext.readVaultUser());
      }
      extraAccounts.addAll(spotMarkets.values());
      extraAccounts.addAll(perpMarkets.values());
      extraAccounts.addAll(oracleAccounts.values());

      final var priceIx = glamAccountClient.priceDriftVaultDepositors(
          solUSDOracleKey, baseAssetUSDOracleKey,
          numVaults, numSpotMarkets, numPerpMarkets,
          true
      ).extraAccounts(extraAccounts);

      priceInstructions.add(priceIx);
      return true;
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
