package systems.glam.services.execution;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.sysvar.Clock;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.services.ServiceContext;

import java.nio.file.Path;
import java.util.Map;

public class BaseServiceContext {

  protected final ServiceContext serviceContext;

  protected BaseServiceContext(final ServiceContext serviceContext) {
    this.serviceContext = serviceContext;
  }

  public final long minCheckStateDelayNanos() {
    return serviceContext.minCheckStateDelayNanos();
  }

  public final long maxCheckStateDelayNanos() {
    return serviceContext.maxCheckStateDelayNanos();
  }

  public final Clock clock(final Map<PublicKey, AccountInfo<byte[]>> accountsNeededMap) {
    return serviceContext.clock(accountsNeededMap);
  }

  public final RpcCaller rpcCaller() {
    return serviceContext.rpcCaller();
  }

  public final void backoff(final long failureCount) throws InterruptedException {
    serviceContext.backoff(failureCount);
  }

  public final boolean isTokenMint(final AccountInfo<byte[]> accountInfo) {
    return serviceContext.isTokenMint(accountInfo);
  }

  public final boolean isTokenAccount(final AccountInfo<byte[]> accountInfo) {
    return serviceContext.isTokenAccount(accountInfo);
  }

  public final Path resolveGlamStateFilePath(final PublicKey glamStateKey) {
    return serviceContext.resolveGlamStateFilePath(glamStateKey);
  }

  public final void executeTask(final Runnable task) {
    serviceContext.executeTask(task);
  }
}
