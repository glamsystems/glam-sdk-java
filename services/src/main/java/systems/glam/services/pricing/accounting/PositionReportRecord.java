package systems.glam.services.pricing.accounting;

import java.math.BigDecimal;

public record PositionReportRecord(BigDecimal totalValue) implements PositionReport {
}
