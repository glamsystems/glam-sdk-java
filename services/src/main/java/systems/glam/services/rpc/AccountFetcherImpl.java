package systems.glam.services.rpc;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.encoding.ByteUtil;
import software.sava.rpc.json.http.client.SolanaRpcClient;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.services.solana.remote.call.RpcCaller;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.WARNING;

final class AccountFetcherImpl implements AccountFetcher {

  private static final System.Logger logger = System.getLogger(AccountFetcher.class.getName());

  private final Duration pollDelay;
  private final long pollDelayNanos;
  private final boolean reactive;
  private final RpcCaller rpcCaller;
  private final Set<PublicKey> alwaysFetch;
  private final LinkedHashSet<PublicKey> batch;
  /// Package-private so tests can assert the lock is released; a leaked lock
  /// blocks every other caller and no result assertion can see it.
  final ReentrantLock lock;
  private final Condition newBatch;
  private final Set<AccountConsumer> pendingUniqueConsumers;
  private final ConcurrentLinkedDeque<AccountBatch> queue;
  private final ConcurrentLinkedDeque<AccountBatch> currentBatch;
  private final Set<AccountConsumer> alwaysCall;

  private volatile Set<PublicKey> currentBatchKeys;
  private volatile StampedSlot recentSlot;

  AccountFetcherImpl(final Duration fetchDelay,
                     final boolean reactive,
                     final RpcCaller rpcCaller,
                     final Set<PublicKey> alwaysFetch) {
    // The polling path sleeps for this delay between passes; below a
    // millisecond that sleep rounds to nothing and the loop spins a core.
    // Reactive fetchers wait on a condition instead, so any delay works there.
    if (!reactive && fetchDelay.toMillis() < 1) {
      throw new IllegalArgumentException(
          "A polling account fetcher needs a fetch delay of at least one millisecond, not " + fetchDelay
      );
    }
    this.pollDelay = fetchDelay;
    this.pollDelayNanos = fetchDelay.toNanos();
    this.reactive = reactive;
    this.rpcCaller = rpcCaller;
    this.alwaysFetch = Set.copyOf(alwaysFetch);
    this.batch = new LinkedHashSet<>((int) Math.ceil((SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS << 1) / (double) 0.75f));
    this.batch.addAll(alwaysFetch);
    this.lock = new ReentrantLock();
    this.newBatch = lock.newCondition();
    this.pendingUniqueConsumers = ConcurrentHashMap.newKeySet(128);
    this.queue = new ConcurrentLinkedDeque<>();
    this.currentBatch = new ConcurrentLinkedDeque<>();
    this.currentBatchKeys = this.alwaysFetch;
    this.alwaysCall = ConcurrentHashMap.newKeySet(32);
  }

  @Override
  public StampedSlot recentSlot() {
    return recentSlot;
  }

  @Override
  public void listenToAll(final AccountConsumer accountConsumer) {
    this.alwaysCall.add(accountConsumer);
  }

  @Override
  public void stopListening(final AccountConsumer accountConsumer) {
    this.alwaysCall.remove(accountConsumer);
  }

  private void lockedQueue(final boolean priority, final AccountBatch accountBatch) {
    if (currentBatchKeys.containsAll(accountBatch.keys())) {
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
  }

  private void queueUnique(final boolean priority,
                           final Collection<PublicKey> accounts,
                           final AccountConsumer callback) {
    if (validBatch(accounts)) {
      lock.lock();
      try {
        if (pendingUniqueConsumers.add(callback)) {
          lockedQueue(priority, new UniqueAccountBatchRecord(accounts, callback));
        }
      } finally {
        lock.unlock();
      }
    }
  }

  private void queue(final boolean priority, final AccountBatch accountBatch) {
    lock.lock();
    try {
      lockedQueue(priority, accountBatch);
    } finally {
      lock.unlock();
    }
  }

  private static boolean validBatch(final Collection<PublicKey> accounts) {
    final int numAccounts = accounts.size();
    if (numAccounts == 0) {
      return false;
    } else if (numAccounts > SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS) {
      throw new IllegalStateException("Unable to fetch more than " + SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS + " accounts in a single request.");
    } else {
      return true;
    }
  }

  private void queue(final boolean priority, final Collection<PublicKey> accounts, final AccountConsumer callback) {
    if (validBatch(accounts)) {
      final var accountBatch = new AccountBatchRecord(accounts, callback);
      queue(priority, accountBatch);
    }
  }

  private void queueBatchable(final boolean priority, final List<PublicKey> accounts, final AccountConsumer callback) {
    final int numAccounts = accounts.size();
    if (numAccounts > SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS) {
      for (int from = 0, to = SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS; ; ) {
        final var batch = accounts.subList(from, to);
        queue(priority, batch, callback);
        if (to >= numAccounts) {
          return;
        }
        from = to;
        to = Math.min(to + SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS, numAccounts);
      }
    } else {
      queue(priority, accounts, callback);
    }
  }

  @Override
  public void priorityQueueBatchable(final List<PublicKey> accounts, final AccountConsumer callback) {
    queueBatchable(true, accounts, callback);
  }

  @Override
  public void queueBatchable(final List<PublicKey> accounts, final AccountConsumer callback) {
    queueBatchable(false, accounts, callback);
  }

  @Override
  public void priorityQueue(final Collection<PublicKey> accounts, final AccountConsumer callback) {
    queue(true, accounts, callback);
  }

  @Override
  public void priorityQueueUnique(final Collection<PublicKey> accounts, final AccountConsumer callback) {
    queueUnique(true, accounts, callback);
  }

  @Override
  public void queue(final Collection<PublicKey> accounts, final AccountConsumer callback) {
    queue(false, accounts, callback);
  }

  @Override
  public void queueUnique(final Collection<PublicKey> accounts, final AccountConsumer callback) {
    queueUnique(false, accounts, callback);
  }

  private static final CompletableFuture<AccountResult> EMPTY = CompletableFuture.completedFuture(new AccountResult(List.of(), Map.of()));

  private CompletableFuture<AccountResult> queue(final boolean priority, final Collection<PublicKey> accounts) {
    if (validBatch(accounts)) {
      final var future = new CompletableFuture<AccountResult>();
      final var accountBatch = new CompletableAccountBatch(accounts, future);
      queue(priority, accountBatch);
      return future;
    } else {
      return EMPTY;
    }
  }

  @Override
  public CompletableFuture<AccountResult> priorityQueue(final Collection<PublicKey> accounts) {
    return queue(true, accounts);
  }

  @Override
  public CompletableFuture<AccountResult> queue(final Collection<PublicKey> accounts) {
    return queue(false, accounts);
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
              try {
                accountBatch.mutableKeysExceededMaxSize();
              } catch (final RuntimeException ex) {
                logger.log(ERROR, "Account consumer failed handling an oversized batch; continuing to poll.", ex);
              }
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

  private void delay(final Duration pollDelay, final long pollDelayNanos) throws InterruptedException {
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
      do { // Amortize (pollDelay / 2) after an initial batch is added.
        Thread.sleep(pollDelay);
      } while (queue.isEmpty());
    }
  }

  @Override
  public void run() {
    try {
      if (queue.isEmpty()) {
        delay(pollDelay, pollDelayNanos);
      }
      for (; ; ) {
        final var keys = createBatch();

        final long requestedAt = System.currentTimeMillis();
        final var accounts = rpcCaller.courteousGet(
            rpcClient -> rpcClient.getAccounts(keys),
            "rpcClient#getAccountsBatch"
        );
        final long receivedAt = System.currentTimeMillis();

        final var accountsMap = toMap(keys, accounts);
        final var clockSysVar = accountsMap.get(SolanaAccounts.MAIN_NET.clockSysVar());
        if (clockSysVar == null) {
          for (final var accountInfo : accounts) {
            if (accountInfo != null) {
              final var context = accountInfo.context();
              if (context != null) {
                final long slot = context.slot();
                if (slot != 0) {
                  final long estimatedSlotTime = requestedAt + ((receivedAt - requestedAt) / 2);
                  this.recentSlot = new StampedSlot(slot, Instant.ofEpochMilli(estimatedSlotTime));
                }
              }
            }
          }
        } else {
          final long epochSeconds = ByteUtil.getInt64LE(clockSysVar.data(), 32);
          this.recentSlot = new StampedSlot(clockSysVar.context().slot(), Instant.ofEpochSecond(epochSeconds));
        }

        for (final var accountConsumer : alwaysCall) {
          dispatch(accountConsumer, accounts, accountsMap);
        }

        for (; ; ) {
          final var accountBatch = currentBatch.pollFirst();
          if (accountBatch == null) {
            lock.lock();
            try {
              if (currentBatch.isEmpty()) { // Reset Batch
                this.currentBatchKeys = this.alwaysFetch;
                break;
              }
            } finally {
              lock.unlock();
            }
          } else if (accountBatch instanceof UniqueAccountBatchRecord(_, final AccountConsumer accountConsumer)) {
            pendingUniqueConsumers.remove(accountConsumer);
            dispatch(accountConsumer, accounts, accountsMap);
          } else {
            dispatch(accountBatch, accounts, accountsMap);
          }
        }

        clearBatch();

        delay(pollDelay, pollDelayNanos);
      }
    } catch (final InterruptedException e) {
      // exit
    } catch (final RuntimeException ex) {
      logger.log(ERROR, "Unexpected error fetching accounts.", ex);
    }
  }


  /// Consumer callbacks run on the polling thread inside run()'s try, so an
  /// unguarded throw would exit the loop and silently stop account fetching
  /// for every service sharing this fetcher. A consumer's failure is its own:
  /// log it and keep polling.
  private static void dispatch(final AccountConsumer accountConsumer,
                               final List<AccountInfo<byte[]>> accounts,
                               final Map<PublicKey, AccountInfo<byte[]>> accountsMap) {
    try {
      accountConsumer.accept(accounts, accountsMap);
    } catch (final RuntimeException ex) {
      logger.log(ERROR, "Account consumer failed; continuing to poll.", ex);
    }
  }

  private static Map<PublicKey, AccountInfo<byte[]>> toMap(final List<PublicKey> keys,
                                                           final List<AccountInfo<byte[]>> accounts) {
    final var accountsMap = HashMap.<PublicKey, AccountInfo<byte[]>>newHashMap(accounts.size());
    int i = 0;
    for (final var accountInfo : accounts) {
      if (accountInfo != null) {
        accountsMap.put(accountInfo.pubKey(), accountInfo);
      } else {
        accountsMap.put(keys.get(i), AccountFetcher.NULL_ACCOUNT_INFO);
      }
      ++i;
    }
    return Collections.unmodifiableMap(accountsMap);
  }

  private interface AccountBatch extends AccountConsumer {

    Collection<PublicKey> keys();
  }

  private record UniqueAccountBatchRecord(Collection<PublicKey> keys,
                                          AccountConsumer accountConsumer) implements AccountBatch {

    @Override
    public void accept(final List<AccountInfo<byte[]>> accounts, final Map<PublicKey, AccountInfo<byte[]>> accountMap) {
      accountConsumer.accept(accounts, accountMap);
    }

    @Override
    public void mutableKeysExceededMaxSize() {
      accountConsumer.mutableKeysExceededMaxSize();
    }
  }

  private record AccountBatchRecord(Collection<PublicKey> keys,
                                    AccountConsumer accountConsumer) implements AccountBatch {

    @Override
    public void accept(final List<AccountInfo<byte[]>> accounts, final Map<PublicKey, AccountInfo<byte[]>> accountMap) {
      accountConsumer.accept(accounts, accountMap);
    }

    @Override
    public void mutableKeysExceededMaxSize() {
      accountConsumer.mutableKeysExceededMaxSize();
    }
  }

  private record CompletableAccountBatch(Collection<PublicKey> keys,
                                         CompletableFuture<AccountResult> future) implements AccountBatch {

    @Override
    public void accept(final List<AccountInfo<byte[]>> accounts, final Map<PublicKey, AccountInfo<byte[]>> accountMap) {
      final var result = new AccountResult(accounts, accountMap);
      future.complete(result);
    }

    @Override
    public void mutableKeysExceededMaxSize() {
      future.completeExceptionally(new IllegalStateException("Mutable Keys Exceeded Max Size"));
    }
  }
}
