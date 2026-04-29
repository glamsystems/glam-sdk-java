package systems.glam.services.db.sql;

import java.sql.PreparedStatement;
import java.sql.SQLException;

@FunctionalInterface
public interface StatementPreparer<T> {
  
  int prepare(final PreparedStatement ps, final T item) throws SQLException;
}
