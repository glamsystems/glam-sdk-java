package systems.glam.services.mints;

import software.sava.core.accounts.PublicKey;
import software.sava.core.rpc.Filter;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import software.sava.services.solana.remote.call.RpcCaller;
import software.sava.solana.programs.stakepool.AccountType;
import software.sava.solana.programs.stakepool.StakePoolAccounts;
import systems.glam.services.io.FileUtils;
import systems.glam.services.io.KeyedFlatFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public interface StakePoolCache extends Runnable, AutoCloseable {

  private static KeyedFlatFile<StakePoolContext> createFlatFile(final Path cacheDirectory,
                                                                final PublicKey stakePoolProgram) {
    return KeyedFlatFile.createFlatFile(StakePoolContext.BYTES, FileUtils.resolveAccountPath(cacheDirectory, stakePoolProgram));
  }

  static CompletableFuture<StakePoolCache> initCache(final ExecutorService taskExecutor,
                                                     final Path stakePoolStateCacheDirectory,
                                                     final StakePoolAccounts stakePoolAccounts,
                                                     final Duration fetchDelay,
                                                     final RpcCaller rpcCaller) {
    final var multiValidatorStakePoolProgram = stakePoolAccounts.stakePoolProgram();
    final var sanctumMultiValidatorStakePoolProgram = stakePoolAccounts.sanctumMultiValidatorStakePoolProgram();
    final var sanctumSingleValidatorStakePoolProgram = stakePoolAccounts.sanctumSingleValidatorStakePoolProgram();
    final var stakePoolFilters = List.of(
        Filter.createMemCompFilter(0, new byte[]{(byte) AccountType.StakePool.ordinal()})
    );
    return CompletableFuture.supplyAsync(() -> {
          try {
            Files.createDirectories(stakePoolStateCacheDirectory);
            final var stakePoolFileChannelByProgram = Map.of(
                multiValidatorStakePoolProgram, createFlatFile(stakePoolStateCacheDirectory, multiValidatorStakePoolProgram),
                sanctumMultiValidatorStakePoolProgram, createFlatFile(stakePoolStateCacheDirectory, sanctumMultiValidatorStakePoolProgram),
                sanctumSingleValidatorStakePoolProgram, createFlatFile(stakePoolStateCacheDirectory, sanctumSingleValidatorStakePoolProgram)
            );
            final var stakePoolContextByMint = new ConcurrentHashMap<PublicKey, StakePoolContext>();
            final var stakePoolCache = new StakePoolCacheImpl(
                fetchDelay,
                rpcCaller,
                stakePoolFilters,
                stakePoolFileChannelByProgram,
                stakePoolContextByMint
            );

            for (final var entry : stakePoolFileChannelByProgram.entrySet()) {
              final var stakePoolProgram = entry.getKey();
              final var stakePoolFileChannel = entry.getValue();
              final byte[] data = Files.readAllBytes(stakePoolFileChannel.filePath());
              if (data.length == 0) {
                stakePoolCache.fetchStateAccounts(stakePoolProgram);
              } else {
                for (int i = 0; i < data.length; i += StakePoolContext.BYTES) {
                  final var stakePoolContext = StakePoolContext.read(stakePoolProgram, data, i);
                  stakePoolContextByMint.put(stakePoolContext.mintKey(), stakePoolContext);
                }
              }
            }

            return stakePoolCache;
          } catch (final IOException ex) {
            throw new UncheckedIOException(ex);
          }
        }, taskExecutor
    );
  }

  void subscribe(final SolanaRpcWebsocket websocket);

  StakePoolContext get(final PublicKey mintPubkey);

  @Override
  void close();
}
