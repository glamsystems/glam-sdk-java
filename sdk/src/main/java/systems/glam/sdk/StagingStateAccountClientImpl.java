package systems.glam.sdk;

import software.sava.core.accounts.ProgramDerivedAddress;
import software.sava.core.accounts.PublicKey;
import systems.glam.sdk.idl.programs.glam.staging.protocol.gen.types.IntegrationAcl;
import systems.glam.sdk.idl.programs.glam.staging.protocol.gen.types.StateAccount;

import java.util.Map;

public class StagingStateAccountClientImpl extends BaseStateAccountClient {

  private final StateAccount stateAccount;
  private final Map<PublicKey, IntegrationAcl> integrationAclMap;

  public StagingStateAccountClientImpl(final StateAccount stateAccount,
                                       final GlamAccountClient accountClient,
                                       final ProgramDerivedAddress escrowAccount,
                                       final Map<PublicKey, IntegrationAcl> integrationAclMap,
                                       final Map<PublicKey, Map<PublicKey, Map<Protocol, ProtocolPermissions>>> delegatePermissions) {
    super(accountClient, stateAccount.name(), escrowAccount, delegatePermissions);
    this.stateAccount = stateAccount;
    this.integrationAclMap = integrationAclMap;
  }

  @Override
  public PublicKey mint() {
    return stateAccount.mint();
  }

  @Override
  public PublicKey baseAssetMint() {
    return stateAccount.baseAssetMint();
  }

  @Override
  public PublicKey[] externalPositions() {
    return stateAccount.externalPositions();
  }

  @Override
  public PublicKey[] assets() {
    return stateAccount.assets();
  }

  @Override
  protected int protocolBitmask(final PublicKey integrationProgram) {
    if (integrationAclMap.get(integrationProgram) instanceof IntegrationAcl(_, final int protocolsBitmask, _)) {
      return protocolsBitmask;
    } else {
      return 0;
    }
  }
}
