package systems.glam.services.db;

import systems.comodal.jsoniter.FieldBufferPredicate;
import systems.comodal.jsoniter.JsonIterator;

import static systems.comodal.jsoniter.JsonIterator.fieldEqualsIgnoreCase;

public record DataSourceHelperConfig(int writeBatchSize, String zTimestampFormat) {

  public static DataSourceHelperConfig parse(final JsonIterator ji) {
    final var parser = new Parser();
    ji.testObject(parser);
    return parser.createConfigRecord();
  }

  private static final class Parser implements FieldBufferPredicate {

    private int writeBatchSize;
    private String zTimestampFormat;

    private DataSourceHelperConfig createConfigRecord() {
      return new DataSourceHelperConfig(writeBatchSize, zTimestampFormat);
    }

    @Override
    public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
      if (fieldEqualsIgnoreCase("writeBatchSize", buf, offset, len)) {
        this.writeBatchSize = ji.readInt();
      } else if (fieldEqualsIgnoreCase("zTimestampFormat", buf, offset, len)) {
        this.zTimestampFormat = ji.readString();
      } else {
        ji.skip();
      }
      return true;
    }
  }
}
