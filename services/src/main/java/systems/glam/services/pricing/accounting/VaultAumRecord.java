package systems.glam.services.pricing.accounting;

import software.sava.core.accounts.PublicKey;
import systems.glam.services.db.sql.SqlDataSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;

public record VaultAumRecord(PublicKey stateKey,
                             Instant timestamp,
                             long slot,
                             long supply,
                             BigInteger baseAUM,
                             BigDecimal quoteAUM) {

  private static final String INSERT_AUM = """
      INSERT INTO vault_aum (state_key, timestamp, slot, supply, base_aum, usd_aum) VALUES (?,?,?,?,?,?) ON CONFLICT DO NOTHING""";

  public Integer insertRecord(final SqlDataSource dataSource) throws InterruptedException {
    return dataSource.executePreparedStatement(INSERT_AUM, stmt -> {
          stmt.setBytes(1, stateKey.toByteArray());
          stmt.setTimestamp(2, Timestamp.from(timestamp));
          stmt.setLong(3, slot);
          stmt.setLong(4, supply);
          stmt.setLong(5, baseAUM.longValue());
          stmt.setBigDecimal(6, quoteAUM);
          return stmt.executeUpdate();
        }
    );
  }
}
