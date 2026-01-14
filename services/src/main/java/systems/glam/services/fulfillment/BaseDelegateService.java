package systems.glam.services.fulfillment;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.sysvar.Clock;
import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateAccount;
import systems.glam.services.ServiceContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class BaseDelegateService implements DelegateService {

  protected final ServiceContext context;
  protected final GlamAccountClient glamAccountClient;
  protected final String vaultName;

  public BaseDelegateService(final ServiceContext context,
                             final GlamAccountClient glamAccountClient,
                             final String vaultName) {
    this.context = context;
    this.glamAccountClient = glamAccountClient;
    this.vaultName = vaultName;
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
