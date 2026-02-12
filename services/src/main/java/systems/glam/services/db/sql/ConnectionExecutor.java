package systems.glam.services.db.sql;

import java.sql.Connection;
import java.sql.SQLException;

public interface ConnectionExecutor<R> {

  R execute(final Connection connection) throws SQLException;
}
