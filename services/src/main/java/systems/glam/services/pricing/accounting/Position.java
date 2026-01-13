package systems.glam.services.pricing.accounting;

import software.sava.core.accounts.PublicKey;
import software.sava.core.tx.Instruction;
import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.sdk.GlamAccountClient;

import java.util.Map;
import java.util.Set;

public interface Position {

  void accountsForPriceInstruction(final Set<PublicKey> keys);

  Instruction priceInstruction(GlamAccountClient glamAccountClient,
                               PublicKey solUSDOracleKey,
                               PublicKey baseAssetUSDOracleKey,
                               Map<PublicKey, AccountInfo<byte[]>> accountMap,
                               Set<PublicKey> returnAccounts);
}
