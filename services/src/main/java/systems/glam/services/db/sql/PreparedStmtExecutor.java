package systems.glam.services.db.sql;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public interface PreparedStmtExecutor<R> {

  R execute(final PreparedStatement preparedStatement) throws SQLException;
}
