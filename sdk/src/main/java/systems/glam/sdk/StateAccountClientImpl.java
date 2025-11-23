package systems.glam.sdk;

import software.sava.core.accounts.ProgramDerivedAddress;
import software.sava.core.accounts.PublicKey;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.IntegrationAcl;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateAccount;

import java.util.Map;

import static systems.glam.sdk.idl.programs.glam.drift.gen.ExtDriftConstants.PROTO_DRIFT_PROTOCOL;
import static systems.glam.sdk.idl.programs.glam.drift.gen.ExtDriftConstants.PROTO_DRIFT_VAULTS;
import static systems.glam.sdk.idl.programs.glam.kamino.gen.ExtKaminoConstants.PROTO_KAMINO_LENDING;
import static systems.glam.sdk.idl.programs.glam.kamino.gen.ExtKaminoConstants.PROTO_KAMINO_VAULTS;
import static systems.glam.sdk.idl.programs.glam.protocol.gen.GlamProtocolConstants.PROTO_JUPITER_SWAP;

public record StateAccountClientImpl(StateAccount stateAccount,
                                     GlamAccountClient accountClient,
                                     ProgramDerivedAddress escrowAccount,
                                     Map<PublicKey, IntegrationAcl> integrationAclMap) implements StateAccountClient {

  @Override
  public boolean integrationEnabled(final PublicKey integrationProgram, final int bitFlag) {
    if (integrationAclMap.get(integrationProgram) instanceof IntegrationAcl(_, final int protocolsBitmask, _)) {
      return (protocolsBitmask & bitFlag) == bitFlag;
    } else {
      return false;
    }
  }

  @Override
  public boolean driftEnabled() {
    return integrationEnabled(accountClient.glamAccounts().driftIntegrationProgram(), PROTO_DRIFT_PROTOCOL);
  }

  @Override
  public boolean driftVaultsEnabled() {
    return integrationEnabled(accountClient.glamAccounts().driftIntegrationProgram(), PROTO_DRIFT_VAULTS);
  }

  @Override
  public boolean kaminoLendEnabled() {
    return integrationEnabled(accountClient.glamAccounts().kaminoIntegrationProgram(), PROTO_KAMINO_LENDING);
  }

  @Override
  public boolean kaminoVaultsEnabled() {
    return integrationEnabled(accountClient.glamAccounts().kaminoIntegrationProgram(), PROTO_KAMINO_VAULTS);
  }

  @Override
  public boolean jupiterSwapEnabled() {
    return integrationEnabled(accountClient.glamAccounts().protocolProgram(), PROTO_JUPITER_SWAP);
  }
}
