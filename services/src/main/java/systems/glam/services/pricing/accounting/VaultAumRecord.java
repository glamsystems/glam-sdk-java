package systems.glam.services.pricing.accounting;

import software.sava.core.accounts.PublicKey;
import software.sava.services.core.remote.call.Backoff;
import systems.glam.services.db.sql.BatchSqlExecutor;
import systems.glam.services.db.sql.SqlDataSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

public record VaultAumRecord(PublicKey stateKey,
                             Instant timestamp,
                             long slot,
                             long supply,
                             BigInteger baseAUM,
                             BigDecimal quoteAUM) {

  public static BatchSqlExecutor<VaultAumRecord> createSqlExecutor(final SqlDataSource datasource,
                                                                   final int batchSize,
                                                                   final Duration batchDelay,
                                                                   final Backoff backoff) {
    return BatchSqlExecutor.create(
        VaultAumRecord.class,
        datasource,
        "INSERT INTO vault_aum (state_key, timestamp, slot, supply, base_aum, usd_aum) VALUES (?,?,?,?,?,?) ON CONFLICT DO NOTHING",
        batchSize,
        (ps, record) -> record.prepare(ps),
        batchDelay,
        backoff
    );
  }

  public void prepare(final PreparedStatement stmt) throws SQLException {
    stmt.setBytes(1, stateKey.toByteArray());
    stmt.setTimestamp(2, Timestamp.from(timestamp));
    stmt.setLong(3, slot);
    stmt.setLong(4, supply);
    stmt.setLong(5, baseAUM.longValue());
    stmt.setBigDecimal(6, quoteAUM);
  }
}
