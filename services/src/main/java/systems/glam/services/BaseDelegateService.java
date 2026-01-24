package systems.glam.services;

import software.sava.core.accounts.PublicKey;
import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateAccount;

import java.util.Map;

public abstract class BaseDelegateService implements DelegateService {

  protected final GlamAccountClient glamAccountClient;

  public BaseDelegateService(final GlamAccountClient glamAccountClient) {
    this.glamAccountClient = glamAccountClient;
  }

  protected final PublicKey stateKey() {
    return glamAccountClient.vaultAccounts().glamStateKey();
  }

  protected final StateAccount stateAccount(final Map<PublicKey, AccountInfo<byte[]>> accountsNeededMap) {
    return StateAccount.read(accountsNeededMap.get(stateKey()));
  }
}
