package systems.glam.services.pricing;

import software.sava.core.accounts.PublicKey;
import software.sava.services.solana.remote.call.RpcCaller;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface AccountFetcher extends Runnable {

  static AccountFetcher createFetcher(final Duration initialDelay,
                                      final Duration fetchDelay,
                                      final boolean reactive,
                                      final RpcCaller rpcCaller,
                                      final Set<PublicKey> alwaysFetch) {
    return new AccountFetcherImpl(initialDelay, fetchDelay, reactive, rpcCaller, alwaysFetch);
  }

  void priorityQueue(final Collection<PublicKey> account, final AccountConsumer callback);

  default void priorityQueue(final PublicKey account, final AccountConsumer callback) {
    priorityQueue(List.of(account), callback);
  }

  void queue(final Collection<PublicKey> accounts, final AccountConsumer callback);

  default void queue(final PublicKey account, final AccountConsumer callback) {
    queue(List.of(account), callback);
  }
}
