package systems.glam.services.pricing.accounting;

import software.sava.core.accounts.PublicKey;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;

final class VaultAumSentinel implements VaultAum {

  @Override
  public PublicKey stateKey() {
    return null;
  }

  @Override
  public Instant timestamp() {
    return null;
  }

  @Override
  public long slot() {
    return 0;
  }

  @Override
  public long supply() {
    return 0;
  }

  @Override
  public BigInteger baseAUM() {
    return null;
  }

  @Override
  public BigDecimal quoteAUM() {
    return null;
  }
}
