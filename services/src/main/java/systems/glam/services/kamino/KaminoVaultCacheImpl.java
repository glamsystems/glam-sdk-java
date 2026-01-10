package systems.glam.services.kamino;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.lookup.AddressLookupTable;
import software.sava.idl.clients.kamino.lend.gen.types.Reserve;
import software.sava.idl.clients.kamino.vaults.gen.types.VaultState;
import software.sava.rpc.json.http.client.SolanaRpcClient;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.services.core.request_capacity.context.CallContext;
import software.sava.services.solana.remote.call.RpcCaller;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

final class KaminoVaultCacheImpl implements KaminoVaultCache {

  static final System.Logger logger = System.getLogger(KaminoVaultCacheImpl.class.getName());

  private final RpcCaller rpcCaller;
  private final CallContext getProgramAccountsCallContext;
  private final Function<SolanaRpcClient, CompletableFuture<List<AccountInfo<byte[]>>>> getVaultAccounts;
  // TODO: Move out Reserve's to reserve cache.
  private volatile Map<PublicKey, KaminoVaultContext> vaultsByKey;
  private volatile Map<PublicKey, KaminoVaultContext> vaultsByShareMint;

  public KaminoVaultCacheImpl(final RpcCaller rpcCaller,
                              final CallContext getProgramAccountsCallContext,
                              final Function<SolanaRpcClient, CompletableFuture<List<AccountInfo<byte[]>>>> getVaultAccounts,
                              final Map<PublicKey, KaminoVaultContext> vaultsByKey,
                              final Map<PublicKey, KaminoVaultContext> vaultsByShareMint) {
    this.rpcCaller = rpcCaller;
    this.getProgramAccountsCallContext = getProgramAccountsCallContext;
    this.getVaultAccounts = getVaultAccounts;
    this.vaultsByKey = vaultsByKey;
    this.vaultsByShareMint = vaultsByShareMint;
  }

  @Override
  public KaminoVaultContext vault(final PublicKey vaultKey) {
    return vaultsByKey.get(vaultKey);
  }

  @Override
  public KaminoVaultContext vaultForShareMint(final PublicKey sharesMint) {
    return vaultsByShareMint.get(sharesMint);
  }

  static List<VaultState> parseVaultList(final List<AccountInfo<byte[]>> accountInfoList) {
    return accountInfoList.stream()
        .map(VaultState::read)
        .toList();
  }

  static Map<PublicKey, AddressLookupTable> parseTables(final List<AccountInfo<byte[]>> accountInfoList) {
    return accountInfoList.stream().<AddressLookupTable>mapMulti(((accountInfo, addressLookupTableConsumer) -> {
      if (accountInfo != null) {
        final var table = AddressLookupTable.read(accountInfo.pubKey(), accountInfo.data());
        addressLookupTableConsumer.accept(table);
      }
    })).collect(Collectors.toUnmodifiableMap(AddressLookupTable::address, Function.identity()));
  }

  static Map<PublicKey, Reserve> parseReserves(final List<AccountInfo<byte[]>> accountInfoList) {
    return accountInfoList.stream()
        .map(Reserve::read)
        .collect(Collectors.toUnmodifiableMap(Reserve::_address, Function.identity()));
  }

  static List<PublicKey> tableKeys(final List<VaultState> kVaults) {
    return kVaults.stream().<PublicKey>mapMulti(((vaultState, consumer) -> {
      final var tableKey = vaultState.vaultLookupTable();
      if (!tableKey.equals(PublicKey.NONE)) {
        consumer.accept(tableKey);
      }
    })).distinct().toList();
  }

  static List<PublicKey> reserveKeys(final List<VaultState> kVaults) {
    return kVaults.stream().<PublicKey>mapMulti(((vaultState, consumer) -> {
      for (final var allocation : vaultState.vaultAllocationStrategy()) {
        final var reserveKey = allocation.reserve();
        if (!reserveKey.equals(PublicKey.NONE)) {
          consumer.accept(reserveKey);
        }
      }
    })).distinct().toList();
  }

  static Map<PublicKey, KaminoVaultContext> reMapByKey(final Map<PublicKey, KaminoVaultContext> contextMap) {
    return contextMap.values().stream().collect(Collectors
        .toUnmodifiableMap(c -> c.vaultState()._address(), Function.identity()));
  }

  @Override
  public void run() {
    // TODO: Only refresh if unable to resolve an external token account. Vaults & Reserves
    try {
      var accountInfoList = rpcCaller.courteousGet(
          getVaultAccounts,
          getProgramAccountsCallContext,
          "rpcClient::getKaminoVaults"
      );
      final var kVaults = parseVaultList(accountInfoList);
      final var kVaultTableKeys = tableKeys(kVaults);
      accountInfoList = rpcCaller.courteousGet(
          rpcClient -> rpcClient.getAccounts(kVaultTableKeys),
          "rpcClient#getKaminoVaultTables"
      );
      final var tables = parseTables(accountInfoList);
      final var kVaultReserves = reserveKeys(kVaults);
      accountInfoList = rpcCaller.courteousGet(
          rpcClient -> rpcClient.getAccounts(kVaultReserves),
          "rpcClient#getKaminoVaultReserves"
      );
      this.vaultsByShareMint = KaminoVaultContext.joinContext(kVaults, tables, parseReserves(accountInfoList));
      this.vaultsByKey = reMapByKey(this.vaultsByShareMint);
    } catch (final RuntimeException e) {
      logger.log(System.Logger.Level.ERROR, "Failed to fetch kamino vaults", e);
    }
  }

//  @Override
//  public void schedule(final GlamServiceConfig serviceConfig, final ScheduledExecutorService scheduledExecutorService) {
//    serviceConfig.driftVaultsSchedule().scheduleTask(scheduledExecutorService, this);
//  }
}
