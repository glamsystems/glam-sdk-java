package systems.glam.services.pricing;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.lookup.AddressLookupTable;
import software.sava.rpc.json.http.response.AccountInfo;

public interface VaultStateContext {

  void init();

  /**
   * Update the local StateAccount if changed and supported.
   *
   * @param account The StateAccount AccountInfo for the vault.
   * @return True if the state is supported for the underlying implementation purpose. True does not indicate that there was a state transition.
   */
  boolean stateChange(final AccountInfo<byte[]> account);

  void removeTable(final PublicKey tableKey);

  void glamVaultTableUpdate(final AddressLookupTable addressLookupTable);
}
