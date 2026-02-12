package systems.glam.services.db;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

public interface DataSourceHelper {

  static DataSourceHelperRecord.Builder build(final DataSourceHelperConfig helperConfig) {
    return new DataSourceHelperRecord.Builder(helperConfig);
  }

  static DataSourceHelperRecord.Builder build() {
    return new DataSourceHelperRecord.Builder();
  }

  int writeBatchSize();

  DateTimeFormatter zDateTimeFormatter();

  Instant parseDatabaseTimestampZ(final String timestamp);
}
