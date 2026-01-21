package systems.glam.services.pricing.accounting;

import systems.glam.services.mints.MintContext;

public abstract class BasePosition implements Position {

  protected final MintContext mintContext;

  protected BasePosition(final MintContext mintContext) {
    this.mintContext = mintContext;
  }
}
