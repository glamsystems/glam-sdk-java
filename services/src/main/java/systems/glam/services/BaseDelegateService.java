package systems.glam.services;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.sysvar.Clock;
import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateAccount;

import java.util.Map;

public abstract class BaseDelegateService implements DelegateService {

  protected final ServiceContext context;
  protected final GlamAccountClient glamAccountClient;

  public BaseDelegateService(final ServiceContext context, final GlamAccountClient glamAccountClient) {
    this.context = context;
    this.glamAccountClient = glamAccountClient;
  }

  protected final Clock clock(final Map<PublicKey, AccountInfo<byte[]>> accountsNeededMap) {
    final var clockSysVar = context.clockSysVar();
    final var clockAccount = accountsNeededMap.get(clockSysVar);
    return Clock.read(clockSysVar, clockAccount.data());
  }

  protected final PublicKey stateKey() {
    return glamAccountClient.vaultAccounts().glamStateKey();
  }

  protected final StateAccount stateAccount(final Map<PublicKey, AccountInfo<byte[]>> accountsNeededMap) {
    return StateAccount.read(accountsNeededMap.get(stateKey()));
  }
}
