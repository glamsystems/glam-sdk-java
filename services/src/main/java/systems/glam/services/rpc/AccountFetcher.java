package systems.glam.services.rpc;

import software.sava.core.accounts.PublicKey;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.services.LoopHeartbeat;

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
    return accountInfo == null || accountInfo == NULL_ACCOUNT_INFO || accountInfo.data().length == 0;
  }

  static AccountFetcher createFetcher(final Duration fetchDelay,
                                      final boolean reactive,
                                      final RpcCaller rpcCaller,
                                      final Set<PublicKey> alwaysFetch) {
    return createFetcher(fetchDelay, reactive, rpcCaller, alwaysFetch, LoopHeartbeat.NONE);
  }

  /// `heartbeat` ticks once per cycle of [#run]. A polling fetcher ticks after every
  /// `fetchDelay` sleep -- once per fetched batch while work flows, once per delay while
  /// idle -- so a fetcher stuck inside an RPC call goes quiet while an idle one stays alive.
  /// A reactive fetcher ticks once its minimum delay has elapsed and then, while nothing is
  /// queued, once per idle window: `fetchDelay` floored at
  /// `AccountFetcherImpl.IDLE_TICK_FLOOR_NANOS`, so a zero delay does not spin the park.
  static AccountFetcher createFetcher(final Duration fetchDelay,
                                      final boolean reactive,
                                      final RpcCaller rpcCaller,
                                      final Set<PublicKey> alwaysFetch,
                                      final LoopHeartbeat heartbeat) {
    return new AccountFetcherImpl(fetchDelay, reactive, rpcCaller, alwaysFetch, heartbeat);
  }

  StampedSlot recentSlot();

  void listenToAll(final AccountConsumer accountConsumer);

  void stopListening(final AccountConsumer accountConsumer);

  void priorityQueueBatchable(final List<PublicKey> accounts, final AccountConsumer callback);

  void queueBatchable(final List<PublicKey> accounts, final AccountConsumer callback);

  void priorityQueue(final Collection<PublicKey> accounts, final AccountConsumer callback);

  default void priorityQueue(final PublicKey account, final AccountConsumer callback) {
    priorityQueue(List.of(account), callback);
  }

  void queueUnique(final Collection<PublicKey> accounts, final AccountConsumer callback);

  CompletableFuture<AccountResult> priorityQueue(final Collection<PublicKey> accounts);

  void priorityQueueUnique(final Collection<PublicKey> accounts, final AccountConsumer callback);

  void queue(final Collection<PublicKey> accounts, final AccountConsumer callback);

  default void queue(final PublicKey account, final AccountConsumer callback) {
    queue(List.of(account), callback);
  }

  CompletableFuture<AccountResult> queue(final Collection<PublicKey> accounts);
}
