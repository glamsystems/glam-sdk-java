package systems.glam.services.pricing.accounting;

import software.sava.core.accounts.PublicKey;
import software.sava.core.tx.Instruction;
import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.services.tokens.MintContext;

import java.util.Map;
import java.util.Set;

public final class TokenPosition extends BasePosition {

  private final PublicKey vaultATA;

  public TokenPosition(final MintContext mintContext, final GlamAccountClient glamClient) {
    super(mintContext, glamClient);
    this.vaultATA = glamClient.findATA(mintContext.tokenProgram(), mintContext.mint()).publicKey();
  }

  @Override
  public void accountsForPriceInstruction(final Set<PublicKey> keys) {
    keys.add(mintContext.mint());
    keys.add(vaultATA);
  }

  @Override
  public Instruction priceInstruction(final GlamAccountClient glamAccountClient,
                                      final PublicKey solUSDOracleKey,
                                      final PublicKey baseAssetUSDOracleKey,
                                      final Map<PublicKey, AccountInfo<byte[]>> accountMap,
                                      final Set<PublicKey> returnAccounts) {
    return null;
  }

  public PublicKey vaultATA() {
    return vaultATA;
  }
}
