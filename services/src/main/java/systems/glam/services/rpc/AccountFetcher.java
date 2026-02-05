package systems.glam.services.rpc;

import software.sava.core.accounts.PublicKey;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.services.solana.remote.call.RpcCaller;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface AccountFetcher extends Runnable {

  AccountInfo<byte[]> NULL_ACCOUNT_INFO = new AccountInfo<>(
      null, null, false, 0, PublicKey.NONE, null, 0, new byte[0]
  );

  static boolean isNull(final AccountInfo<byte[]> accountInfo) {
    return accountInfo == null || accountInfo == NULL_ACCOUNT_INFO;
  }

  static AccountFetcher createFetcher(final Duration fetchDelay,
                                      final boolean reactive,
                                      final RpcCaller rpcCaller,
                                      final Set<PublicKey> alwaysFetch) {
    return new AccountFetcherImpl(fetchDelay, reactive, rpcCaller, alwaysFetch);
  }

  void priorityQueueBatchable(final List<PublicKey> accounts, final AccountConsumer callback);

  void queueBatchable(final List<PublicKey> accounts, final AccountConsumer callback);

  void priorityQueue(final Collection<PublicKey> accounts, final AccountConsumer callback);

  default void priorityQueue(final PublicKey account, final AccountConsumer callback) {
    priorityQueue(List.of(account), callback);
  }

  CompletableFuture<AccountResult> priorityQueue(final Collection<PublicKey> accounts);

  void queue(final Collection<PublicKey> accounts, final AccountConsumer callback);

  default void queue(final PublicKey account, final AccountConsumer callback) {
    queue(List.of(account), callback);
  }

  CompletableFuture<AccountResult> queue(final Collection<PublicKey> accounts);
}
