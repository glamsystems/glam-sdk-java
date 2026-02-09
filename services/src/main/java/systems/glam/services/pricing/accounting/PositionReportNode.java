package systems.glam.services.pricing.accounting;

import java.math.BigDecimal;
import java.util.Collection;

public record PositionReportNode(BigDecimal value,
                                 Collection<? extends AggregatePositionReport> subPositions) implements
    AggregatePositionReport {
}
