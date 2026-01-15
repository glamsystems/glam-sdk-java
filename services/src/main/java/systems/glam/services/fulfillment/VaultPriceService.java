package systems.glam.services.fulfillment;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.lookup.AddressLookupTable;
import software.sava.rpc.json.http.response.AccountInfo;

public interface VaultPriceService extends DelegateService {

  boolean unsupported();

  boolean stateChange(final AccountInfo<byte[]> account);

  void removeTable(final PublicKey tableKey);

  void glamVaultTableUpdate(final AddressLookupTable addressLookupTable);
}
