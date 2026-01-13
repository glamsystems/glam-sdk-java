package systems.glam.services.integrations.kamino;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.kamino.KaminoAccounts;
import software.sava.idl.clients.kamino.vaults.gen.types.VaultState;
import software.sava.rpc.json.http.client.SolanaRpcClient;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.services.core.request_capacity.context.CallContext;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.services.pricing.MintCache;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static systems.glam.services.integrations.kamino.KaminoVaultCacheImpl.*;

public interface KaminoVaultCache extends Runnable {

  static CompletableFuture<KaminoVaultCache> createCache(final RpcCaller rpcCaller,
                                                         final CallContext getProgramAccountsCallContext,
                                                         final KaminoAccounts kaminoAccounts,
                                                         final MintCache mintCache) {
    final var filters = List.of(VaultState.SIZE_FILTER, VaultState.DISCRIMINATOR_FILTER);
    final Function<SolanaRpcClient, CompletableFuture<List<AccountInfo<byte[]>>>> getVaultAccounts
        = rpcClient -> rpcClient.getProgramAccounts(kaminoAccounts.kVaultsProgram(), filters);
    return rpcCaller
        .courteousCall(getVaultAccounts, getProgramAccountsCallContext, "rpcClient::getKaminoVaults")
        .thenCompose(accountInfoList -> {
          try {
            final var kVaults = parseVaultList(accountInfoList);
            final var kVaultTableKeys = tableKeys(kVaults);
            final var tablesFuture = rpcCaller.courteousCall(
                rpcClient -> rpcClient.getAccounts(kVaultTableKeys),
                "rpcClient#getKaminoVaultTables"
            );
            final var kVaultReserves = reserveKeys(kVaults);
            final var reservesFuture = rpcCaller.courteousCall(
                rpcClient -> rpcClient.getAccounts(kVaultReserves),
                "rpcClient#getKaminoVaultReserves"
            );
            return tablesFuture.thenCombine(reservesFuture, (tableAccounts, reserveAccounts) -> {
                  try {
                    final var byShareMint = KaminoVaultContext.joinContext(kVaults, parseTables(tableAccounts), parseReserves(reserveAccounts));
                    final var byVaultKey = reMapByKey(byShareMint);
                    return new KaminoVaultCacheImpl(rpcCaller, getProgramAccountsCallContext, getVaultAccounts, byVaultKey, byShareMint);
                  } catch (final RuntimeException ex) {
                    logger.log(System.Logger.Level.ERROR, "Failed to create Kamino Cache", ex);
                    return null;
                  }
                }
            );
          } catch (final RuntimeException ex) {
            logger.log(System.Logger.Level.ERROR, "Failed to create Kamino Cache", ex);
            return null;
          }
        });
  }

  KaminoVaultContext vault(final PublicKey vaultKey);

  KaminoVaultContext vaultForShareMint(final PublicKey sharesMint);
}
