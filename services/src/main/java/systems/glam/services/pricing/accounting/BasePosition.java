package systems.glam.services.pricing.accounting;

import systems.glam.sdk.GlamAccountClient;
import systems.glam.services.tokens.MintContext;

public abstract class BasePosition implements Position {

  protected final MintContext mintContext;
  protected final GlamAccountClient glamClient;

  protected BasePosition(final MintContext mintContext, final GlamAccountClient glamClient) {
    this.mintContext = mintContext;
    this.glamClient = glamClient;
  }
}
