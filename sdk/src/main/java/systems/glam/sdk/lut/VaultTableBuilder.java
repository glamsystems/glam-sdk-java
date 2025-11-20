package systems.glam.sdk.lut;

import software.sava.core.accounts.PublicKey;
import software.sava.idl.clients.drift.DriftAccounts;
import software.sava.idl.clients.drift.vaults.gen.types.VaultDepositor;
import software.sava.idl.clients.kamino.KaminoAccounts;
import software.sava.idl.clients.kamino.lend.gen.types.Obligation;
import software.sava.idl.clients.kamino.vaults.gen.types.VaultState;
import software.sava.rpc.json.http.client.SolanaRpcClient;
import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.sdk.StateAccountClient;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateAccount;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public interface VaultTableBuilder {

  static VaultTableBuilder createBuilder(final StateAccount stateAccount, final PublicKey feePayer) {
    final var stateAccountClient = StateAccountClient.createClient(stateAccount, feePayer);
    return createBuilder(stateAccountClient);
  }

  static VaultTableBuilder createBuilder(final StateAccountClient stateAccountClient) {
    final var stateAccount = stateAccountClient.stateAccount();
    final var accountsNeeded = HashSet.<PublicKey>newHashSet(256);
    accountsNeeded.addAll(Arrays.asList(stateAccount.externalPositions()));
    accountsNeeded.addAll(Arrays.asList(stateAccount.assets())); // Need the token programs to derive the token accounts.
    final var baseAssetMint = stateAccount.baseAssetMint();
    if (baseAssetMint != null && !baseAssetMint.equals(PublicKey.NONE)) {
      accountsNeeded.add(baseAssetMint);
    }

    if (stateAccountClient.driftEnabled() || stateAccountClient.driftVaultsEnabled()) {
      final var driftAccounts = DriftAccounts.MAIN_NET;
      accountsNeeded.addAll(driftAccounts.marketLookupTables());
    }

    final Map<PublicKey, VaultDepositor> driftVaultDepositors = stateAccountClient.driftVaultsEnabled()
        ? HashMap.newHashMap(8)
        : Map.of();

    final Map<PublicKey, Obligation> glamVaultKaminoObligations;
    if (stateAccountClient.kaminoLendEnabled()) {
      accountsNeeded.add(KaminoAccounts.MAIN_NET.mainMarketLUT());
      glamVaultKaminoObligations = HashMap.newHashMap(16);
    } else {
      glamVaultKaminoObligations = Map.of();
    }

    final Map<PublicKey, VaultState> kaminoVaults;
    if (stateAccountClient.kaminoVaultsEnabled()) {
      accountsNeeded.add(KaminoAccounts.MAIN_NET.mainMarketLUT());
      kaminoVaults = HashMap.newHashMap(8);
    } else {
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
        driftVaultDepositors,
        glamVaultKaminoObligations,
        kaminoVaults
    );
  }

  static CompletableFuture<List<AccountInfo<byte[]>>> fetchKaminoVaultStates(final SolanaRpcClient rpcClient) {
    final var filters = List.of(VaultState.SIZE_FILTER, VaultState.DISCRIMINATOR_FILTER);
    return rpcClient.getProgramAccounts(KaminoAccounts.MAIN_NET.kVaultsProgram(), filters);
  }

  static Map<PublicKey, VaultState> mapKaminoVaultStatesByMint(final List<AccountInfo<byte[]>> vaults) {
    return vaults.stream().map(VaultState::read).collect(Collectors.toMap(VaultState::tokenMint, v -> v));
  }

  StateAccountClient stateAccountClient();

  Set<PublicKey> accountsNeeded();

  /// Note: If the set of accounts needed exceeds 100 this call will fail and will require batching the requests by the user.
  default CompletableFuture<List<AccountInfo<byte[]>>> fetchAccountsNeeded(final SolanaRpcClient rpcClient) {
    return rpcClient.getAccounts(List.copyOf(accountsNeeded()));
  }

  Set<PublicKey> secondPhaseAccountsNeeded();

  /// Note: If the set of accounts needed exceeds 100 this call will fail and will require batching the requests by the user.
  default CompletableFuture<List<AccountInfo<byte[]>>> fetchSecondPhaseAccountsNeeded(final SolanaRpcClient rpcClient) {
    return rpcClient.getAccounts(List.copyOf(secondPhaseAccountsNeeded()));
  }

  default void addAccounts(final List<AccountInfo<byte[]>> accountsNeeded,
                           final Map<PublicKey, VaultState> kVaultStatesByMint,
                           final boolean ignoreKVaultTable) {
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
      addKaminoVaultAccounts(accountsNeeded, kVaultStatesByMint, ignoreKVaultTable);
    }
  }

  default void addAccounts(final List<AccountInfo<byte[]>> accountsNeeded,
                           final Map<PublicKey, VaultState> kVaultStatesByMint) {
    addAccounts(accountsNeeded, kVaultStatesByMint, false);
  }

  default void addAccountsSecondPhase(final List<AccountInfo<byte[]>> accountsNeeded) {
    addDriftVaultAccountsSecondPhase(accountsNeeded);
    addKaminoAccountsSecondPhase(accountsNeeded);
    addKaminoVaultAccountsSecondPhase(accountsNeeded);
  }

  void addGlamVaultAccounts(final List<AccountInfo<byte[]>> accountsNeeded);

  void addGlamVaultTokens(final List<AccountInfo<byte[]>> accountsNeeded);

  void addDriftAccounts(final List<AccountInfo<byte[]>> accountsNeeded);

  void addDriftVaultAccounts(final List<AccountInfo<byte[]>> accountsNeeded);

  void addDriftVaultAccountsSecondPhase(final List<AccountInfo<byte[]>> accountsNeeded);

  void addJupiterSwapAccounts(final List<AccountInfo<byte[]>> accountsNeeded);

  void addKaminoLendAccounts(final List<AccountInfo<byte[]>> accountsNeeded);

  void addKaminoAccountsSecondPhase(final List<AccountInfo<byte[]>> accountsNeeded);

  void addKaminoVaultAccounts(final List<AccountInfo<byte[]>> accountsNeeded,
                              final Map<PublicKey, VaultState> vaultStatesByMint,
                              final boolean ignoreKVaultTable);

  void addKaminoVaultAccountsSecondPhase(final List<AccountInfo<byte[]>> accountsNeeded);
}
