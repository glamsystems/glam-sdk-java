package systems.glam.services.pricing.accounting;

import software.sava.core.accounts.PublicKey;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public record PositionReportLeaf(PublicKey mint,
                                 BigDecimal amount,
                                 BigDecimal value) implements PositionReport {

  static final Map<PublicKey, PositionReportLeaf> ZERO_POSITIONS = HashMap.newHashMap(256);

}
