package systems.glam.services.pricing;

import software.sava.core.accounts.PublicKey;
import software.sava.rpc.json.http.client.SolanaRpcClient;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.services.solana.remote.call.RpcCaller;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.WARNING;

final class AccountFetcherImpl implements AccountFetcher {

  private static final System.Logger logger = System.getLogger(AccountFetcher.class.getName());

  private final long pollDelayMillis;
  private final long pollDelayNanos;
  private final boolean reactive;
  private final RpcCaller rpcCaller;
  private final Set<PublicKey> alwaysFetch;
  private final LinkedHashSet<PublicKey> batch;
  private final ReentrantLock lock;
  private final Condition newBatch;
  private final ConcurrentLinkedDeque<AccountBatch> queue;
  private final ConcurrentLinkedDeque<AccountBatch> currentBatch;

  private volatile Set<PublicKey> currentBatchKeys;

  AccountFetcherImpl(final Duration fetchDelay,
                     final boolean reactive,
                     final RpcCaller rpcCaller,
                     final Set<PublicKey> alwaysFetch) {
    this.pollDelayMillis = fetchDelay.toMillis();
    this.pollDelayNanos = fetchDelay.toNanos();
    this.reactive = reactive;
    this.rpcCaller = rpcCaller;
    this.alwaysFetch = Set.copyOf(alwaysFetch);
    this.batch = new LinkedHashSet<>((int) Math.ceil((SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS << 1) / (double) 0.75f));
    this.batch.addAll(alwaysFetch);
    this.lock = new ReentrantLock();
    this.newBatch = lock.newCondition();
    this.queue = new ConcurrentLinkedDeque<>();
    this.currentBatch = new ConcurrentLinkedDeque<>();
    this.currentBatchKeys = this.alwaysFetch;
  }

  private void queue(final boolean priority, final Collection<PublicKey> accounts, final AccountConsumer callback) {
    final int numAccounts = accounts.size();
    if (numAccounts > SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS) {
      throw new IllegalStateException("Unable to fetch more than " + SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS + " accounts in a single request.");
    }

    final var accountBatch = new AccountBatch(accounts, callback);
    lock.lock();
    try {
      if (currentBatchKeys.containsAll(accounts)) {
        currentBatch.addLast(accountBatch);
      } else {
        if (priority) {
          queue.addFirst(accountBatch);
        } else {
          queue.addLast(accountBatch);
        }
        if (reactive) {
          newBatch.signal();
        }
      }
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void priorityQueue(final Collection<PublicKey> accounts, final AccountConsumer callback) {
    queue(true, accounts, callback);
  }

  @Override
  public void queue(final Collection<PublicKey> accounts, final AccountConsumer callback) {
    queue(false, accounts, callback);
  }

  private List<PublicKey> createBatchKeys(final int size) {
    final var batchKeys = new PublicKey[size];
    final var iterator = batch.iterator();
    for (int i = 0; i < size; ++i) {
      batchKeys[i] = iterator.next();
    }
    this.currentBatchKeys = Set.of(batchKeys);
    return Arrays.asList(batchKeys);
  }

  private void removeTrailing(final int count) {
    for (int i = count; i > 0; --i) {
      batch.removeLast();
    }
  }

  private void clearBatch() {
    removeTrailing(batch.size() - alwaysFetch.size());
  }

  private List<PublicKey> createBatch() {
    lock.lock();
    try {
      int size = batch.size();
      AccountBatch accountBatch;
      final var iterator = queue.iterator();
      for (int numCallbacks = 0, nextSize; ; ) {
        accountBatch = iterator.next();
        final var keys = accountBatch.keys();
        batch.addAll(keys);
        nextSize = batch.size();
        if (nextSize > SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS) {
          if (numCallbacks == 0) {
            final int numAccounts = keys.size();
            if (numAccounts > SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS) {
              // Should never happen because an exception is thrown on any attempt to add a batch that exceeds this limit.
              logger.log(WARNING, "Ignoring batch because it exceeds the RPC limit of " + SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS);
              iterator.remove();
              clearBatch();
              continue;
            } else {
              batch.clear();
              batch.addAll(accountBatch.keys());
              if (numAccounts < SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS) {
                // Add as many always fetch accounts as possible
                final var alwaysFetchIterator = alwaysFetch.iterator();
                for (int add = SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS - numAccounts; add > 0; ) {
                  if (batch.add(alwaysFetchIterator.next())) {
                    --add;
                  }
                }
              }
              size = SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS;
              break;
            }
          }
          if (iterator.hasNext()) {
            removeTrailing(nextSize - size);
          } else {
            break;
          }
        } else {
          size = nextSize;
          iterator.remove();
          currentBatch.addLast(accountBatch);
          ++numCallbacks;
          if (!iterator.hasNext()) {
            break;
          } else if (size == SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS) {
            do { // Check for 100% overlap with the existing batch.
              accountBatch = iterator.next();
              if (batch.containsAll(accountBatch.keys())) {
                iterator.remove();
                currentBatch.addLast(accountBatch);
              }
            } while (iterator.hasNext());
            break;
          }
        }
      }
      return createBatchKeys(size);
    } finally {
      lock.unlock();
    }
  }

  private void delay(final long pollDelayMillis, final long pollDelayNanos) throws InterruptedException {
    if (reactive) {
      // Break out on the first batch received after the minimum delay has been met.
      lock.lock();
      try {
        for (long remainingAwaitNanos = pollDelayNanos; ; ) {
          remainingAwaitNanos = newBatch.awaitNanos(remainingAwaitNanos);
          if (remainingAwaitNanos <= 0) {
            break;
          }
        }
        while (queue.isEmpty()) {
          newBatch.await();
        }
      } finally {
        lock.unlock();
      }
    } else {
      do { // Amortize (pollDelayMillis / 2) after an initial batch is added.
        //noinspection BusyWait
        Thread.sleep(pollDelayMillis);
      } while (queue.isEmpty());
    }
  }

  @Override
  public void run() {
    try {
      if (queue.isEmpty()) {
        delay(pollDelayMillis, pollDelayNanos);
      }
      for (; ; ) {
        final var keys = createBatch();

        final var accounts = rpcCaller.courteousGet(
            rpcClient -> rpcClient.getAccounts(keys),
            "rpcClient#getAccountsBatch"
        );

        final var accountsMap = HashMap.<PublicKey, AccountInfo<byte[]>>newHashMap(accounts.size());
        for (final var accountInfo : accounts) {
          if (accountInfo != null) {
            accountsMap.put(accountInfo.pubKey(), accountInfo);
          }
        }

        lock.lock();
        try {
          this.currentBatchKeys = this.alwaysFetch;
        } finally {
          lock.unlock();
        }

        for (; ; ) {
          final var accountBatch = currentBatch.pollFirst();
          if (accountBatch == null) {
            break;
          }
          accountBatch.accept(accounts, accountsMap);
        }

        clearBatch();

        delay(pollDelayMillis, pollDelayNanos);
      }
    } catch (final InterruptedException e) {
      // exit
    } catch (final RuntimeException ex) {
      logger.log(ERROR, "Unexpected error fetching accounts.", ex);
    }
  }

  record AccountBatch(Collection<PublicKey> keys, AccountConsumer accountConsumer) implements AccountConsumer {

    @Override
    public void accept(final List<AccountInfo<byte[]>> accounts, final Map<PublicKey, AccountInfo<byte[]>> accountMap) {
      accountConsumer.accept(accounts, accountMap);
    }
  }
}
