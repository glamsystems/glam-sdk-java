package systems.glam.services.state;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.sdk.idl.programs.glam.config.gen.types.GlobalConfig;
import systems.glam.services.mints.AssetMetaContext;
import systems.glam.services.mints.MintCache;
import systems.glam.services.mints.MintContext;
import systems.glam.services.rpc.AccountFetcher;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static systems.glam.services.state.GlobalConfigCacheImpl.*;

public interface GlobalConfigCache extends Runnable {

  static CompletableFuture<GlobalConfigCache> initCache(final Path globalConfigFilePath,
                                                        final PublicKey configProgram, final PublicKey globalConfigKey,
                                                        final SolanaAccounts solanaAccounts,
                                                        final MintCache mintCache,
                                                        final RpcCaller rpcCaller,
                                                        final AccountFetcher accountFetcher,
                                                        final Duration fetchDelay) {
    if (Files.exists(globalConfigFilePath)) {
      try {
        final byte[] data = Files.readAllBytes(globalConfigFilePath);
        final var globalConfig = GlobalConfig.read(globalConfigKey, data);
        final var assetMetaContexts = AssetMetaContext.mapAssetMetas(globalConfig);
        final var assetMetaMap = createMap(assetMetaContexts);
        final var globalConfigUpdate = new GlobalConfigCacheImpl.GlobalConfigUpdate(0, assetMetaContexts, data);
        final var cache = new GlobalConfigCacheImpl(
            globalConfigFilePath,
            configProgram, globalConfigKey,
            solanaAccounts,
            mintCache,
            accountFetcher,
            fetchDelay,
            globalConfigUpdate, assetMetaMap
        );
        return CompletableFuture.completedFuture(cache);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    } else {
      try {
        Files.createDirectories(globalConfigFilePath.getParent());
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
      return rpcCaller.courteousCall(
          rpcClient -> rpcClient.getAccountInfo(globalConfigKey),
          "rpcClient::getGlobalConfigAccount"
      ).thenApply(accountInfo -> {
        final long slot = accountInfo.context().slot();
        final byte[] data = accountInfo.data();
        if (checkAccount(configProgram, accountInfo.owner(), slot, accountInfo.pubKey(), data)) {
          final var globalConfig = GlobalConfig.read(accountInfo.pubKey(), data);
          final var assetMetaContexts = AssetMetaContext.mapAssetMetas(globalConfig);
          final var assetMetaMap = createMapChecked(slot, assetMetaContexts, mintCache);
          final var globalConfigUpdate = new GlobalConfigCacheImpl.GlobalConfigUpdate(
              slot, assetMetaContexts, data
          );
          persistGlobalConfig(globalConfigFilePath, data);
          final var mintsNeeded = Arrays.stream(globalConfig.assetMetas()).<PublicKey>mapMulti((assetMeta, downstream) -> {
            final var asset = assetMeta.asset();
            final var mintContext = mintCache.get(asset);
            if (mintContext == null) {
              downstream.accept(asset);
            }
          }).toList();
          final var cache = new GlobalConfigCacheImpl(
              globalConfigFilePath,
              configProgram, globalConfigKey,
              solanaAccounts,
              mintCache,
              accountFetcher,
              fetchDelay,
              globalConfigUpdate, assetMetaMap
          );
          if (!mintsNeeded.isEmpty()) {
            accountFetcher.priorityQueueBatchable(mintsNeeded, cache);
          }
          return cache;
        } else {
          throw new IllegalStateException("Unexpected GlobalConfig Account.");
        }
      });
    }
  }

  private static Map<PublicKey, AssetMetaContext[]> createMap(final AssetMetaContext[] assetMetaContexts) {
    final var assetMetaMap = HashMap.<PublicKey, AssetMetaContext[]>newHashMap(assetMetaContexts.length);
    for (final var assetMetaContext : assetMetaContexts) {
      final var mint = assetMetaContext.asset();
      final var entries = assetMetaMap.get(mint);
      if (entries == null) {
        assetMetaMap.put(mint, new AssetMetaContext[]{assetMetaContext});
      } else {
        final int len = entries.length;
        final var newEntries = Arrays.copyOf(entries, len + 1);
        newEntries[len] = assetMetaContext;
        assetMetaMap.put(mint, newEntries);
      }
    }
    for (final var assetMetas : assetMetaMap.values()) {
      if (assetMetas.length > 1) {
        Arrays.sort(assetMetas);
      }
    }
    return assetMetaMap;
  }

  AssetMetaContext getByIndex(final int index);

  AssetMetaContext topPriorityForMint(final PublicKey mint);

  AssetMetaContext solAssetMeta();

  AssetMetaContext topPriorityForMintChecked(final PublicKey mint);

  AssetMetaContext topPriorityForMintChecked(final MintContext mintContext);

  void subscribe(final SolanaRpcWebsocket websocket);
}
