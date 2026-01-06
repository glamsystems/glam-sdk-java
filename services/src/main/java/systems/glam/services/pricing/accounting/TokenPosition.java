package systems.glam.services.pricing.accounting;

import software.sava.core.accounts.PublicKey;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.services.tokens.MintContext;

import java.util.List;
import java.util.Set;

public class TokenPosition extends BasePosition {

  TokenPosition(final MintContext mintContext,
                final GlamAccountClient glamClient,
                final List<PublicKey> lookupTableKeys) {
    super(mintContext, glamClient, lookupTableKeys);
  }

  @Override
  public void accountsNeeded(final Set<PublicKey> keys) {
    keys.add(mintContext.mint());
    keys.add(mintContext.vaultATA());
  }
}
