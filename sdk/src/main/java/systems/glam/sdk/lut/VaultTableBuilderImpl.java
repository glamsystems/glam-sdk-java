package systems.glam.sdk.lut;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.Signer;
import software.sava.core.accounts.lookup.AddressLookupTable;
import software.sava.core.accounts.token.TokenAccount;
import software.sava.core.tx.Transaction;
import software.sava.idl.clients.drift.DriftAccounts;
import software.sava.idl.clients.drift.DriftPDAs;
import software.sava.idl.clients.drift.gen.types.User;
import software.sava.idl.clients.drift.vaults.gen.types.Vault;
import software.sava.idl.clients.drift.vaults.gen.types.VaultDepositor;
import software.sava.idl.clients.jupiter.JupiterAccounts;
import software.sava.idl.clients.kamino.KaminoAccounts;
import software.sava.idl.clients.kamino.lend.gen.types.Obligation;
import software.sava.idl.clients.kamino.lend.gen.types.Reserve;
import software.sava.idl.clients.kamino.vaults.gen.types.VaultState;
import software.sava.rpc.json.http.SolanaNetwork;
import software.sava.rpc.json.http.client.SolanaRpcClient;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.solana.programs.address_lookup_table.AddressLookupTableProgram;
import software.sava.solana.programs.compute_budget.ComputeBudgetProgram;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.sdk.StateAccountClient;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateAccount;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static software.sava.core.accounts.lookup.AddressLookupTable.*;
import static software.sava.core.rpc.Filter.createMemCompFilter;
import static software.sava.rpc.json.http.request.Commitment.CONFIRMED;
import static software.sava.solana.programs.compute_budget.ComputeBudgetProgram.MAX_COMPUTE_BUDGET;

record VaultTableBuilderImpl(StateAccountClient stateAccountClient,
                             List<PublicKey> tablePrefix,
                             Set<PublicKey> accountsNeeded,
                             Set<PublicKey> secondPhaseAccountsNeeded,
                             Set<PublicKey> glamVaultTableAccounts,
                             DriftAccounts driftAccounts,
                             Map<PublicKey, AddressLookupTable> driftLookupTables,
                             Map<PublicKey, VaultDepositor> driftVaultDepositors,
                             JupiterAccounts jupiterAccounts,
                             KaminoAccounts kaminoAccounts,
                             Map<PublicKey, AddressLookupTable> kaminoLookupTables,
                             Map<PublicKey, Obligation> glamVaultKaminoObligations,
                             Map<PublicKey, AddressLookupTable> kaminoVaultLookupTables,
                             Map<PublicKey, VaultState> kaminoVaults) implements VaultTableBuilder {

  private static void addAccount(final PublicKey key, final Set<PublicKey> accountsNeeded) {
    if (key != null && !key.equals(PublicKey.NONE)) {
      accountsNeeded.add(key);
    }
  }

  private void add(final PublicKey key) {
    addAccount(key, accountsNeeded);
  }

  private void addSecondPhase(final PublicKey key) {
    addAccount(key, secondPhaseAccountsNeeded);
  }

  private AddressLookupTable mapTable(final List<AccountInfo<byte[]>> accounts, final PublicKey tableKey) {
    if (tableKey == null || tableKey.equals(PublicKey.NONE)) {
      return null;
    }
    return accounts.stream()
        .filter(accountInfo -> accountInfo != null && accountInfo.pubKey().equals(tableKey))
        .findFirst()
        .map(accountInfo -> AddressLookupTable.read(accountInfo.pubKey(), accountInfo.data()))
        .orElse(null);
  }

  @Override
  public CompletableFuture<List<AddressLookupTable>> fetchGlamVaultTables(final SolanaRpcClient rpcClient) {
    final var accountClient = stateAccountClient.accountClient();
    final var addressLookupTableProgram = accountClient.solanaAccounts().addressLookupTableProgram();
    final byte[] prefixKeys = new byte[PublicKey.PUBLIC_KEY_LENGTH * tablePrefix.size()];
    int i = 0;
    for (final var key : tablePrefix) {
      i += key.write(prefixKeys, i);
    }
    return rpcClient.getProgramAccounts(
        addressLookupTableProgram,
        List.of(
            activeFilter(),
            createMemCompFilter(LOOKUP_TABLE_META_SIZE, prefixKeys)
        )
    ).thenApply(accounts -> accounts.stream()
        .map(accountInfo -> AddressLookupTable.read(accountInfo.pubKey(), accountInfo.data()))
        .toList()
    );
  }

  @Override
  public List<TableTask> batchTableTasks(final List<AddressLookupTable> lookupTables) {
    tablePrefix.forEach(glamVaultTableAccounts::remove);
    for (final var table : lookupTables) {
      glamVaultTableAccounts.removeIf(table::containKey);
    }

    // Sort tables by most populated.
    final var remainingTables = lookupTables.stream()
        .filter(table -> table.numAccounts() < LOOKUP_TABLE_MAX_ADDRESSES)
        .sorted(Comparator.comparingInt(AddressLookupTable::numAccounts).reversed())
        .toArray(AddressLookupTable[]::new);

    PublicKey tableKey;
    int tableSpace;
    if (remainingTables.length > 0) {
      final var maxTable = remainingTables[0];
      tableKey = maxTable.address();
      tableSpace = LOOKUP_TABLE_MAX_ADDRESSES - maxTable.numAccounts();
    } else {
      tableKey = null;
      tableSpace = LOOKUP_TABLE_MAX_ADDRESSES;
    }

    if (glamVaultTableAccounts.isEmpty()) {
      return List.of();
    }

    final var accountClient = stateAccountClient.accountClient();
    final var feePayer = accountClient.feePayerKey();
    final var accounts = glamVaultTableAccounts.toArray(PublicKey[]::new);
    final int maxAccountsWithCreateIx = 27;
    final var tasks = new ArrayList<TableTask>((accounts.length / maxAccountsWithCreateIx) + 1);
    CreateTable createTableTask = null;
    for (int i = 0, remainingTableIndex = 0; i < accounts.length; ) {
      final int remainingAccounts = accounts.length - i;
      final List<PublicKey> extendAccounts;
      final TableTask tableTask;
      if (tableKey == null || tableSpace == 0) {
        final int add = Math.min(maxAccountsWithCreateIx, tablePrefix.size() + remainingAccounts);
        extendAccounts = new ArrayList<>(add);
        extendAccounts.addAll(tablePrefix);
        for (final int to = i + add; i < to; ++i) {
          extendAccounts.add(accounts[i]);
        }
        createTableTask = new CreateTable(accountClient, extendAccounts);
        tableTask = createTableTask;
      } else {
        final int add = Math.min(tableSpace, Math.min(30, remainingAccounts));
        extendAccounts = new ArrayList<>(add);
        for (final int to = i + add; i < to; ++i) {
          extendAccounts.add(accounts[i]);
        }
        if (createTableTask != null) {
          tableTask = new DynamicExtendTable(accountClient, extendAccounts, createTableTask);
        } else {
          final var extendTableIx = AddressLookupTableProgram.extendLookupTable(
              accountClient.solanaAccounts(),
              tableKey,
              feePayer, feePayer,
              extendAccounts
          );
          tableTask = new ExtendTable(tableKey, List.of(extendTableIx));
        }
      }
      tasks.add(tableTask);
      tableSpace -= extendAccounts.size();
      if (tableSpace == 0) {
        if (++remainingTableIndex < remainingTables.length) {
          final var maxTable = remainingTables[remainingTableIndex];
          tableKey = maxTable.address();
          tableSpace = LOOKUP_TABLE_MAX_ADDRESSES - maxTable.numAccounts();
        } else {
          tableKey = null;
        }
      }
    }

    return tasks;
  }

  @Override
  public void addGlamVaultAccounts(final List<AccountInfo<byte[]>> accountsNeeded) {
    final var accountClient = stateAccountClient.accountClient();
    final var solanaAccounts = accountClient.solanaAccounts();
    final var glamAccounts = accountClient.glamAccounts();
    add(solanaAccounts.systemProgram());
    add(glamAccounts.protocolProgram());
    add(glamAccounts.readSplIntegrationAuthority().publicKey());
    final var stateAccount = stateAccountClient.stateAccount();
    final var mintKey = stateAccount.mint();
    if (mintKey != null && !PublicKey.NONE.equals(mintKey)) {
      add(glamAccounts.readMintIntegrationAuthority().publicKey());
      add(glamAccounts.mintEventAuthority());
      add(mintKey);
      final var escrowKey = stateAccountClient.escrowAccount().publicKey();
      add(escrowKey);
      final var splClient = accountClient.splClient();
      final var escrowTokenAccount = splClient.findATA(
          escrowKey, solanaAccounts.token2022Program(), mintKey
      );
      add(escrowTokenAccount.publicKey());
    }
    add(stateAccount.baseAssetMint());
  }

  @Override
  public void addGlamVaultTokens(final List<AccountInfo<byte[]>> accountsNeeded) {
    final var stateAccount = stateAccountClient.stateAccount();
    final var baseAssetMint = stateAccount.baseAssetMint();
    final var accountClient = stateAccountClient.accountClient();
    final var solanaAccounts = accountClient.solanaAccounts();
    final var tokenProgram = solanaAccounts.tokenProgram();
    final var token2022Program = solanaAccounts.token2022Program();

    for (final var accountInfo : accountsNeeded) {
      if (accountInfo == null) {
        continue;
      }
      final var program = accountInfo.owner();
      if (program.equals(tokenProgram) || program.equals(token2022Program)) {
        final var mint = accountInfo.pubKey();
        final var vaultTokenAccount = accountClient.findATA(program, mint).publicKey();
        add(vaultTokenAccount);

        if (mint.equals(baseAssetMint)) {
          final var escrowKey = stateAccountClient.escrowAccount().publicKey();
          final var splClient = accountClient.splClient();
          final var escrowBaseAssetTokenAccount = splClient.findATA(
              escrowKey, program, baseAssetMint
          );
          add(escrowBaseAssetTokenAccount.publicKey());
        }
      }
    }
  }

  private void removeAccounts(final Map<PublicKey, AddressLookupTable> tables) {
    for (final var table : tables.values()) {
      this.glamVaultTableAccounts.removeIf(table::containKey);
    }
  }

  @Override
  public void removeDriftTableAccounts() {
    removeAccounts(driftLookupTables);
  }

  private List<AddressLookupTable> mapDriftTables(final List<AccountInfo<byte[]>> accountsNeeded,
                                                  final DriftAccounts driftAccounts) {
    final var tableKeys = driftAccounts.marketLookupTables();
    return accountsNeeded.stream().<AddressLookupTable>mapMulti((accountInfo, downstream) -> {
      if (accountInfo != null) {
        if (tableKeys.contains(accountInfo.pubKey())) {
          final var table = AddressLookupTable.read(accountInfo.pubKey(), accountInfo.data());
          downstream.accept(table);
        }
      }
    }).toList();
  }

  private void addDriftUsers(final List<AccountInfo<byte[]>> accountsNeeded, final DriftAccounts driftAccounts) {
    final var driftProgram = driftAccounts.driftProgram();
    for (final var accountInfo : accountsNeeded) {
      if (accountInfo == null
          || !accountInfo.owner().equals(driftProgram)
          || accountInfo.data().length != User.BYTES
          || !User.DISCRIMINATOR.equals(accountInfo.data(), 0)) {
        continue;
      }
      final var user = User.read(accountInfo);
      add(user._address());
      for (final var spotPosition : user.spotPositions()) {
        final int marketIndex = spotPosition.marketIndex();
        final var spotMarketKey = DriftPDAs.deriveSpotMarketAccount(driftAccounts, marketIndex);
        add(spotMarketKey.publicKey());
        final var spotMarketVaultKey = DriftPDAs.deriveSpotMarketVaultAccount(driftAccounts, marketIndex);
        add(spotMarketVaultKey.publicKey());
      }
      for (final var perpPosition : user.perpPositions()) {
        final var perpMarket = DriftPDAs.derivePerpMarketAccount(driftAccounts, perpPosition.marketIndex());
        add(perpMarket.publicKey());
      }
    }
  }

  @Override
  public void addDriftAccounts(final List<AccountInfo<byte[]>> accountsNeeded) {
    final var glamAccounts = stateAccountClient.accountClient().glamAccounts();
    add(glamAccounts.readDriftIntegrationAuthority().publicKey());

    final var driftTables = mapDriftTables(accountsNeeded, driftAccounts);
    for (final var table : driftTables) {
      this.driftLookupTables.put(table.address(), table);
    }

    add(driftAccounts.driftProgram());
    add(driftAccounts.stateKey());

    final var glamVaultKey = stateAccountClient.accountClient().owner();
    final var userStats = DriftPDAs.deriveUserStatsAccount(this.driftAccounts, glamVaultKey);
    add(userStats.publicKey());

    addDriftUsers(accountsNeeded, this.driftAccounts);
  }

  @Override
  public void addDriftVaultAccounts(final List<AccountInfo<byte[]>> accountsNeeded) {
    final var glamAccounts = stateAccountClient.accountClient().glamAccounts();
    add(glamAccounts.readDriftIntegrationAuthority().publicKey());
    final var driftVaultsProgram = this.driftAccounts.driftVaultsProgram();
    add(driftVaultsProgram);
    for (final var accountInfo : accountsNeeded) {
      if (accountInfo == null
          || !accountInfo.owner().equals(driftVaultsProgram)
          || accountInfo.data().length != VaultDepositor.BYTES
          || !VaultDepositor.DISCRIMINATOR.equals(accountInfo.data(), 0)) {
        continue;
      }
      final var depositor = VaultDepositor.read(accountInfo);
      add(depositor._address());
      final var driftVaultKey = depositor.vault();
      add(driftVaultKey);
      addSecondPhase(driftVaultKey);
      final var user = DriftPDAs.deriveUserAccount(this.driftAccounts, driftVaultKey, 0);
      addSecondPhase(user.publicKey());
    }
  }

  @Override
  public void addDriftVaultAccountsSecondPhase(final List<AccountInfo<byte[]>> accountsNeeded) {
    final var driftVaultsProgram = this.driftAccounts.driftVaultsProgram();
    for (final var accountInfo : accountsNeeded) {
      if (accountInfo == null
          || !accountInfo.owner().equals(driftVaultsProgram)
          || accountInfo.data().length != Vault.BYTES
          || !Vault.DISCRIMINATOR.equals(accountInfo.data(), 0)) {
        continue;
      }
      final var driftVault = Vault.read(accountInfo);
      add(driftVault.userStats());
      add(driftVault.user());
      add(driftVault.tokenAccount());
    }
    addDriftUsers(accountsNeeded, this.driftAccounts);
  }

  @Override
  public void addJupiterSwapAccounts(final List<AccountInfo<byte[]>> accountsNeeded) {
    add(this.jupiterAccounts.swapProgram());
    add(this.jupiterAccounts.aggregatorEventAuthority());
  }

  private void addKFarmAccounts() {
    // add(kaminoAccounts.farmProgram()); // In Kamino LUT
    add(this.kaminoAccounts.farmsGlobalConfig());
    // TODO Obligation Farm User State
    // TODO Reserve Farm State
  }

  @Override
  public void removeKaminoLendTableAccounts() {
    removeAccounts(kaminoLookupTables);
  }

  @Override
  public void addKaminoLendAccounts(final List<AccountInfo<byte[]>> accountsNeeded) {
    final var glamAccounts = stateAccountClient.accountClient().glamAccounts();
    add(glamAccounts.readKaminoIntegrationAuthority().publicKey());
    addKFarmAccounts();

    final var mainMarketTable = mapTable(accountsNeeded, this.kaminoAccounts.mainMarketLUT());
    kaminoLookupTables.put(mainMarketTable.address(), mainMarketTable);

    final var kLendProgram = this.kaminoAccounts.kLendProgram();

    for (final var accountInfo : accountsNeeded) {
      if (accountInfo == null
          || !accountInfo.owner().equals(kLendProgram)
          || accountInfo.data().length != Obligation.BYTES
          || !Obligation.DISCRIMINATOR.equals(accountInfo.data(), 0)) {
        continue;
      }
      final var obligation = Obligation.read(accountInfo);
      glamVaultKaminoObligations.put(obligation._address(), obligation);
      add(obligation._address());
      final var market = obligation.lendingMarket();
      add(market);
      final var marketAuthority = KaminoAccounts.lendingMarketAuthPda(market, kLendProgram).publicKey();
      add(marketAuthority);
      for (final var deposit : obligation.deposits()) {
        addSecondPhase(deposit.depositReserve());
      }
      for (final var borrow : obligation.borrows()) {
        addSecondPhase(borrow.borrowReserve());
      }
    }
  }

  @Override
  public void addKaminoAccountsSecondPhase(final List<AccountInfo<byte[]>> accountsNeeded) {
    final var solanaAccounts = stateAccountClient.accountClient().solanaAccounts();
    add(solanaAccounts.instructionsSysVar());

    final var reserveKeys = glamVaultKaminoObligations.values().stream().mapMulti((obligation, downstream) -> {
      for (final var deposit : obligation.deposits()) {
        downstream.accept(deposit.depositReserve());
      }
      for (final var borrow : obligation.borrows()) {
        downstream.accept(borrow.borrowReserve());
      }
    }).collect(Collectors.toUnmodifiableSet());

    for (final var accountInfo : accountsNeeded) {
      if (accountInfo != null && reserveKeys.contains(accountInfo.pubKey())) {
        final var reserve = Reserve.read(accountInfo);
        add(reserve._address());
        add(reserve.liquidity().mintPubkey());
        final var reserveCollateral = reserve.collateral();
        add(reserveCollateral.supplyVault());
        add(reserveCollateral.mintPubkey());
      }
    }
  }

  @Override
  public void removeKaminoVaultTableAccounts() {
    removeAccounts(kaminoVaultLookupTables);
  }

  @Override
  public void addKaminoVaultAccounts(final List<AccountInfo<byte[]>> accountsNeeded,
                                     final Map<PublicKey, VaultState> vaultStatesByMint) {
    final var accountClient = stateAccountClient.accountClient();
    final var glamAccounts = accountClient.glamAccounts();
    add(glamAccounts.readKaminoIntegrationAuthority().publicKey());
    final var kVaultProgram = this.kaminoAccounts.kVaultsProgram();
    add(kVaultProgram);
    add(this.kaminoAccounts.kVaultsEventAuthority());

    addKFarmAccounts();

    final var solanaAccounts = accountClient.solanaAccounts();
    final var tokenProgram = solanaAccounts.tokenProgram();
    final var token2022Program = solanaAccounts.token2022Program();

    for (final var accountInfo : accountsNeeded) {
      if (accountInfo == null) {
        continue;
      }
      final var program = accountInfo.owner();
      if (!program.equals(tokenProgram) && !program.equals(token2022Program)) {
        continue;
      }
      final var tokenAccount = TokenAccount.read(accountInfo.pubKey(), accountInfo.data());
      final var vaultState = vaultStatesByMint.get(tokenAccount.mint());
      if (vaultState != null) {
        kaminoVaults.put(tokenAccount.address(), vaultState);

        for (final var allocation : vaultState.vaultAllocationStrategy()) {
          addSecondPhase(allocation.reserve());
        }

        add(tokenAccount.address());
        final var tokenMint = vaultState.tokenMint();
        final var sharesMint = vaultState.sharesMint();
        final var userTokenAta = accountClient.findATA(tokenProgram, tokenMint).publicKey();
        add(userTokenAta);
        add(vaultState._address());
        add(vaultState.tokenVault());
        add(tokenMint);
        add(sharesMint);
        add(vaultState.baseVaultAuthority());

        final var vaultTableKey = vaultState.vaultLookupTable();
        addSecondPhase(vaultTableKey);
      }
    }
  }

  @Override
  public void addKaminoVaultAccountsSecondPhase(final List<AccountInfo<byte[]>> accountsNeeded) {
    final var kLendProgram = this.kaminoAccounts.kLendProgram();
    final var hubbleScopeFeedAccounts = this.kaminoAccounts.scopeMainnetHubbleFeed();
    final var kaminoScopeFeedAccounts = this.kaminoAccounts.scopeMainnetKLendFeed();

    final var scopeFeeds = Map.of(
        hubbleScopeFeedAccounts.oraclePrices(), this.kaminoAccounts.scopeMainnetHubbleFeed(),
        kaminoScopeFeedAccounts.oraclePrices(), this.kaminoAccounts.scopeMainnetKLendFeed()
    );

    final var reserves = accountsNeeded.stream().<Reserve>mapMulti((accountInfo, downstream) -> {
      if (accountInfo != null && accountInfo.owner().equals(kLendProgram)) {
        final byte[] data = accountInfo.data();
        if (data.length == Reserve.BYTES && Reserve.DISCRIMINATOR.equals(data, 0)) {
          downstream.accept(Reserve.read(accountInfo));
        }
      }
    }).collect(Collectors.toUnmodifiableMap(Reserve::_address, r -> r));

    for (final var vaultState : kaminoVaults.values()) {
      for (final var allocation : vaultState.vaultAllocationStrategy()) {
        final var reserve = reserves.get(allocation.reserve());
        if (reserve != null) {
          final var kVaultTable = mapTable(accountsNeeded, vaultState.vaultLookupTable());
          if (kVaultTable != null) {
            kaminoVaultLookupTables.put(vaultState._address(), kVaultTable);
          }

          final var scopeConfiguration = reserve.config().tokenInfo().scopeConfiguration();
          final var scopeFeed = scopeFeeds.get(scopeConfiguration.priceFeed());
          add(reserve._address());
          add(reserve.lendingMarket());
          if (scopeFeed != null) {
            add(scopeFeed.oraclePrices());
            add(scopeFeed.oracleMappings());
          }
        }
      }
    }
  }

  static void main(final String[] args) {
    final var rpcEndpoint = args.length > 0 ? URI.create(args[0]) : SolanaNetwork.MAIN_NET.getEndpoint();

    final Signer signer = null;
    final var feePayer = signer.publicKey();
    final var glamStateKey = PublicKey.fromBase58Encoded("");

    try (final var httpClient = HttpClient.newHttpClient()) {
      final var rpcClient = SolanaRpcClient.build()
          .httpClient(httpClient)
          .endpoint(rpcEndpoint)
          .createClient();

      final var vaultTableBuilderBuilder = VaultTableBuilder.build();
      // These Kamino VaultState's can be re-used if managing multiple GLAM vault's.
      // But still would need to be refreshed periodically.
      final var kaminoVaultsFuture = vaultTableBuilderBuilder.kaminoAccounts().fetchVaults(rpcClient);

      final var stateAccountInfoFuture = rpcClient.getAccountInfo(glamStateKey);

      final var glamAccountClient = GlamAccountClient.createClient(feePayer, glamStateKey);

      final var stateAccount = StateAccount.read(stateAccountInfoFuture.join());
      final var stateAccountClient = StateAccountClient.createClient(stateAccount, glamAccountClient);
      final var vaultTableBuilder = vaultTableBuilderBuilder.create(stateAccountClient);
      var accountsNeededFuture = vaultTableBuilder.fetchAccountsNeeded(rpcClient);

      final var glamVaultTablesFuture = vaultTableBuilder.fetchGlamVaultTables(rpcClient);

      final var kaminoVaults = kaminoVaultsFuture.join();
      final var kVaultsByMint = VaultTableBuilder.mapKaminoVaultStatesByMint(kaminoVaults);

      final var accountsNeeded = accountsNeededFuture.join();
      vaultTableBuilder.addAccounts(accountsNeeded, kVaultsByMint);

      accountsNeededFuture = vaultTableBuilder.fetchSecondPhaseAccountsNeeded(rpcClient);
      vaultTableBuilder.addAccountsSecondPhase(accountsNeededFuture.join());

      vaultTableBuilder.removeExternalProtocolTableAccounts();

      final var glamVaultTables = glamVaultTablesFuture.join();
      final var tableTasks = vaultTableBuilder.batchTableTasks(glamVaultTables);


      // Execute Create & Extend Table Instructions
      final var computeBudgetProgram = glamAccountClient.solanaAccounts().invokedComputeBudgetProgram();
      final var simulationCUInstructions = List.of(
          ComputeBudgetProgram.setComputeUnitLimit(computeBudgetProgram, MAX_COMPUTE_BUDGET),
          ComputeBudgetProgram.setComputeUnitPrice(computeBudgetProgram, 0)
      );

      long recentSlot = -1;
      final var taskIterator = tableTasks.iterator();
      for (var tableTask = taskIterator.next(); ; ) {
        final var simulationTx = Transaction.createTx(glamAccountClient.feePayer(), simulationCUInstructions);
        if (tableTask.needsSlot() && recentSlot < 0) {
          recentSlot = rpcClient.getSlot(CONFIRMED).join();
        }
        final var instructions = tableTask.instructions(recentSlot);
        simulationTx.appendInstructions(instructions);
        var base64EncodedTx = simulationTx.base64EncodeToString();
        final var simulationResponse = rpcClient.simulateTransaction(CONFIRMED, base64EncodedTx, true).join();
        if (simulationResponse.error() != null) {
          // TODO: handle error and retry.
          recentSlot = -1;
          continue;
        }

        final int cuBudget = simulationResponse
            .unitsConsumed()
            .orElseThrow(() -> new IllegalStateException("RPC server did not provide a CU budget:" + simulationResponse));
        // Typically fetch from Helius.
        final long cuPrice = 12345;

        final var transaction = Transaction.createTx(
            glamAccountClient.feePayer(),
            List.of(
                ComputeBudgetProgram.setComputeUnitLimit(computeBudgetProgram, cuBudget),
                ComputeBudgetProgram.setComputeUnitPrice(computeBudgetProgram, cuPrice)
            )
        );
        transaction.appendInstructions(instructions);
        transaction.setRecentBlockHash(simulationResponse.replacementBlockHash().blockhash());
        transaction.sign(signer);
        base64EncodedTx = transaction.base64EncodeToString();
        final var txId = rpcClient.sendTransaction(base64EncodedTx).join();
        System.out.println("Sent transaction: " + txId);
        // TODO: Confirmation logic.

        if (!taskIterator.hasNext()) {
          break;
        }
        recentSlot = simulationResponse.context().slot();
        tableTask = taskIterator.next();
      }
    }
  }
}
