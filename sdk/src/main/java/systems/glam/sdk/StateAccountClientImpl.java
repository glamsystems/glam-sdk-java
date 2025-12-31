package systems.glam.sdk;

import software.sava.core.accounts.ProgramDerivedAddress;
import software.sava.core.accounts.PublicKey;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.*;

import java.util.Arrays;
import java.util.Map;

public final class StateAccountClientImpl extends BaseStateAccountClient {

  private final StateAccount stateAccount;
  private final Map<PublicKey, IntegrationAcl> integrationAclMap;
  private final NotifyAndSettle notifyAndSettle;

  public StateAccountClientImpl(final StateAccount stateAccount,
                                final GlamAccountClient accountClient,
                                final ProgramDerivedAddress escrowAccount,
                                final Map<PublicKey, IntegrationAcl> integrationAclMap,
                                final Map<PublicKey, Map<PublicKey, Map<Protocol, ProtocolPermissions>>> delegatePermissions) {
    super(accountClient, stateAccount.name(), escrowAccount, delegatePermissions);
    this.stateAccount = stateAccount;
    this.integrationAclMap = integrationAclMap;
    this.notifyAndSettle = Arrays.stream(stateAccount.params()).flatMap(Arrays::stream).<NotifyAndSettle>mapMulti((engineField, downstream) -> {
      if (engineField.value() instanceof EngineFieldValue.NotifyAndSettle(final var _notifyAndSettle)) {
        downstream.accept(_notifyAndSettle);
      }
    }).findFirst().orElse(null);
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
  public long redeemNoticePeriod() {
    return notifyAndSettle.redeemNoticePeriod();
  }

  @Override
  public long redeemSettlementPeriod() {
    return notifyAndSettle.redeemSettlementPeriod();
  }

  @Override
  public long redeemCancellationWindow() {
    return notifyAndSettle.redeemCancellationWindow();
  }

  @Override
  public boolean redeemWindowInSeconds() {
    return notifyAndSettle.timeUnit() == TimeUnit.Second;
  }

  @Override
  public boolean softRedeem() {
    return notifyAndSettle.redeemNoticePeriodType() == NoticePeriodType.Soft;
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
