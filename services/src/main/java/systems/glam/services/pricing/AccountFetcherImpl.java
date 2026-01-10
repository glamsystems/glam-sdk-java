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

final class AccountFetcherImpl implements AccountFetcher {

  private static final System.Logger logger = System.getLogger(AccountFetcher.class.getName());

  private final long initialDelayMillis;
  private final long initialDelayNanos;
  private final long pollDelayMillis;
  private final long pollDelayNanos;
  private final boolean reactive;
  private final RpcCaller rpcCaller;
  private final Set<PublicKey> alwaysFetch;
  private final ReentrantLock lock;
  private final Condition newBatch;
  private final ConcurrentLinkedDeque<AccountBatch> queue;
  private final ConcurrentLinkedDeque<AccountBatch> currentBatch;

  private volatile Set<PublicKey> currentBatchKeys;

  AccountFetcherImpl(final Duration initialDelay,
                     final Duration fetchDelay,
                     final boolean reactive,
                     final RpcCaller rpcCaller,
                     final Set<PublicKey> alwaysFetch) {
    this.initialDelayMillis = initialDelay.toMillis();
    this.initialDelayNanos = initialDelay.toNanos();
    this.pollDelayMillis = fetchDelay.toMillis();
    this.pollDelayNanos = fetchDelay.toNanos();
    this.reactive = reactive;
    this.rpcCaller = rpcCaller;
    this.alwaysFetch = Set.copyOf(alwaysFetch);
    this.lock = new ReentrantLock();
    this.newBatch = lock.newCondition();
    this.queue = new ConcurrentLinkedDeque<>();
    this.currentBatch = new ConcurrentLinkedDeque<>();
    this.currentBatchKeys = Set.of();
  }

  private void queue(final boolean priority, final Collection<PublicKey> accounts, final AccountConsumer callback) {
    final int numAccounts = accounts.size();
    if (numAccounts > SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS) {
      throw new IllegalStateException("Unable to fetch more than " + SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS + " accounts in a single request.");
    } else if ((numAccounts + alwaysFetch.size()) > SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS) {
      if (!accounts.containsAll(alwaysFetch)) {
        throw new IllegalStateException("Batch cannot execute because after included the always fetch accounts the rpc limit of " + SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS + " accounts would be exceeded.");
      }
    }

    final var accountBatch = new AccountBatch(accounts, callback);
    lock.lock();
    try {
      final var currentBatchKeys = this.currentBatchKeys;
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

  private List<PublicKey> createBatchKeys(final LinkedHashSet<PublicKey> batch, final int size) {
    final var batchKeys = new PublicKey[size];
    final var iterator = batch.iterator();
    for (int i = 0; i < size; ++i) {
      batchKeys[i] = iterator.next();
    }
    this.currentBatchKeys = Set.of(batchKeys);
    return Arrays.asList(batchKeys);
  }

  private List<PublicKey> createBatchKeys(final LinkedHashSet<PublicKey> batch) {
    lock.lock();
    try {
      int size = 0;
      for (int nextSize; ; ) {
        final var accountBatch = queue.peekFirst();
        if (accountBatch == null) {
          break;
        }
        batch.addAll(accountBatch.keys());
        nextSize = batch.size();
        if (nextSize > SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS) {
          // TODO: Check if any other batch would fit.
          break;
        }
        queue.removeFirst();
        currentBatch.addLast(accountBatch);
        size = nextSize;
        if (size == SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS) {
          break;
        }
      }

      return createBatchKeys(batch, size);
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
      do {
        //noinspection BusyWait
        Thread.sleep(pollDelayMillis);
      } while (queue.isEmpty());
    }
  }

  @Override
  public void run() {
    try {
      final var batch = new LinkedHashSet<PublicKey>((int) Math.ceil((SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS << 1) / (double) 0.75f));
      if (initialDelayMillis > 0) {
        delay(initialDelayMillis, initialDelayNanos);
      }
      for (; ; ) {
        final var keys = createBatchKeys(batch);

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

        batch.clear();
        batch.addAll(this.alwaysFetch);

        delay(pollDelayMillis, pollDelayNanos);
      }
    } catch (final InterruptedException e) {
      // exit
    } catch (final RuntimeException ex) {
      logger.log(System.Logger.Level.ERROR, "Unexpected error fetching accounts.", ex);
    }
  }

  record AccountBatch(Collection<PublicKey> keys, AccountConsumer accountConsumer) implements AccountConsumer {

    @Override
    public void accept(final List<AccountInfo<byte[]>> accountsList,
                       final Map<PublicKey, AccountInfo<byte[]>> accountMap) {
      accountConsumer.accept(accountsList, accountMap);
    }
  }
}
