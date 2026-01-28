package systems.glam.services.integrations.kamino;

import software.sava.core.accounts.PublicKey;
import software.sava.core.tx.Instruction;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.InnerInstructions;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.services.integrations.IntegrationServiceContext;
import systems.glam.services.state.MinGlamStateAccount;
import systems.glam.services.pricing.accounting.PositionReport;
import systems.glam.services.pricing.accounting.Position;

import java.util.Map;
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
  public Instruction priceInstruction(final IntegrationServiceContext serviceContext,
                                      final GlamAccountClient glamAccountClient,
                                      final PublicKey solUSDOracleKey,
                                      final PublicKey baseAssetUSDOracleKey, final MinGlamStateAccount stateAccount,
                                      final Map<PublicKey, AccountInfo<byte[]>> accountMap,
                                      final Set<PublicKey> returnAccounts) {
    return null;
  }

  @Override
  public PositionReport positionReport(final PublicKey mintProgram,
                                       final int baseAssetDecimals,
                                       final Map<PublicKey, AccountInfo<byte[]>> returnedAccountsMap,
                                       final InnerInstructions innerInstructions) {
    return null;
  }
}
