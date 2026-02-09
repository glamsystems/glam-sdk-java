package systems.glam.services.integrations.kamino;

import software.sava.core.accounts.PublicKey;
import software.sava.core.tx.Instruction;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.InnerInstructions;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.services.integrations.IntegrationServiceContext;
import systems.glam.services.pricing.accounting.AggregatePositionReport;
import systems.glam.services.pricing.accounting.Position;
import systems.glam.services.state.MinGlamStateAccount;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.SequencedCollection;
import java.util.Set;

public final class KaminoVaultPosition implements Position {

  private final PublicKey glamVaultSharesTokenAccount;
  private final KaminoVaultCache kaminoVaultCache;
  private final PublicKey kVaultKey;

  public KaminoVaultPosition(final PublicKey glamVaultSharesTokenAccount,
                             final KaminoVaultCache kaminoVaultCache,
                             final PublicKey kVaultKey) {
    this.glamVaultSharesTokenAccount = glamVaultSharesTokenAccount;
    this.kaminoVaultCache = kaminoVaultCache;
    this.kVaultKey = kVaultKey;
  }

  @Override
  public void removeAccount(final PublicKey account) {

  }

  @Override
  public void accountsForPriceInstruction(final Set<PublicKey> keys) {
    keys.add(kVaultKey);
    keys.add(glamVaultSharesTokenAccount);
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
    return false;
  }

  @Override
  public int positionReport(final IntegrationServiceContext serviceContext,
                            final PublicKey mintProgram,
                            final MinGlamStateAccount stateAccount,
                            final Map<PublicKey, AccountInfo<byte[]>> returnedAccountsMap,
                            final int ixIndex,
                            final List<Instruction> priceInstructions,
                            final List<InnerInstructions> innerInstructionsList,
                            final Map<PublicKey, BigDecimal> assetPrices,
                            final List<AggregatePositionReport> positionReportsList) {
    return -1;
  }
}
