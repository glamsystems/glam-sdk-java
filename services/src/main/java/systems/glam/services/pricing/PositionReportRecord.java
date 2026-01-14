package systems.glam.services.pricing;

import java.math.BigDecimal;

public record PositionReportRecord(BigDecimal totalValue) implements PositionReport {
}
