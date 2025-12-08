package systems.glam.sdk.lut;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.lookup.AddressLookupTable;
import software.sava.idl.clients.drift.DriftAccounts;
import software.sava.idl.clients.drift.vaults.gen.types.VaultDepositor;
import software.sava.idl.clients.jupiter.JupiterAccounts;
import software.sava.idl.clients.kamino.KaminoAccounts;
import software.sava.idl.clients.kamino.lend.gen.types.Obligation;
import software.sava.idl.clients.kamino.vaults.gen.types.VaultState;
import software.sava.idl.clients.marinade.stake_pool.MarinadeAccounts;
import software.sava.idl.clients.meteora.MeteoraAccounts;
import software.sava.rpc.json.http.client.SolanaRpcClient;
import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.sdk.StateAccountClient;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateAccount;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public interface VaultTableBuilder {

  static VaultTableBuilder.Builder build() {
    return Builder.newBuilder();
  }

  static Map<PublicKey, VaultState> mapKaminoVaultStatesByMint(final List<AccountInfo<byte[]>> vaults) {
    return vaults.stream().map(VaultState::read).collect(Collectors.toMap(VaultState::tokenMint, v -> v));
  }

  StateAccountClient stateAccountClient();

  Set<PublicKey> accountsNeeded();

  /// Note: If the set of accounts needed exceeds 100, this call will fail and will require batching the requests by the user.
  default CompletableFuture<List<AccountInfo<byte[]>>> fetchAccountsNeeded(final SolanaRpcClient rpcClient) {
    return rpcClient.getAccounts(List.copyOf(accountsNeeded()));
  }

  default void addAccounts(final List<AccountInfo<byte[]>> accountsNeeded,
                           final Map<PublicKey, VaultState> kVaultStatesByMint) {
    addGlamVaultAccounts(accountsNeeded);
    addGlamVaultTokens(accountsNeeded);
    final var stateAccountClient = stateAccountClient();
    if (stateAccountClient.driftEnabled()) {
      addDriftAccounts(accountsNeeded);
    }
    if (stateAccountClient.driftVaultsEnabled()) {
      addDriftVaultAccounts(accountsNeeded);
    }
    if (stateAccountClient.jupiterSwapEnabled()) {
      addJupiterSwapAccounts(accountsNeeded);
    }
    if (stateAccountClient.kaminoLendEnabled()) {
      addKaminoLendAccounts(accountsNeeded);
    }
    if (stateAccountClient.kaminoVaultsEnabled()) {
      addKaminoVaultAccounts(accountsNeeded, kVaultStatesByMint);
    }
  }

  Set<PublicKey> secondPhaseAccountsNeeded();

  /// Note: If the set of accounts needed exceeds 100, this call will fail and will require batching the requests by the user.
  default CompletableFuture<List<AccountInfo<byte[]>>> fetchSecondPhaseAccountsNeeded(final SolanaRpcClient rpcClient) {
    return rpcClient.getAccounts(List.copyOf(secondPhaseAccountsNeeded()));
  }

  default void addAccountsSecondPhase(final List<AccountInfo<byte[]>> accountsNeeded) {
    addDriftVaultAccountsSecondPhase(accountsNeeded);
    addKaminoAccountsSecondPhase(accountsNeeded);
    addKaminoVaultAccountsSecondPhase(accountsNeeded);
  }

  CompletableFuture<List<AddressLookupTable>> fetchGlamVaultTables(final SolanaRpcClient rpcClient);

  default void removeExternalProtocolTableAccounts() {
    removeDriftTableAccounts();
    removeKaminoLendTableAccounts();
    removeKaminoVaultTableAccounts();
  }

  List<TableTask> batchTableTasks(final List<AddressLookupTable> lookupTables);

  void addGlamVaultAccounts(final List<AccountInfo<byte[]>> accountsNeeded);

  void addGlamVaultTokens(final List<AccountInfo<byte[]>> accountsNeeded);

  void removeDriftTableAccounts();

  void addDriftAccounts(final List<AccountInfo<byte[]>> accountsNeeded);

  void addDriftVaultAccounts(final List<AccountInfo<byte[]>> accountsNeeded);

  void addDriftVaultAccountsSecondPhase(final List<AccountInfo<byte[]>> accountsNeeded);

  void addJupiterSwapAccounts(final List<AccountInfo<byte[]>> accountsNeeded);

  void removeKaminoLendTableAccounts();

  void addKaminoLendAccounts(final List<AccountInfo<byte[]>> accountsNeeded);

  void addKaminoAccountsSecondPhase(final List<AccountInfo<byte[]>> accountsNeeded);

  void removeKaminoVaultTableAccounts();

  void addKaminoVaultAccounts(final List<AccountInfo<byte[]>> accountsNeeded,
                              final Map<PublicKey, VaultState> vaultStatesByMint);

  void addKaminoVaultAccountsSecondPhase(final List<AccountInfo<byte[]>> accountsNeeded);

  final class Builder {

    private DriftAccounts driftAccounts = DriftAccounts.MAIN_NET;
    private JupiterAccounts jupiterAccounts = JupiterAccounts.MAIN_NET;
    private KaminoAccounts kaminoAccounts = KaminoAccounts.MAIN_NET;
    private MarinadeAccounts marinadeAccounts = MarinadeAccounts.MAIN_NET;
    private MeteoraAccounts meteorAccounts = MeteoraAccounts.MAIN_NET;

    public static Builder newBuilder() {
      return new Builder();
    }

    public Builder driftAccounts(final DriftAccounts driftAccounts) {
      this.driftAccounts = Objects.requireNonNull(driftAccounts);
      return this;
    }

    public DriftAccounts driftAccounts() {
      return driftAccounts;
    }

    public Builder jupiterAccounts(final JupiterAccounts jupiterAccounts) {
      this.jupiterAccounts = Objects.requireNonNull(jupiterAccounts);
      return this;
    }

    public JupiterAccounts jupiterAccounts() {
      return jupiterAccounts;
    }

    public Builder kaminoAccounts(final KaminoAccounts kaminoAccounts) {
      this.kaminoAccounts = Objects.requireNonNull(kaminoAccounts);
      return this;
    }

    public KaminoAccounts kaminoAccounts() {
      return kaminoAccounts;
    }

    public Builder marinadeAccounts(final MarinadeAccounts marinadeAccounts) {
      this.marinadeAccounts = Objects.requireNonNull(marinadeAccounts);
      return this;
    }

    public MarinadeAccounts marinadeAccounts() {
      return marinadeAccounts;
    }

    public Builder meteorAccounts(final MeteoraAccounts meteorAccounts) {
      this.meteorAccounts = Objects.requireNonNull(meteorAccounts);
      return this;
    }

    public MeteoraAccounts meteorAccounts() {
      return meteorAccounts;
    }

    public VaultTableBuilder create(final StateAccount stateAccount, final PublicKey feePayer) {
      final var stateAccountClient = StateAccountClient.createClient(stateAccount, feePayer);
      return create(stateAccountClient);
    }

    public VaultTableBuilder create(final StateAccountClient stateAccountClient) {
      final var stateAccount = stateAccountClient.stateAccount();
      final var accountsNeeded = HashSet.<PublicKey>newHashSet(256);
      accountsNeeded.addAll(Arrays.asList(stateAccount.externalPositions()));
      accountsNeeded.addAll(Arrays.asList(stateAccount.assets())); // Need the token programs to derive the token accounts.
      final var baseAssetMint = stateAccount.baseAssetMint();
      if (baseAssetMint != null && !baseAssetMint.equals(PublicKey.NONE)) {
        accountsNeeded.add(baseAssetMint);
      }

      final Map<PublicKey, AddressLookupTable> driftLookupTables;
      if (stateAccountClient.driftEnabled() || stateAccountClient.driftVaultsEnabled()) {
        final var tableKeys = driftAccounts.marketLookupTables();
        accountsNeeded.addAll(tableKeys);
        driftLookupTables = HashMap.newHashMap(tableKeys.size());
      } else {
        driftLookupTables = Map.of();
      }

      final Map<PublicKey, VaultDepositor> driftVaultDepositors = stateAccountClient.driftVaultsEnabled()
          ? HashMap.newHashMap(8)
          : Map.of();

      final Map<PublicKey, AddressLookupTable> kaminoLendLookupTables;
      final Map<PublicKey, Obligation> glamVaultKaminoObligations;
      if (stateAccountClient.kaminoLendEnabled()) {
        accountsNeeded.add(kaminoAccounts.mainMarketLUT());
        kaminoLendLookupTables = HashMap.newHashMap(16);
        glamVaultKaminoObligations = HashMap.newHashMap(16);
      } else {
        kaminoLendLookupTables = Map.of();
        glamVaultKaminoObligations = Map.of();
      }

      final Map<PublicKey, AddressLookupTable> kaminoVaultLookupTables;
      final Map<PublicKey, VaultState> kaminoVaults;
      if (stateAccountClient.kaminoVaultsEnabled()) {
        accountsNeeded.add(kaminoAccounts.mainMarketLUT());
        kaminoVaultLookupTables = HashMap.newHashMap(8);
        kaminoVaults = HashMap.newHashMap(8);
      } else {
        kaminoVaultLookupTables = Map.of();
        kaminoVaults = Map.of();
      }

      final var secondPhaseAccountsNeeded = HashSet.<PublicKey>newHashSet(128);
      final var glamVaultTableAccounts = HashSet.<PublicKey>newHashSet(256);

      final var accountClient = stateAccountClient.accountClient();
      final var vaultAccounts = accountClient.vaultAccounts();
      final var glamAccounts = accountClient.glamAccounts();
      final var globalConfig = glamAccounts.globalConfigPDA().publicKey();

      final var tablePrefix = List.of(
          vaultAccounts.glamStateKey(),
          vaultAccounts.vaultPublicKey(),
          globalConfig
      );

      return new VaultTableBuilderImpl(
          stateAccountClient,
          tablePrefix,
          accountsNeeded,
          secondPhaseAccountsNeeded,
          glamVaultTableAccounts,
          driftAccounts,
          driftLookupTables,
          driftVaultDepositors,
          jupiterAccounts,
          kaminoAccounts,
          kaminoLendLookupTables,
          glamVaultKaminoObligations,
          kaminoVaultLookupTables,
          kaminoVaults,
          marinadeAccounts,
          meteorAccounts
      );
    }
  }
}
