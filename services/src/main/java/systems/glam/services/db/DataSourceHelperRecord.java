package systems.glam.services.db;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

public record DataSourceHelperRecord(int writeBatchSize,
                                     DateTimeFormatter zDateTimeFormatter) implements DataSourceHelper {

  public Instant parseDatabaseTimestampZ(final String timestamp) {
    return zDateTimeFormatter.parse(timestamp, Instant::from);
  }

  public static final class Builder {

    private final DataSourceHelperConfig helperConfig;
    private int writeBatchSize;
    private DateTimeFormatter zDateTimeFormatter;

    Builder(final DataSourceHelperConfig helperConfig) {
      this.helperConfig = helperConfig;
      if (helperConfig != null) {
        this.writeBatchSize = helperConfig.writeBatchSize();
      }
    }

    Builder() {
      this.helperConfig = null;
    }

    public DataSourceHelper createHelper() {
      return new DataSourceHelperRecord(writeBatchSize, zDateTimeFormatter);
    }

    public void defaultWriteBatchSize(final int defaultWriteBatchSize) {
      if (this.writeBatchSize <= 0) {
        this.writeBatchSize = defaultWriteBatchSize;
      }
    }

    public void defaultZDateTimeFormatter(final DateTimeFormatter defaultZDateTimeFormatter) {
      if (helperConfig == null) {
        this.zDateTimeFormatter = defaultZDateTimeFormatter;
      } else {
        final var format = helperConfig.zTimestampFormat();
        this.zDateTimeFormatter = format == null || format.isBlank() ? defaultZDateTimeFormatter : DateTimeFormatter.ofPattern(format);
      }
    }
  }
}
