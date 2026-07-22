package systems.glam.services.mints;

import software.sava.core.accounts.PublicKey;
import software.sava.core.rpc.Filter;
import software.sava.idl.clients.spl.stakepool.StakePoolState;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.ws.SolanaRpcWebsocket;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.services.io.KeyedFlatFile;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static java.lang.System.Logger.Level.ERROR;

final class StakePoolCacheImpl implements StakePoolCache, Consumer<AccountInfo<byte[]>> {

  private static final System.Logger logger = System.getLogger(StakePoolCache.class.getName());

  private final Duration fetchDelay;
  private final RpcCaller rpcCaller;
  private final List<Filter> stakePoolFilters;
  private final Map<PublicKey, KeyedFlatFile<StakePoolContext>> stakePoolFileChannelByProgram;
  private final Map<PublicKey, StakePoolContext> stakePoolContextByMint;

  StakePoolCacheImpl(final Duration fetchDelay,
                     final RpcCaller rpcCaller,
                     final List<Filter> stakePoolFilters,
                     final Map<PublicKey, KeyedFlatFile<StakePoolContext>> stakePoolFileChannelByProgram,
                     final Map<PublicKey, StakePoolContext> stakePoolContextByMint) {
    // This delay is slept between polling passes; below a millisecond that
    // sleep rounds to nothing and the loop spins a core.
    if (fetchDelay.toMillis() < 1) {
      throw new IllegalArgumentException(
          "A stake pool cache needs a fetch delay of at least one millisecond, not " + fetchDelay
      );
    }
    this.fetchDelay = fetchDelay;
    this.rpcCaller = rpcCaller;
    this.stakePoolFilters = stakePoolFilters;
    this.stakePoolFileChannelByProgram = stakePoolFileChannelByProgram;
    this.stakePoolContextByMint = stakePoolContextByMint;
  }

  List<AccountInfo<byte[]>> fetchStateAccounts(final PublicKey stakePoolProgram) {
    return rpcCaller.courteousGet(
        rpcClient -> rpcClient.getProgramAccounts(stakePoolProgram, stakePoolFilters),
        "rpcClient::getStakePoolStateAccounts"
    );
  }

  @Override
  public void run() {
    try {
      for (; ; ) {
        for (final var stakePoolProgram : stakePoolFileChannelByProgram.keySet()) {
          final var stateAccounts = fetchStateAccounts(stakePoolProgram);
          stateAccounts.parallelStream().forEach(this);
        }
        //noinspection BusyWait
        Thread.sleep(fetchDelay);
      }
    } catch (final InterruptedException e) {
      // exit
    } catch (final RuntimeException ex) {
      logger.log(ERROR, "Unexpected error fetching stake pool accounts.", ex);
    }
  }

  @Override
  public void accept(final AccountInfo<byte[]> accountInfo) {
    final var stakePoolFileChannel = stakePoolFileChannelByProgram.get(accountInfo.owner());
    if (stakePoolFileChannel == null) {
      return;
    }
    final byte[] data = accountInfo.data();
    if (data.length < StakePoolState.NEXT_EPOCH_FEE_OFFSET) {
      return;
    }
    final var mintKey = PublicKey.readPubKey(data, StakePoolState.POOL_MINT_OFFSET);
    if (this.stakePoolContextByMint.containsKey(mintKey)) {
      return;
    }

    final var owner = accountInfo.owner();
    final var stakePoolContext = StakePoolContext.createContext(owner, accountInfo.pubKey(), mintKey);
    if (this.stakePoolContextByMint.putIfAbsent(mintKey, stakePoolContext) == null) {
      stakePoolFileChannel.appendEntry(stakePoolContext);
    }
  }

  @Override
  public void subscribe(final SolanaRpcWebsocket websocket) {
    for (final var stakePoolProgram : stakePoolFileChannelByProgram.keySet()) {
      websocket.programSubscribe(stakePoolProgram, this.stakePoolFilters, this);
    }
  }

  @Override
  public StakePoolContext get(final PublicKey mintPubkey) {
    return stakePoolContextByMint.get(mintPubkey);
  }

  @Override
  public void close() {
    for (final var stakePoolFileChannel : stakePoolFileChannelByProgram.values()) {
      stakePoolFileChannel.close();
    }
  }
}
