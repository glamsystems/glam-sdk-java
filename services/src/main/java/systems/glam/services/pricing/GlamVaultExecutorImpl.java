package systems.glam.services.pricing;

import software.sava.rpc.json.http.client.SolanaRpcClient;
import software.sava.services.solana.remote.call.RpcCaller;

import java.util.Arrays;
import java.util.Comparator;

import static java.lang.System.Logger.Level.ERROR;
import static java.util.concurrent.TimeUnit.*;

final class GlamVaultExecutorImpl implements GlamVaultExecutor {

  private static final System.Logger logger = System.getLogger(GlamVaultExecutor.class.getName());

  private static final Comparator<VaultStateContext> BY_USD_VALUE = (a, b) -> Long.compareUnsigned(a.usdValue(), b.usdValue());

  private final RpcCaller rpcCaller;
  private final GlamStateContextCache stateCache;

  GlamVaultExecutorImpl(final RpcCaller rpcCaller, final GlamStateContextCache stateCache) {
    this.rpcCaller = rpcCaller;
    this.stateCache = stateCache;
  }

  @Override
  public void run() {
    try {
      Thread.sleep(5_000);
      for (; ; ) {
        // TODO run N minutes before schedule
        final var stateContextArray = refreshStateContext();
        // Delay to one second before the scheduled time.
        for (int i = stateContextArray.length - 1; i >= 0; i--) {
          final var stateContext = stateContextArray[i];
          if (stateContext != null) {
            final var positionReportFuture = stateContext.priceVault();
            if (positionReportFuture == null) {

            } else {
              final var positionReport = positionReportFuture.join();
            }
          }
        }
        SECONDS.sleep(15);
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
