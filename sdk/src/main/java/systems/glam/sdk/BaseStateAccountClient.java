package systems.glam.sdk;

import software.sava.core.accounts.ProgramDerivedAddress;
import software.sava.core.accounts.PublicKey;

import java.util.Map;

import static systems.glam.sdk.idl.programs.glam.drift.gen.ExtDriftConstants.PROTO_DRIFT_PROTOCOL;
import static systems.glam.sdk.idl.programs.glam.drift.gen.ExtDriftConstants.PROTO_DRIFT_VAULTS;
import static systems.glam.sdk.idl.programs.glam.kamino.gen.ExtKaminoConstants.PROTO_KAMINO_LENDING;
import static systems.glam.sdk.idl.programs.glam.kamino.gen.ExtKaminoConstants.PROTO_KAMINO_VAULTS;
import static systems.glam.sdk.idl.programs.glam.protocol.gen.GlamProtocolConstants.PROTO_JUPITER_SWAP;

public abstract class BaseStateAccountClient implements StateAccountClient {

  protected final GlamAccountClient accountClient;
  protected final GlamAccounts glamAccounts;
  private final String name;
  protected final ProgramDerivedAddress escrowAccount;
  private final Map<PublicKey, Map<PublicKey, Map<Protocol, ProtocolPermissions>>> delegatePermissions;

  public BaseStateAccountClient(final GlamAccountClient accountClient,
                                final byte[] name,
                                final ProgramDerivedAddress escrowAccount,
                                final Map<PublicKey, Map<PublicKey, Map<Protocol, ProtocolPermissions>>> delegatePermissions) {
    this.accountClient = accountClient;
    this.glamAccounts = accountClient.glamAccounts();
    this.name = GlamUtil.parseFixLengthString(name);
    this.escrowAccount = escrowAccount;
    this.delegatePermissions = delegatePermissions;
  }

  @Override
  public final GlamAccountClient accountClient() {
    return accountClient;
  }

  @Override
  public final String name() {
    return name;
  }

  @Override
  public final ProgramDerivedAddress escrowAccount() {
    return escrowAccount;
  }

  @Override
  public final boolean delegateHasPermissions(final PublicKey delegateKey,
                                              final Map<PublicKey, ProtocolPermissions> requiredPermissions) {
    final var delegatePermissions = this.delegatePermissions.get(delegateKey);
    for (final var entry : requiredPermissions.entrySet()) {
      final var integrationPermissions = delegatePermissions.get(entry.getKey());
      final var requiredProtocolPermissions = entry.getValue();
      final var protocolPermissions = integrationPermissions.get(requiredProtocolPermissions.protocol());
      final var mask = protocolPermissions.permissionMask();
      if ((requiredProtocolPermissions.permissionMask() & mask) != mask) {
        return false;
      }
    }
    return true;
  }

  protected abstract int protocolBitmask(final PublicKey integrationProgram);

  @Override
  public final boolean integrationEnabled(final PublicKey integrationProgram, final int bitFlag) {
    return (protocolBitmask(integrationProgram) & bitFlag) == bitFlag;
  }

  @Override
  public final boolean driftEnabled() {
    return integrationEnabled(glamAccounts.driftIntegrationProgram(), PROTO_DRIFT_PROTOCOL);
  }

  @Override
  public final boolean driftVaultsEnabled() {
    return integrationEnabled(glamAccounts.driftIntegrationProgram(), PROTO_DRIFT_VAULTS);
  }

  @Override
  public final boolean kaminoLendEnabled() {
    return integrationEnabled(glamAccounts.kaminoIntegrationProgram(), PROTO_KAMINO_LENDING);
  }

  @Override
  public final boolean kaminoVaultsEnabled() {
    return integrationEnabled(glamAccounts.kaminoIntegrationProgram(), PROTO_KAMINO_VAULTS);
  }

  @Override
  public final boolean jupiterSwapEnabled() {
    return integrationEnabled(glamAccounts.protocolProgram(), PROTO_JUPITER_SWAP);
  }
}
