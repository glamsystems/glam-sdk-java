package systems.glam.services.pricing.accounting;

import software.sava.core.accounts.PublicKey;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.services.tokens.MintContext;

import java.util.Set;

public final class TokenPosition extends BasePosition {

  private final PublicKey vaultATA;

  public TokenPosition(final MintContext mintContext, final GlamAccountClient glamClient) {
    super(mintContext, glamClient);
    this.vaultATA = glamClient.findATA(mintContext.tokenProgram(), mintContext.mint()).publicKey();
  }

  @Override
  public void accountsNeeded(final Set<PublicKey> keys) {
    keys.add(mintContext.mint());
    keys.add(vaultATA);
  }

  public PublicKey vaultATA() {
    return vaultATA;
  }
}
