package systems.glam.services.pricing.accounting;

import software.sava.core.accounts.PublicKey;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;

public interface VaultAum {

  VaultAum RETRY = new VaultAumSentinel();

  PublicKey stateKey();

  Instant timestamp();

  long slot();

  long supply();

  BigInteger baseAUM();

  BigDecimal quoteAUM();
}
