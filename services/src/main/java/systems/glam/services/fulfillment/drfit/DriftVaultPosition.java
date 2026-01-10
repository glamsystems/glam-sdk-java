package systems.glam.services.fulfillment.drfit;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.drift.vaults.DriftVaultsProgramClient;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.services.pricing.accounting.BasePosition;
import systems.glam.services.tokens.MintContext;

import java.util.Set;

public final class DriftVaultPosition extends BasePosition {

  private final DriftVaultsProgramClient driftVaultClient;
  private final PublicKey vaultKey;
  private final DriftUserPosition driftUserPosition;
  private final PublicKey vaultDepositorKey;

  public DriftVaultPosition(final MintContext mintContext,
                            final GlamAccountClient glamClient,
                            final DriftVaultsProgramClient driftVaultClient,
                            final PublicKey vaultKey, final DriftUserPosition driftUserPosition,
                            final PublicKey vaultDepositorKey) {
    super(mintContext, glamClient);
    this.driftVaultClient = driftVaultClient;
    this.vaultKey = vaultKey;
    this.driftUserPosition = driftUserPosition;
    this.vaultDepositorKey = vaultDepositorKey;
  }

  @Override
  public void accountsNeeded(final Set<PublicKey> keys) {
    keys.add(vaultKey);
    keys.add(vaultDepositorKey);
    driftUserPosition.accountsNeeded(keys);
  }
}
