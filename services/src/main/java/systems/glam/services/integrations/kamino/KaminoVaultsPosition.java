package systems.glam.services.integrations.kamino;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.InnerInstructions;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.services.integrations.IntegrationServiceContext;
import systems.glam.services.pricing.accounting.AggregatePositionReport;
import systems.glam.services.pricing.accounting.Position;
import systems.glam.services.state.MinGlamStateAccount;

import java.math.BigDecimal;
import java.util.*;

public final class KaminoVaultsPosition implements Position {

  private record ExtraAccounts(AccountMeta vaultTokenAccount,
                               AccountMeta kVaultSharesMint,
                               AccountMeta kVaultState,
                               AccountMeta kVaultBaseMint) {

  }

  private final Map<PublicKey, ExtraAccounts> extraAccounts;

  public KaminoVaultsPosition() {
    this.extraAccounts = new HashMap<>();
  }

  @Override
  public void removeAccount(final PublicKey account) {
    extraAccounts.remove(account);
  }

  @Override
  public void accountsForPriceInstruction(final Set<PublicKey> keys) {
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

    final var priceIx = glamAccountClient.priceKaminoVaultShares(
        solUSDOracleKey,
        baseAssetUSDOracleKey,
        0,
        true
    );
    return false;
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
    return -1;
  }
}
