package systems.glam.services.pricing.accounting;

import software.sava.core.accounts.PublicKey;

import java.util.Set;

public interface Position {

  void accountsNeeded(final Set<PublicKey> keys);
}
