package systems.glam.services.fulfillment.drfit;

import software.sava.core.accounts.PublicKey;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.drift.vaults.DriftVaultsProgramClient;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.InnerInstructions;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.services.integrations.IntegrationServiceContext;
import systems.glam.services.pricing.MinStateAccount;
import systems.glam.services.pricing.PositionReport;
import systems.glam.services.pricing.accounting.Position;

import java.util.Map;
import java.util.Set;

public final class DriftVaultPosition implements Position {

  private final DriftVaultsProgramClient driftVaultClient;
  private final PublicKey vaultKey;
  private final DriftUsersPosition driftUsersPosition;
  private final PublicKey vaultDepositorKey;

  public DriftVaultPosition(final DriftVaultsProgramClient driftVaultClient,
                            final PublicKey vaultKey,
                            final DriftUsersPosition driftUsersPosition,
                            final PublicKey vaultDepositorKey) {
    this.driftVaultClient = driftVaultClient;
    this.vaultKey = vaultKey;
    this.driftUsersPosition = driftUsersPosition;
    this.vaultDepositorKey = vaultDepositorKey;
  }

  @Override
  public void removeAccount(final PublicKey account) {

  }

  @Override
  public void accountsForPriceInstruction(final Set<PublicKey> keys) {
    keys.add(vaultKey);
    keys.add(vaultDepositorKey);
    driftUsersPosition.accountsForPriceInstruction(keys);
  }

  @Override
  public Instruction priceInstruction(final IntegrationServiceContext serviceContext, final GlamAccountClient glamAccountClient,
                                      final PublicKey baseAssetUSDOracleKey, final MinStateAccount stateAccount, final Map<PublicKey, AccountInfo<byte[]>> accountMap,
                                      final Set<PublicKey> returnAccounts) {
    return null;
  }

  @Override
  public PositionReport positionReport(final PublicKey mintProgram,
                                       final int baseAssetDecimals,
                                       final Map<PublicKey, AccountInfo<byte[]>> accountMap,
                                       final InnerInstructions innerInstructions) {
    return null;
  }
}
