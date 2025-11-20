package systems.glam.sdk;

import software.sava.core.accounts.ProgramDerivedAddress;
import software.sava.core.accounts.PublicKey;
import systems.glam.sdk.idl.programs.glam.drift.gen.ExtDriftConstants;
import systems.glam.sdk.idl.programs.glam.kamino.gen.ExtKaminoConstants;
import systems.glam.sdk.idl.programs.glam.protocol.gen.GlamProtocolConstants;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.IntegrationAcl;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateAccount;

import java.util.Map;

public record StateAccountClientImpl(StateAccount stateAccount,
                                     GlamAccountClient accountClient,
                                     ProgramDerivedAddress escrowAccount,
                                     Map<PublicKey, IntegrationAcl> integrationAclMap) implements StateAccountClient {

  @Override
  public boolean integrationEnabled(final PublicKey extensionProgram, final int bitFlag) {
    if (integrationAclMap.get(extensionProgram) instanceof IntegrationAcl(_, final int protocolsBitmask, _)) {
      return (protocolsBitmask & bitFlag) == bitFlag;
    } else {
      return false;
    }
  }

  @Override
  public boolean driftEnabled() {
    final var glamAccounts = accountClient.glamAccounts();
    return integrationEnabled(glamAccounts.driftIntegrationProgram(), ExtDriftConstants.PROTO_DRIFT_PROTOCOL);
  }

  @Override
  public boolean driftVaultsEnabled() {
    final var glamAccounts = accountClient.glamAccounts();
    return integrationEnabled(glamAccounts.driftIntegrationProgram(), ExtDriftConstants.PROTO_DRIFT_VAULTS);
  }

  @Override
  public boolean kaminoLendEnabled() {
    final var glamAccounts = accountClient.glamAccounts();
    return integrationEnabled(glamAccounts.kaminoIntegrationProgram(), ExtKaminoConstants.PROTO_KAMINO_LENDING);
  }

  @Override
  public boolean kaminoVaultsEnabled() {
    final var glamAccounts = accountClient.glamAccounts();
    return integrationEnabled(glamAccounts.kaminoIntegrationProgram(), ExtKaminoConstants.PROTO_KAMINO_VAULTS);
  }

  @Override
  public boolean jupiterSwapEnabled() {
    final var glamAccounts = accountClient.glamAccounts();
    return integrationEnabled(glamAccounts.protocolProgram(), GlamProtocolConstants.PROTO_JUPITER_SWAP);
  }
}
