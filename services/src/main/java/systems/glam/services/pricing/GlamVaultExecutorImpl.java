package systems.glam.services.pricing;

import software.sava.rpc.json.http.client.SolanaRpcClient;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.services.pricing.accounting.VaultAum;
import systems.glam.services.rpc.AccountFetcher;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.INFO;

final class GlamVaultExecutorImpl implements GlamVaultExecutor {

  private static final System.Logger logger = System.getLogger(GlamVaultExecutor.class.getName());

  private static final Comparator<VaultStateContext> BY_USD_VALUE = (a, b) -> Long.compareUnsigned(a.usdValue(), b.usdValue());

  private final RpcCaller rpcCaller;
  private final GlamStateContextCache stateCache;
  private final TimeUnit scheduleTimeUnit;
  private final int maxGroupRetries;

  GlamVaultExecutorImpl(final RpcCaller rpcCaller,
                        final GlamStateContextCache stateCache,
                        final TimeUnit scheduleTimeUnit,
                        final int maxGroupRetries) {
    this.rpcCaller = rpcCaller;
    this.stateCache = stateCache;
    this.scheduleTimeUnit = scheduleTimeUnit;
    this.maxGroupRetries = maxGroupRetries;
  }

  private static void waitUntil(final TimeUnit timeUnit, final int val) throws InterruptedException {
    final var now = LocalTime.now();
    final int nowUnit = switch (timeUnit) {
      case HOURS -> now.getHour();
      case MINUTES -> now.getMinute();
      case SECONDS -> now.getSecond();
      default -> throw new IllegalStateException("Unexpected value: " + timeUnit);
    };
    if (nowUnit < val) {
      timeUnit.sleep(val - nowUnit);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public void run() {
    try {
      var results = (CompletableFuture<VaultAum>[]) new CompletableFuture[stateCache.size() << 1];
      var stateChanges = new BitSet(results.length);
      for (; ; stateChanges.clear()) {
        waitUntil(scheduleTimeUnit, 30);

        final var stateContextArray = refreshStateContext();
        if (stateContextArray.length > results.length) {
          results = (CompletableFuture<VaultAum>[]) new CompletableFuture[stateContextArray.length << 1];
          stateChanges = new BitSet(results.length);
        }

        waitUntil(scheduleTimeUnit, 60);
        for (int groupRetries = 0; ; ) {
          int numVaults = 0;
          for (int i = stateContextArray.length - 1; i >= 0; i--) {
            final var stateContext = stateContextArray[i];
            if (stateContext != null) {
              results[i] = stateContext.priceVault();
              ++numVaults;
            }
          }

          int numStaleOracles = 0;
          for (int i = stateContextArray.length - 1; i >= 0; i--) {
            final var resultFuture = results[i];
            results[i] = null;
            if (resultFuture != null) {
              final var vaultAUM = resultFuture.join();
              if (!vaultAUM.isRetryable()) {
                stateContextArray[i] = null;
              } else if (vaultAUM == VaultAum.RETRY_STALE_ORACLE) {
                ++numStaleOracles;
              } else if (vaultAUM == VaultAum.RETRY_STATE_CHANGE) {
                stateChanges.set(i);
              }
            } else {
              stateContextArray[i] = null;
            }
          }

          final int numStateChanges = stateChanges.cardinality();
          if (numStateChanges > 0) { // Fast retry on state changes.
            final var stateChangeContextArray = stateChanges.stream().mapToObj(i -> stateContextArray[i]).toArray(VaultStateContext[]::new);
            final var stateChangeResults = (CompletableFuture<VaultAum>[]) new CompletableFuture[numStateChanges];
            refreshStateContext(stateChangeContextArray);
            for (int i = stateChanges.nextSetBit(0), j = 0; ; ++j) {
              final var stateContext = stateChangeContextArray[j];
              if (stateContext != null) {
                stateChangeResults[j] = stateContext.priceVault();
                ++numVaults;
              } else {
                stateContextArray[i] = null;
              }
              i = stateChanges.nextSetBit(i + 1);
              if (i < 0) {
                break;
              }
            }
            for (int i = stateChanges.nextSetBit(0), j = 0; ; ++j) {
              final var resultFuture = stateChangeResults[j];
              if (resultFuture != null) {
                final var vaultAUM = resultFuture.join();
                if (vaultAUM != VaultAum.RETRY_STATE_CHANGE) {
                  stateChanges.clear(i);
                  if (!vaultAUM.isRetryable()) {
                    stateContextArray[i] = null;
                  } else if (vaultAUM == VaultAum.RETRY_STALE_ORACLE) {
                    ++numStaleOracles;
                  }
                }
              } else {
                stateChanges.clear(i);
                stateContextArray[i] = null;
              }
              i = stateChanges.nextSetBit(i + 1);
              if (i < 0) {
                break;
              }
            }
          }

          if (numStaleOracles == 0 && stateChanges.cardinality() == 0) {
            break;
          } else if (++groupRetries > maxGroupRetries) {
            final var nonPricedVaults = Arrays.stream(stateContextArray).<String>mapMulti((vault, downstream) -> {
              if (vault != null) {
                downstream.accept(vault.stateAccountKey().toBase58());
              }
            }).collect(Collectors.joining(","));
            logger.log(ERROR, "Giving up trying to price the following vault states: " + nonPricedVaults);
            break;
          } else {
            logger.log(INFO, String.format(
                    "Retrying %d out of %d vaults.",
                    numStaleOracles, numVaults
                )
            );
            Thread.sleep(2000);
          }
        }
      }
    } catch (final InterruptedException e) {
      // exit
    } catch (final RuntimeException ex) {
      logger.log(ERROR, "Unexpected error refreshing vaults states and executing pricing.", ex);
    }
  }


  private VaultStateContext[] refreshStateContext() {
    final var stateContextArray = stateCache.stream().sorted(BY_USD_VALUE).toArray(VaultStateContext[]::new);
    refreshStateContext(stateContextArray);
    return stateContextArray;
  }

  private void refreshStateContext(final VaultStateContext[] stateContextArray) {
    final int numVaults = stateContextArray.length;
    if (numVaults == 0) {
      return;
    }

    final var stateAccountKeys = Arrays.stream(stateContextArray).map(VaultStateContext::stateAccountKey).toList();

    for (int from = 0, to; ; from = to) {
      to = Math.min(from + SolanaRpcClient.MAX_MULTIPLE_ACCOUNTS, stateContextArray.length);
      final var batch = stateAccountKeys.subList(from, to);
      final var accountInfoList = rpcCaller.courteousGet(
          rpcClient -> rpcClient.getAccounts(batch),
          "rpcClient#getGlamStateAccounts"
      );
      int i = from;
      for (final var accountInfo : accountInfoList) {
        stateCache.acceptStateAccount(stateContextArray[i], accountInfo);
        if (AccountFetcher.isNull(accountInfo)) {
          stateContextArray[i] = null;
        }
        ++i;
      }
      if (to == numVaults) {
        break;
      }
    }
  }
}
