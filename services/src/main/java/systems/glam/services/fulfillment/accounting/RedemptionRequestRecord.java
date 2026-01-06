package systems.glam.services.fulfillment.accounting;

import software.sava.core.accounts.PublicKey;
import systems.glam.services.tokens.MintContext;

import java.math.BigDecimal;
import java.time.Instant;

record RedemptionRequestRecord(PublicKey user,
                               long createdAt,
                               BigDecimal shares) implements RedemptionRequest {

  @Override
  public int compareTo(final RedemptionRequest o) {
    return Long.compareUnsigned(createdAt, o.createdAt());
  }

  @Override
  public String toJson(final MintContext sharesMintContext) {
    return String.format("""
            
            {
             "user": "%s",
             "shares": "%s",
             "createdAt": "%s"
            }""",
        user,
        sharesMintContext.toDecimal(shares).toPlainString(),
        Instant.ofEpochSecond(createdAt)
    );
  }
}
