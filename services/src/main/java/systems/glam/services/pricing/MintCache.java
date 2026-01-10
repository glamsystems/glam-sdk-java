package systems.glam.services.pricing;

import software.sava.core.accounts.PublicKey;
import systems.glam.services.tokens.MintContext;

public interface MintCache {

  MintContext get(final PublicKey mintPubkey);
}
