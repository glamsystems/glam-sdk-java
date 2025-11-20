package systems.glam.sdk.lut;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.lookup.AddressLookupTable;
import software.sava.core.accounts.token.TokenAccount;
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
import systems.glam.sdk.GlamAccountClient;
import systems.glam.sdk.StateAccountClient;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateAccount;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

record VaultTableBuilderImpl(StateAccountClient stateAccountClient,
                             List<PublicKey> tablePrefix,
                             Set<PublicKey> accountsNeeded,
                             Set<PublicKey> secondPhaseAccountsNeeded,
                             Set<PublicKey> glamVaultTableAccounts,
                             DriftAccounts driftAccounts,
                             Map<PublicKey, VaultDepositor> driftVaultDepositors,
                             JupiterAccounts jupiterAccounts,
                             KaminoAccounts kaminoAccounts,
                             Map<PublicKey, Obligation> glamVaultKaminoObligations,
                             Map<PublicKey, VaultState> kaminoVaults) implements VaultTableBuilder {

  private void add(final PublicKey key) {
    glamVaultTableAccounts.add(key);
  }

  private void addIfAbsent(final PublicKey key, final AddressLookupTable table) {
    if (!table.containKey(key)) {
      add(key);
    }
  }

  private void addIfAbsent(final PublicKey key, final Collection<AddressLookupTable> tables) {
    for (final var table : tables) {
      addIfAbsent(key, table);
    }
  }

  private AddressLookupTable mapTable(final List<AccountInfo<byte[]>> accounts, final PublicKey tableKey) {
    return accounts.stream()
        .filter(accountInfo -> accountInfo != null && accountInfo.pubKey().equals(tableKey))
        .findFirst()
        .map(accountInfo -> AddressLookupTable.read(accountInfo.pubKey(), accountInfo.data()))
        .orElse(null);
  }

  public void createTable() {
    tablePrefix.forEach(glamVaultTableAccounts::remove);
    // TODO
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
    final var baseAssetMint = stateAccount.baseAssetMint();
    if (baseAssetMint != null && !PublicKey.NONE.equals(baseAssetMint)) {
      add(baseAssetMint);
    }
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

  private void addDriftUsers(final List<AccountInfo<byte[]>> accountsNeeded,
                             final DriftAccounts driftAccounts,
                             final List<AddressLookupTable> driftTables) {
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
        final var spotMarketKey = DriftPDAs.deriveSpotMarketAccount(driftAccounts, marketIndex).publicKey();
        addIfAbsent(spotMarketKey, driftTables);
        final var spotMarketVaultKey = DriftPDAs.deriveSpotMarketVaultAccount(driftAccounts, marketIndex).publicKey();
        addIfAbsent(spotMarketVaultKey, driftTables);
      }
      for (final var perpPosition : user.perpPositions()) {
        final var perpMarket = DriftPDAs.derivePerpMarketAccount(driftAccounts, perpPosition.marketIndex()).publicKey();
        addIfAbsent(perpMarket, driftTables);
      }
    }
  }

  @Override
  public void addDriftAccounts(final List<AccountInfo<byte[]>> accountsNeeded) {
    final var glamAccounts = stateAccountClient.accountClient().glamAccounts();
    add(glamAccounts.readDriftIntegrationAuthority().publicKey());
    final var driftProgram = driftAccounts.driftProgram();

    final var driftTables = mapDriftTables(accountsNeeded, driftAccounts);

    addIfAbsent(driftProgram, driftTables);
    addIfAbsent(driftAccounts.stateKey(), driftTables);

    final var glamVaultKey = stateAccountClient.accountClient().owner();
    final var userStats = DriftPDAs.deriveUserStatsAccount(this.driftAccounts, glamVaultKey).publicKey();
    add(userStats);

    addDriftUsers(accountsNeeded, this.driftAccounts, driftTables);
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
      secondPhaseAccountsNeeded.add(driftVaultKey);
      final var user = DriftPDAs.deriveUserAccount(this.driftAccounts, driftVaultKey, 0);
      secondPhaseAccountsNeeded.add(user.publicKey());
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
    final var driftTables = mapDriftTables(accountsNeeded, this.driftAccounts);
    addDriftUsers(accountsNeeded, this.driftAccounts, driftTables);
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
  public void addKaminoLendAccounts(final List<AccountInfo<byte[]>> accountsNeeded) {
    final var glamAccounts = stateAccountClient.accountClient().glamAccounts();
    add(glamAccounts.readKaminoIntegrationAuthority().publicKey());
    addKFarmAccounts();

    final var mainMarketTable = mapTable(accountsNeeded, this.kaminoAccounts.mainMarketLUT());

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
      addIfAbsent(market, mainMarketTable);
      final var marketAuthority = KaminoAccounts.lendingMarketAuthPda(market, kLendProgram).publicKey();
      addIfAbsent(marketAuthority, mainMarketTable);
      for (final var deposit : obligation.deposits()) {
        secondPhaseAccountsNeeded.add(deposit.depositReserve());
      }
      for (final var borrow : obligation.borrows()) {
        secondPhaseAccountsNeeded.add(borrow.borrowReserve());
      }
    }
  }

  @Override
  public void addKaminoAccountsSecondPhase(final List<AccountInfo<byte[]>> accountsNeeded) {
    final var mainMarketTable = mapTable(accountsNeeded, this.kaminoAccounts.mainMarketLUT());

    final var solanaAccounts = stateAccountClient.accountClient().solanaAccounts();
    addIfAbsent(solanaAccounts.instructionsSysVar(), mainMarketTable);

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
        addIfAbsent(reserve._address(), mainMarketTable);
        addIfAbsent(reserve.liquidity().mintPubkey(), mainMarketTable);
        final var reserveCollateral = reserve.collateral();
        addIfAbsent(reserveCollateral.supplyVault(), mainMarketTable);
        addIfAbsent(reserveCollateral.mintPubkey(), mainMarketTable);
      }
    }
  }

  @Override
  public void addKaminoVaultAccounts(final List<AccountInfo<byte[]>> accountsNeeded,
                                     final Map<PublicKey, VaultState> vaultStatesByMint,
                                     final boolean ignoreKVaultTable) {
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
          final var reserveKey = allocation.reserve();
          if (!reserveKey.equals(PublicKey.NONE)) {
            secondPhaseAccountsNeeded.add(reserveKey);
          }
        }

        add(tokenAccount.address()); // User Share ATA.
        final var tokenMint = vaultState.tokenMint();
        final var sharesMint = vaultState.sharesMint();
        final var userTokenAta = accountClient.findATA(tokenProgram, tokenMint).publicKey();
        add(userTokenAta);

        if (ignoreKVaultTable) {
          add(vaultState._address());
          add(vaultState.tokenVault());
          add(tokenMint);
          add(sharesMint);
          add(vaultState.baseVaultAuthority());
        } else {
          secondPhaseAccountsNeeded.add(vaultState.vaultLookupTable());
        }
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

          final var scopeConfiguration = reserve.config().tokenInfo().scopeConfiguration();
          final var scopeFeed = scopeFeeds.get(scopeConfiguration.priceFeed());

          if (kVaultTable != null) {
            addIfAbsent(vaultState._address(), kVaultTable);
            addIfAbsent(vaultState.tokenVault(), kVaultTable);
            addIfAbsent(vaultState.tokenMint(), kVaultTable);
            addIfAbsent(vaultState.sharesMint(), kVaultTable);
            addIfAbsent(vaultState.baseVaultAuthority(), kVaultTable);

            addIfAbsent(reserve._address(), kVaultTable);
            addIfAbsent(reserve.lendingMarket(), kVaultTable);

            if (scopeFeed != null) {
              addIfAbsent(scopeFeed.oraclePrices(), kVaultTable);
              addIfAbsent(scopeFeed.oracleMappings(), kVaultTable);
            }
          } else {
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
  }

  static void main(final String[] args) {
    final var feePayer = PublicKey.fromBase58Encoded("");
    final var glamStateKey = PublicKey.fromBase58Encoded("");

    final var rpcEndpoint = args.length > 0 ? URI.create(args[0]) : SolanaNetwork.MAIN_NET.getEndpoint();
    try (final var httpClient = HttpClient.newHttpClient()) {
      final var rpcClient = SolanaRpcClient.build()
          .httpClient(httpClient)
          .endpoint(rpcEndpoint)
          .createClient();
      // These Kamino VaultState's can be re-used if managing multiple GLAM vault's.
      // But still would need to be refreshed periodically.
      final var kaminoVaultsFuture = VaultTableBuilder.fetchKaminoVaultStates(rpcClient);

      final var stateAccountInfoFuture = rpcClient.getAccountInfo(glamStateKey);

      final var glamAccountClient = GlamAccountClient.createClient(feePayer, glamStateKey);

      final var stateAccount = StateAccount.read(stateAccountInfoFuture.join());
      final var stateAccountClient = StateAccountClient.createClient(stateAccount, glamAccountClient);
      final var glamStateAccountClient = VaultTableBuilder.build().create(stateAccountClient);
      var accountsNeededFuture = glamStateAccountClient.fetchAccountsNeeded(rpcClient);

      final var kaminoVaults = kaminoVaultsFuture.join();
      final var kVaultsByMint = VaultTableBuilder.mapKaminoVaultStatesByMint(kaminoVaults);

      final var accountsNeeded = accountsNeededFuture.join();
      glamStateAccountClient.addAccounts(accountsNeeded, kVaultsByMint);

      accountsNeededFuture = glamStateAccountClient.fetchSecondPhaseAccountsNeeded(rpcClient);
      glamStateAccountClient.addAccountsSecondPhase(accountsNeededFuture.join());

      // TODO: Create Table Instructions.
    }
  }
}
