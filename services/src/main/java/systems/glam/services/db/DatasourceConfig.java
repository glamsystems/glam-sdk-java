package systems.glam.services.db;

import systems.comodal.jsoniter.FieldBufferPredicate;
import systems.comodal.jsoniter.JsonIterator;

import java.util.Locale;

import static systems.comodal.jsoniter.JsonIterator.fieldEquals;
import static systems.comodal.jsoniter.JsonIterator.fieldEqualsIgnoreCase;

public record DatasourceConfig(DatasourceType type,
                               DatasourceFormat format,
                               String uri,
                               int port,
                               String user,
                               String password,
                               String passwordFilePath,
                               String databaseName,
                               String sslMode,
                               String sslCertFilePath,
                               int connectTimeout,
                               DataSourceHelperRecord.Builder dataSourceHelperBuilder) {

  public static DatasourceConfig parseConfig(final JsonIterator ji) {
    final var parser = new Parser();
    ji.testObject(parser);
    return parser.createConfigRecord();
  }

  private static final class Parser implements FieldBufferPredicate {

    private DatasourceType type;
    private DatasourceFormat format;
    private String uri;
    private int port;
    private String user;
    private String password;
    private String passwordFilePath;
    private String databaseName;
    private String sslMode;
    private String sslCertFilePath;
    private int connectTimeout;
    private DataSourceHelperConfig dataSourceHelperConfig;

    private Parser() {
      this.connectTimeout = 5;
    }

    private DatasourceConfig createConfigRecord() {
      return new DatasourceConfig(
          type, format,
          uri, port, user, password, passwordFilePath,
          databaseName,
          sslMode, sslCertFilePath,
          connectTimeout,
          DataSourceHelper.build(dataSourceHelperConfig)
      );
    }

    @Override
    public boolean test(final char[] buf, final int offset, final int len, final JsonIterator ji) {
      if (fieldEqualsIgnoreCase("type", buf, offset, len)) {
        this.type = DatasourceType.valueOf(ji.readString().strip().toUpperCase(Locale.ENGLISH));
      } else if (fieldEqualsIgnoreCase("format", buf, offset, len)) {
        this.format = DatasourceFormat.valueOf(ji.readString().strip().toUpperCase(Locale.ENGLISH));
      } else if (fieldEqualsIgnoreCase("uri", buf, offset, len)) {
        this.uri = ji.readString();
      } else if (fieldEqualsIgnoreCase("port", buf, offset, len)) {
        this.port = ji.readInt();
      } else if (fieldEqualsIgnoreCase("user", buf, offset, len)) {
        this.user = ji.readString();
      } else if (fieldEqualsIgnoreCase("password", buf, offset, len)) {
        this.password = ji.readString();
      } else if (fieldEqualsIgnoreCase("passwordFilePath", buf, offset, len)) {
        this.passwordFilePath = ji.readString();
      } else if (fieldEqualsIgnoreCase("database", buf, offset, len)) {
        this.databaseName = ji.readString();
      } else if (fieldEqualsIgnoreCase("sslMode", buf, offset, len)) {
        this.sslMode = ji.readString();
      } else if (fieldEqualsIgnoreCase("sslCertFilePath", buf, offset, len)) {
        this.sslCertFilePath = ji.readString();
      } else if (fieldEqualsIgnoreCase("connectTimeout", buf, offset, len)) {
        this.connectTimeout = ji.readInt();
      } else if (fieldEqualsIgnoreCase("helper", buf, offset, len)) {
        this.dataSourceHelperConfig = DataSourceHelperConfig.parse(ji);
      } else {
        ji.skip();
      }
      return true;
    }
  }
}
