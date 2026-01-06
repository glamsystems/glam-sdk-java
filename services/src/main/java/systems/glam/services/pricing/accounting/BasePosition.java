package systems.glam.services.pricing.accounting;

import software.sava.core.accounts.PublicKey;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.services.tokens.MintContext;

import java.util.List;

abstract class BasePosition implements Position {

  protected final MintContext mintContext;
  protected final GlamAccountClient glamClient;
  protected final List<PublicKey> lookupTableKeys;

  BasePosition(final MintContext mintContext,
               final GlamAccountClient glamClient,
               final List<PublicKey> lookupTableKeys) {
    this.mintContext = mintContext;
    this.glamClient = glamClient;
    this.lookupTableKeys = lookupTableKeys;
  }
}
