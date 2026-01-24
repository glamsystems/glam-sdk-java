package systems.glam.services;

import software.sava.core.accounts.PublicKey;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.sdk.idl.programs.glam.config.gen.types.AssetMeta;
import systems.glam.sdk.idl.programs.glam.config.gen.types.GlobalConfig;
import systems.glam.services.rpc.AccountFetcher;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static systems.glam.services.GlobalConfigCacheImpl.*;

public interface GlobalConfigCache extends Runnable {

  static CompletableFuture<GlobalConfigCache> initCache(final Path globalConfigFilePath,
                                                        final PublicKey configProgram, final PublicKey globalConfigKey,
                                                        final RpcCaller rpcCaller,
                                                        final AccountFetcher accountFetcher,
                                                        final Duration fetchDelay) {
    if (Files.exists(globalConfigFilePath)) {
      try {
        final byte[] data = Files.readAllBytes(globalConfigFilePath);
        final var globalConfig = GlobalConfig.read(globalConfigKey, data);
        final var assetMetaMap = GlobalConfigCacheImpl.createMap(globalConfig);
        final var globalConfigUpdate = new GlobalConfigCacheImpl.GlobalConfigUpdate(0, globalConfig, data);
        final var cache = new GlobalConfigCacheImpl(
            globalConfigFilePath,
            configProgram, globalConfigKey,
            accountFetcher, fetchDelay, globalConfigUpdate, assetMetaMap
        );
        return CompletableFuture.completedFuture(cache);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    } else {
      return rpcCaller.courteousCall(
          rpcClient -> rpcClient.getAccountInfo(globalConfigKey),
          "rpcClient::getGlobalConfigAccount"
      ).thenApply(accountInfo -> {
        final long slot = accountInfo.context().slot();
        final byte[] data = accountInfo.data();
        if (checkAccount(configProgram, accountInfo.owner(), slot, accountInfo.pubKey(), data)) {
          final var globalConfig = GlobalConfig.read(accountInfo.pubKey(), data);
          final var assetMetaMap = createMapChecked(slot, globalConfig);
          final var globalConfigUpdate = new GlobalConfigCacheImpl.GlobalConfigUpdate(
              slot, globalConfig, data
          );
          persistGlobalConfig(globalConfigFilePath, data);
          return new GlobalConfigCacheImpl(
              globalConfigFilePath,
              configProgram, globalConfigKey,
              accountFetcher, fetchDelay, globalConfigUpdate, assetMetaMap
          );
        } else {
          throw new IllegalStateException("Unexpected GlobalConfig Account.");
        }
      });
    }
  }

  AssetMeta getByIndex(final int index);

  AssetMeta topPriorityForMint(final PublicKey mint);

  void subscribe(final SolanaRpcWebsocket websocket);
}
