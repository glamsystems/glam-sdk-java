package systems.glam.services.pricing;

import software.sava.rpc.json.http.client.SolanaRpcClient;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.services.pricing.accounting.VaultAum;

import java.time.LocalTime;
import java.util.Arrays;
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
      for (; ; ) {
        waitUntil(scheduleTimeUnit, 30);

        final var stateContextArray = refreshStateContext();
        if (stateContextArray.length > results.length) {
          results = (CompletableFuture<VaultAum>[]) new CompletableFuture[stateContextArray.length << 1];
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

          int retryCount = 0;
          for (int i = stateContextArray.length - 1; i >= 0; i--) {
            final var resultFuture = results[i];
            results[i] = null;
            if (resultFuture != null) {
              final var vaultAUM = resultFuture.join();
              if (vaultAUM != VaultAum.RETRY) {
                // TODO: Handle vaults that changed state and need accounts.
                stateContextArray[i] = null;
              } else {
                ++retryCount;
              }
            }
          }
          if (retryCount == 0) {
            break;
          } else if (++groupRetries > maxGroupRetries) {
            final var nonPricedVaults = Arrays.stream(stateContextArray).<String>mapMulti((vault, downstream) -> {
              if (vault != null) {
                downstream.accept(vault.stateAccountKey().toBase58());
              }
            }).collect(Collectors.joining(","));
            logger.log(ERROR, "Giving up trying to price the following vault states: " + nonPricedVaults
            );
            break;
          } else {
            logger.log(INFO, String.format(
                    "Retrying %d out of %d vaults.",
                    retryCount, numVaults
                )
            );
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
    final int numVaults = stateContextArray.length;
    if (numVaults == 0) {
      return stateContextArray;
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
        if (accountInfo == null) {
          stateContextArray[i] = null;
        }
        ++i;
      }
      if (to == numVaults) {
        break;
      }
    }

    return stateContextArray;
  }
}
