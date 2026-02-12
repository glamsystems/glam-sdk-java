package systems.glam.services.db.sql;

public interface StmtExecutor<R> {

  R execute() throws InterruptedException;
}
