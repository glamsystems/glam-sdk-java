package systems.glam.sdk.lut;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.lookup.AddressLookupTable;
import software.sava.core.encoding.ByteUtil;
import software.sava.core.rpc.Filter;
import software.sava.idl.clients.jupiter.JupiterAccounts;
import software.sava.idl.clients.kamino.KaminoAccounts;
import software.sava.idl.clients.kamino.lend.gen.types.Obligation;
import software.sava.idl.clients.kamino.lend.gen.types.ObligationCollateral;
import software.sava.idl.clients.kamino.lend.gen.types.ObligationLiquidity;
import software.sava.idl.clients.kamino.lend.gen.types.Reserve;
import software.sava.idl.clients.kamino.lend.gen.types.ReserveConfig;
import software.sava.idl.clients.kamino.lend.gen.types.ScopeConfiguration;
import software.sava.idl.clients.kamino.lend.gen.types.TokenInfo;
import software.sava.idl.clients.marinade.stake_pool.MarinadeAccounts;
import software.sava.idl.clients.meteora.MeteoraAccounts;
import software.sava.idl.clients.spl.lut.gen.AddressLookupTablePDAs;
import systems.glam.sdk.GlamAccounts;
import systems.glam.sdk.StateAccountClient;
import systems.glam.sdk.idl.programs.glam.kamino.gen.ExtKaminoConstants;
import systems.glam.sdk.idl.programs.glam.protocol.gen.GlamProtocolConstants;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.*;

import java.util.*;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;
import static software.sava.core.accounts.lookup.AddressLookupTable.LOOKUP_TABLE_MAX_ADDRESSES;
import static software.sava.core.accounts.lookup.AddressLookupTable.LOOKUP_TABLE_META_SIZE;

final class VaultTableBuilderTests {

  private static final PublicKey FEE_PAYER = fromBase58Encoded("F1oQY1jbdiJyxxeeuMBF2NsUckboyWo6TSXNqzJbrhxs");
  private static final PublicKey STATE_KEY = fromBase58Encoded("9fkan2jCsS7Xq3fLqgxgZT5pDCbj2MhQ5MAoEKSHrcAT");

  private static PublicKey key(final int id) {
    final byte[] bytes = new byte[PublicKey.PUBLIC_KEY_LENGTH];
    bytes[0] = (byte) (id >> 8);
    bytes[1] = (byte) id;
    bytes[31] = 7;
    return PublicKey.createPubKey(bytes);
  }

  private static List<PublicKey> keys(final int from, final int count) {
    final var list = new ArrayList<PublicKey>(count);
    for (int i = 0; i < count; ++i) {
      list.add(key(from + i));
    }
    return list;
  }

  private static AddressLookupTable table(final PublicKey address, final List<PublicKey> accounts) {
    final byte[] data = new byte[LOOKUP_TABLE_META_SIZE + accounts.size() * PublicKey.PUBLIC_KEY_LENGTH];
    int offset = LOOKUP_TABLE_META_SIZE;
    for (final var account : accounts) {
      offset += account.write(data, offset);
    }
    return AddressLookupTable.read(address, data);
  }

  private static StateAccountClient stateAccountClient() {
    return stateAccountClient(key(9003));
  }

  private static StateAccountClient stateAccountClient(final PublicKey baseAssetMint,
                                                       final IntegrationAcl... integrationAcls) {
    return StateAccountClient.createClient(stateAccount(baseAssetMint, integrationAcls), FEE_PAYER);
  }

  private static StateAccount stateAccount(final PublicKey baseAssetMint,
                                           final IntegrationAcl... integrationAcls) {
    final var name = Arrays.copyOf("Table Vault".getBytes(US_ASCII), StateAccount.NAME_LEN);
    return new StateAccount(
        STATE_KEY, StateAccount.DISCRIMINATOR, AccountType.TokenizedVault, true,
        key(9001), key(9002),
        new byte[StateAccount.PORTFOLIO_MANAGER_NAME_LEN],
        new CreatedModel(new byte[8], FEE_PAYER, 1_650_000_000L),
        baseAssetMint, 9, 0,
        name,
        0L, 0L,
        GlamAccounts.MAIN_NET.mintPDA(STATE_KEY, 0).publicKey(),
        new PublicKey[0],
        integrationAcls,
        new DelegateAcl[0],
        new PublicKey[0],
        new PricedProtocol[0],
        new EngineField[0][]
    );
  }

  private static final List<PublicKey> TABLE_PREFIX = List.of(key(9101), key(9102), key(9103));

  private static VaultTableBuilderImpl builder(final Collection<PublicKey> tableAccounts) {
    // LinkedHashSet: batchTableTasks chunks in iteration order, which the
    // assertions below depend on
    return new VaultTableBuilderImpl(
        stateAccountClient(),
        TABLE_PREFIX,
        new LinkedHashSet<>(),
        new LinkedHashSet<>(),
        new LinkedHashSet<>(tableAccounts),
        JupiterAccounts.MAIN_NET,
        KaminoAccounts.MAIN_NET,
        Map.of(), Map.of(), Map.of(), Map.of(),
        MarinadeAccounts.MAIN_NET,
        MeteoraAccounts.MAIN_NET
    );
  }

  @Test
  void alreadyCoveredAccountsProduceNoTasks() {
    final var covered = keys(1, 4);
    final var withPrefix = new ArrayList<>(covered);
    withPrefix.addAll(TABLE_PREFIX);
    final var builder = builder(withPrefix);
    // prefix keys are implicit, table keys are already available
    final var tasks = builder.batchTableTasks(List.of(table(key(500), covered)));
    assertEquals(List.of(), tasks);
  }

  @Test
  void createsOneTableForASmallAccountSet() {
    final var accounts = keys(1, 5);
    final var builder = builder(accounts);
    final var tasks = builder.batchTableTasks(List.of());

    assertEquals(1, tasks.size());
    final var task = tasks.getFirst();
    assertInstanceOf(CreateTable.class, task);
    assertTrue(task.needsSlot());
    // the table key is derived from the recent slot, so unknown until then
    assertNull(task.tableKey());

    final long recentSlot = 250_000_000L;
    final var instructions = task.instructions(recentSlot);
    assertEquals(2, instructions.size());

    final var accountClient = ((CreateTable) task).accountClient;
    final var solanaAccounts = accountClient.solanaAccounts();
    final var expectedPDA = AddressLookupTablePDAs.addressLookupTablePDA(
        solanaAccounts.addressLookupTableProgram(), FEE_PAYER, recentSlot
    );
    assertEquals(expectedPDA.publicKey(), task.tableKey());

    final var createIx = instructions.getFirst();
    assertEquals(solanaAccounts.addressLookupTableProgram(), createIx.programId().publicKey());
    final var extendIx = instructions.getLast();
    assertEquals(solanaAccounts.addressLookupTableProgram(), extendIx.programId().publicKey());
    // the created table starts with the prefix, then the new accounts
    final var extendedKeys = extendIx.accounts();
    assertEquals(expectedPDA.publicKey(), extendedKeys.getFirst().publicKey());
  }

  @Test
  void chunksALargeSetIntoCreateThenDynamicExtends() {
    // 24 fit alongside the create (27 minus the 3 prefix keys); the remaining
    // 36 extend the same pending table in chunks of 30
    final var accounts = keys(1, 60);
    final var builder = builder(accounts);
    final var tasks = builder.batchTableTasks(List.of());

    assertEquals(3, tasks.size());
    final var createTask = assertInstanceOf(CreateTable.class, tasks.get(0));
    final var extendA = assertInstanceOf(DynamicExtendTable.class, tasks.get(1));
    final var extendB = assertInstanceOf(DynamicExtendTable.class, tasks.get(2));
    assertFalse(extendA.needsSlot());

    assertEquals(TABLE_PREFIX.size() + 24, createTask.accounts.size());
    assertEquals(30, extendA.accounts.size());
    assertEquals(6, extendB.accounts.size());

    // chunks partition the account set in order with no overlap
    final var chunked = new ArrayList<PublicKey>();
    chunked.addAll(createTask.accounts.subList(TABLE_PREFIX.size(), createTask.accounts.size()));
    chunked.addAll(extendA.accounts);
    chunked.addAll(extendB.accounts);
    assertEquals(accounts, chunked);

    // the dynamic extends follow the pending create's table key
    createTask.instructions(250_000_000L);
    assertEquals(createTask.tableKey(), extendA.tableKey());
    assertEquals(createTask.tableKey(), extendB.tableKey());
    final var extendIxs = extendA.instructions(0L);
    assertEquals(1, extendIxs.size());
    assertEquals(createTask.tableKey(), extendIxs.getFirst().accounts().getFirst().publicKey());
  }

  @Test
  void fillsExistingTableSpaceBeforeCreating() {
    final var existingKey = key(600);
    // 250 entries: 6 slots of space remain
    final var existing = table(existingKey, keys(2000, 250));
    final var accounts = keys(1, 10);
    final var builder = builder(accounts);

    final var tasks = builder.batchTableTasks(List.of(existing));
    assertEquals(2, tasks.size());

    final var extendTask = assertInstanceOf(ExtendTable.class, tasks.get(0));
    assertEquals(existingKey, extendTask.tableKey());
    assertFalse(extendTask.needsSlot());
    final var extendIx = extendTask.instructions(0L).getFirst();
    // 6 accounts into the existing table; table key leads the ix accounts
    assertEquals(existingKey, extendIx.accounts().getFirst().publicKey());

    final var createTask = assertInstanceOf(CreateTable.class, tasks.get(1));
    // the remaining 4, plus the prefix seeded into every new table
    assertEquals(TABLE_PREFIX.size() + 4, createTask.accounts.size());
    assertEquals(accounts.subList(6, 10), createTask.accounts.subList(TABLE_PREFIX.size(), createTask.accounts.size()));
  }

  @Test
  void fullTablesAreNotExtended() {
    final var full = table(key(600), keys(2000, LOOKUP_TABLE_MAX_ADDRESSES));
    final var accounts = keys(1, 3);
    final var builder = builder(accounts);

    final var tasks = builder.batchTableTasks(List.of(full));
    assertEquals(1, tasks.size());
    assertInstanceOf(CreateTable.class, tasks.getFirst());
  }

  @Test
  void mostPopulatedTableIsFilledFirst() {
    final var bigKey = key(600);
    final var smallKey = key(601);
    final var big = table(bigKey, keys(2000, 254));    // 2 free
    final var small = table(smallKey, keys(3000, 100)); // 156 free
    final var accounts = keys(1, 5);
    final var builder = builder(accounts);

    final var tasks = builder.batchTableTasks(List.of(small, big));
    assertEquals(2, tasks.size());
    assertEquals(bigKey, tasks.get(0).tableKey());
    assertEquals(smallKey, tasks.get(1).tableKey());
  }

  @Test
  void builderSeedsAccountsNeededFromState() {
    final var vaultTableBuilder = VaultTableBuilder.build()
        .create(stateAccountClient());
    final var accountsNeeded = vaultTableBuilder.accountsNeeded();
    // assets and the base asset mint are needed to derive token accounts
    assertTrue(accountsNeeded.contains(key(9003)));
    // no kamino ACLs: the kamino market table is not fetched
    assertFalse(accountsNeeded.contains(KaminoAccounts.MAIN_NET.mainMarketLUT()));
    assertSame(stateAccountClient().getClass(), vaultTableBuilder.stateAccountClient().getClass());
  }

  @Test
  void builderSkipsAbsentBaseAssetMint() {
    final var vaultTableBuilder = VaultTableBuilder.build()
        .create(stateAccountClient(PublicKey.NONE));
    assertFalse(vaultTableBuilder.accountsNeeded().contains(PublicKey.NONE));
  }

  @Test
  void builderFetchesKaminoMarketTableWhenEnabled() {
    final var kaminoAcl = new IntegrationAcl(
        GlamAccounts.MAIN_NET.kaminoIntegrationProgram(),
        systems.glam.sdk.Protocol.KAMINO_LENDING.protocolBitFlag(),
        new ProtocolPolicy[0]
    );
    final var vaultTableBuilder = VaultTableBuilder.build()
        .create(stateAccountClient(key(9003), kaminoAcl));
    assertTrue(vaultTableBuilder.accountsNeeded().contains(KaminoAccounts.MAIN_NET.mainMarketLUT()));
  }

  @Test
  void builderAccessorsRoundTrip() {
    final var builder = VaultTableBuilder.build();
    assertSame(JupiterAccounts.MAIN_NET, builder.jupiterAccounts());
    assertSame(KaminoAccounts.MAIN_NET, builder.kaminoAccounts());
    assertSame(MarinadeAccounts.MAIN_NET, builder.marinadeAccounts());
    assertSame(MeteoraAccounts.MAIN_NET, builder.meteorAccounts());
    // the fluent setters hand back the builder
    assertSame(builder, builder.jupiterAccounts(JupiterAccounts.MAIN_NET));
    assertSame(builder, builder.kaminoAccounts(KaminoAccounts.MAIN_NET));
    assertSame(builder, builder.marinadeAccounts(MarinadeAccounts.MAIN_NET));
    assertSame(builder, builder.meteorAccounts(MeteoraAccounts.MAIN_NET));
  }

  // --- kamino account-collection phases, against the mainnet snapshots shared
  // --- from the services suite (see resources/accounts/kamino/README.md)

  private static final PublicKey VAULT_STATE_KEY = fromBase58Encoded("5YxwKgsvyTdT8q2CBgwA4L9BKbnKNrB66K9wUzij5wH");
  private static final PublicKey SOL_RESERVE_KEY = fromBase58Encoded("d4A2prbA2whesmvHaL88BH6Ewn5N4bTSU2Ze8P6Bc4Q");

  private static byte[] readResource(final String name) {
    try (final var in = new java.util.zip.GZIPInputStream(
        Objects.requireNonNull(VaultTableBuilderTests.class.getResourceAsStream("/" + name), name))) {
      return in.readAllBytes();
    } catch (final java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  private static software.sava.rpc.json.http.response.AccountInfo<byte[]> accountInfo(
      final PublicKey key, final PublicKey owner, final byte[] data) {
    return new software.sava.rpc.json.http.response.AccountInfo<>(
        key, new software.sava.rpc.json.http.response.Context(1L, null), false, 0,
        owner, java.math.BigInteger.ZERO, 0, data
    );
  }

  /// The batching helper above uses immutable maps; the collection phases
  /// write into theirs, so they get their own builder.
  private static VaultTableBuilderImpl kaminoBuilder() {
    return new VaultTableBuilderImpl(
        stateAccountClient(),
        TABLE_PREFIX,
        new LinkedHashSet<>(),
        new LinkedHashSet<>(),
        new LinkedHashSet<>(),
        JupiterAccounts.MAIN_NET,
        KaminoAccounts.MAIN_NET,
        new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
        MarinadeAccounts.MAIN_NET,
        MeteoraAccounts.MAIN_NET
    );
  }

  @Test
  void glamVaultAccountsCoverTheProtocolAndMintSurface() {
    final var builder = kaminoBuilder();
    final var client = builder.stateAccountClient();
    final var accountClient = client.accountClient();
    builder.addGlamVaultAccounts(List.of());

    final var needed = builder.accountsNeeded();
    final var solanaAccounts = accountClient.solanaAccounts();
    final var glamAccounts = accountClient.glamAccounts();
    // the system program IS the all-zero key the sentinel filter drops, so
    // the impl adds it directly; a filtered add here silently loses it
    assertTrue(needed.contains(solanaAccounts.systemProgram()));
    assertTrue(needed.contains(glamAccounts.protocolProgram()));
    assertTrue(needed.contains(glamAccounts.readSplIntegrationAuthority().publicKey()));
    assertTrue(needed.contains(client.baseAssetMint()));
    // the fixture state is a tokenized vault: the whole mint surface rides too
    final var mintKey = client.mint();
    assertTrue(needed.contains(glamAccounts.readMintIntegrationAuthority().publicKey()));
    assertTrue(needed.contains(glamAccounts.mintEventAuthority()));
    assertTrue(needed.contains(mintKey));
    final var escrowKey = client.escrowAccount().publicKey();
    assertTrue(needed.contains(escrowKey));
    assertTrue(needed.contains(
        accountClient.splClient().findATA(escrowKey, solanaAccounts.token2022Program(), mintKey).publicKey()));
  }

  @Test
  void vaultTokensDeriveAtasOnlyForTokenProgramAccounts() {
    final var builder = kaminoBuilder();
    final var client = builder.stateAccountClient();
    final var accountClient = client.accountClient();
    final var solanaAccounts = accountClient.solanaAccounts();
    final var tokenProgram = solanaAccounts.tokenProgram();
    final var baseAssetMint = client.baseAssetMint();

    final var foreign = key(9200);
    final var mint2022 = key(9201);
    builder.addGlamVaultTokens(Arrays.asList(
        accountInfo(baseAssetMint, tokenProgram, new byte[0]),
        accountInfo(mint2022, solanaAccounts.token2022Program(), new byte[0]),
        // owned by a non-token program: no ATA may be derived for it
        accountInfo(foreign, solanaAccounts.systemProgram(), new byte[0]),
        null
    ));

    final var needed = builder.accountsNeeded();
    // the 2022-owned mint gets a vault ATA, but no escrow ATA: it is not the base asset
    assertTrue(needed.contains(accountClient.findATA(solanaAccounts.token2022Program(), mint2022).publicKey()));
    assertFalse(needed.contains(
            accountClient.splClient()
                .findATA(client.escrowAccount().publicKey(), solanaAccounts.token2022Program(), mint2022)
                .publicKey()),
        "an escrow ATA was derived for a non-base-asset mint");
    assertTrue(needed.contains(accountClient.findATA(tokenProgram, baseAssetMint).publicKey()),
        "vault ATA for the base asset missing");
    final var escrowKey = client.escrowAccount().publicKey();
    assertTrue(needed.contains(
            accountClient.splClient().findATA(escrowKey, tokenProgram, baseAssetMint).publicKey()),
        "escrow base-asset ATA missing");
    assertFalse(needed.contains(accountClient.findATA(tokenProgram, foreign).publicKey()),
        "an ATA was derived for a non-token account");
  }

  @Test
  void kaminoVaultStatesMapByTokenMint() {
    final byte[] vaultStateData = readResource("accounts/kamino/" + VAULT_STATE_KEY + ".dat.gz");
    final var map = VaultTableBuilder.mapKaminoVaultStatesByMint(
        List.of(accountInfo(VAULT_STATE_KEY, KaminoAccounts.MAIN_NET.kVaultsProgram(), vaultStateData))
    );
    final var vaultState = software.sava.idl.clients.kamino.vaults.gen.types.VaultState
        .read(VAULT_STATE_KEY, vaultStateData);
    assertEquals(1, map.size());
    assertEquals(VAULT_STATE_KEY, map.get(vaultState.tokenMint())._address());
  }

  @Test
  void kaminoVaultAccountsCollectTheVaultSurfaceAndQueueReserves() {
    final byte[] vaultStateData = readResource("accounts/kamino/" + VAULT_STATE_KEY + ".dat.gz");
    final var vaultState = software.sava.idl.clients.kamino.vaults.gen.types.VaultState
        .read(VAULT_STATE_KEY, vaultStateData);
    final var builder = kaminoBuilder();
    final var accountClient = builder.stateAccountClient().accountClient();
    final var tokenProgram = accountClient.solanaAccounts().tokenProgram();
    final var kaminoAccounts = KaminoAccounts.MAIN_NET;

    // a token account holding the vault's deposit token, as the vault position
    final var positionKey = key(9300);
    final byte[] tokenAccountData = new byte[165];
    vaultState.tokenMint().write(tokenAccountData, 0);
    positionKey.write(tokenAccountData, 32);
    // a token account whose mint has no vault contributes nothing
    final var strangeMint = key(9400);
    final byte[] strangeTokenAccount = new byte[165];
    strangeMint.write(strangeTokenAccount, 0);
    builder.addKaminoVaultAccounts(
        Arrays.asList(
            accountInfo(positionKey, tokenProgram, tokenAccountData),
            accountInfo(key(9401), tokenProgram, strangeTokenAccount),
            // an 82-byte MINT account is skipped by shape, not parsed
            accountInfo(key(9402), tokenProgram, new byte[82]),
            null
        ),
        Map.of(vaultState.tokenMint(), vaultState)
    );

    final var needed = builder.accountsNeeded();
    assertFalse(needed.contains(key(9401)), "an unknown-mint position was collected");
    assertTrue(needed.contains(accountClient.glamAccounts().readKaminoIntegrationAuthority().publicKey()));
    assertTrue(needed.contains(kaminoAccounts.kVaultsProgram()));
    assertTrue(needed.contains(kaminoAccounts.kVaultsEventAuthority()));
    assertTrue(needed.contains(kaminoAccounts.farmsGlobalConfig()));
    assertTrue(needed.contains(positionKey));
    assertTrue(needed.contains(vaultState._address()));
    assertTrue(needed.contains(vaultState.tokenVault()));
    assertTrue(needed.contains(vaultState.tokenMint()));
    assertTrue(needed.contains(vaultState.sharesMint()));
    assertTrue(needed.contains(vaultState.baseVaultAuthority()));
    assertTrue(needed.contains(accountClient.findATA(tokenProgram, vaultState.tokenMint()).publicKey()));

    // the vault's own lookup table is fetched in the second phase too
    final var secondPhase = builder.secondPhaseAccountsNeeded();
    assertTrue(secondPhase.contains(vaultState.vaultLookupTable()));
    boolean anyReserve = false;
    for (final var allocation : vaultState.vaultAllocationStrategy()) {
      final var reserve = allocation.reserve();
      if (reserve != null && !PublicKey.NONE.equals(reserve)) {
        anyReserve = true;
        assertTrue(secondPhase.contains(reserve), reserve::toBase58);
      }
    }
    assertTrue(anyReserve, "fixture vault should allocate to at least one reserve");
  }

  @Test
  void kaminoVaultSecondPhaseAddsReserveAndScopeAccounts() {
    final byte[] vaultStateData = readResource("accounts/kamino/" + VAULT_STATE_KEY + ".dat.gz");
    final byte[] reserveData = readResource("accounts/kamino/" + SOL_RESERVE_KEY + ".dat.gz");
    final var vaultState = software.sava.idl.clients.kamino.vaults.gen.types.VaultState
        .read(VAULT_STATE_KEY, vaultStateData);
    final var solReserve = software.sava.idl.clients.kamino.lend.gen.types.Reserve
        .read(SOL_RESERVE_KEY, reserveData);
    // the second phase only walks reserves allocated by a registered vault
    final boolean allocated = Arrays.stream(vaultState.vaultAllocationStrategy())
        .anyMatch(a -> SOL_RESERVE_KEY.equals(a.reserve()));
    assertTrue(allocated, "fixture drift: the vault no longer allocates to the SOL reserve");

    final var builder = kaminoBuilder();
    final var tokenProgram = builder.stateAccountClient().accountClient().solanaAccounts().tokenProgram();
    final var positionKey = key(9300);
    final byte[] tokenAccountData = new byte[165];
    vaultState.tokenMint().write(tokenAccountData, 0);
    positionKey.write(tokenAccountData, 32);
    builder.addKaminoVaultAccounts(
        List.of(accountInfo(positionKey, tokenProgram, tokenAccountData)),
        Map.of(vaultState.tokenMint(), vaultState)
    );

    // the fetched second phase carries the reserve, the vault's lookup table,
    // a null slot, and an unrelated account the mapping must skip
    final var tableAccounts = keys(9500, 3);
    final var vaultTable = table(vaultState.vaultLookupTable(), tableAccounts);
    final byte[] tableData = new byte[LOOKUP_TABLE_META_SIZE + tableAccounts.size() * PublicKey.PUBLIC_KEY_LENGTH];
    int offset = LOOKUP_TABLE_META_SIZE;
    for (final var account : tableAccounts) {
      offset += account.write(tableData, offset);
    }
    builder.addKaminoVaultAccountsSecondPhase(Arrays.asList(
        accountInfo(SOL_RESERVE_KEY, KaminoAccounts.MAIN_NET.kLendProgram(), reserveData),
        accountInfo(vaultState.vaultLookupTable(),
            builder.stateAccountClient().accountClient().solanaAccounts().addressLookupTableProgram(), tableData),
        accountInfo(key(9600), KaminoAccounts.MAIN_NET.kLendProgram(), new byte[16]),
        null
    ));

    final var needed = builder.accountsNeeded();
    assertTrue(needed.contains(SOL_RESERVE_KEY));
    assertTrue(needed.contains(solReserve.lendingMarket()));
    // the SOL reserve prices through a mainnet scope feed the builder knows
    final var scopeFeedPrices = solReserve.config().tokenInfo().scopeConfiguration().priceFeed();
    assertTrue(needed.contains(scopeFeedPrices), "scope oracle prices account missing");
    final var hubble = KaminoAccounts.MAIN_NET.scopeMainnetHubbleFeed();
    final var klend = KaminoAccounts.MAIN_NET.scopeMainnetKLendFeed();
    final var mappingsKey = scopeFeedPrices.equals(hubble.oraclePrices())
        ? hubble.oracleMappings()
        : klend.oracleMappings();
    assertTrue(needed.contains(mappingsKey), "scope oracle mappings account missing");

    // the vault's lookup table was mapped and registered under the vault key
    final var mapped = builder.kaminoVaultLookupTables().get(vaultState._address());
    assertNotNull(mapped, "the vault lookup table was not mapped");
    assertEquals(vaultTable.address(), mapped.address());
    assertEquals(vaultTable.numAccounts(), mapped.numAccounts());
  }

  private static software.sava.rpc.json.http.client.SolanaRpcClient accountsClient(
      final List<List<PublicKey>> capturedKeys,
      final List<software.sava.rpc.json.http.response.AccountInfo<byte[]>> response) {
    return (software.sava.rpc.json.http.client.SolanaRpcClient) java.lang.reflect.Proxy.newProxyInstance(
        software.sava.rpc.json.http.client.SolanaRpcClient.class.getClassLoader(),
        new Class<?>[]{software.sava.rpc.json.http.client.SolanaRpcClient.class},
        (proxy, method, args) -> {
          if (method.getName().equals("getAccounts")) {
            @SuppressWarnings("unchecked") final var keys = (List<PublicKey>) args[0];
            capturedKeys.add(keys);
            return java.util.concurrent.CompletableFuture.completedFuture(response);
          }
          throw new UnsupportedOperationException(method.getName());
        }
    );
  }

  /// Drives the interface's default orchestration end to end on a state with
  /// every integration enabled: fetch, first-phase adds behind their ACL
  /// gates, the second-phase fetch, and the external-table removals.
  @Test
  void pipelineDefaultsDriveEveryEnabledPhase() {
    final var kaminoAccounts = KaminoAccounts.MAIN_NET;
    final var glamAccounts = GlamAccounts.MAIN_NET;
    final var enabledState = stateAccount(
        key(9003),
        new IntegrationAcl(glamAccounts.protocolProgram(),
            GlamProtocolConstants.PROTO_JUPITER_SWAP, new ProtocolPolicy[0]),
        new IntegrationAcl(glamAccounts.kaminoIntegrationProgram(),
            ExtKaminoConstants.PROTO_KAMINO_LENDING | ExtKaminoConstants.PROTO_KAMINO_VAULTS,
            new ProtocolPolicy[0])
    );
    final var vaultTableBuilder = VaultTableBuilder.build().create(enabledState, FEE_PAYER);
    final var accountClient = vaultTableBuilder.stateAccountClient().accountClient();
    final var altProgram = accountClient.solanaAccounts().addressLookupTableProgram();
    final var tokenProgram = accountClient.solanaAccounts().tokenProgram();

    final byte[] vaultStateData = readResource("accounts/kamino/" + VAULT_STATE_KEY + ".dat.gz");
    final var vaultState = software.sava.idl.clients.kamino.vaults.gen.types.VaultState
        .read(VAULT_STATE_KEY, vaultStateData);
    final var positionKey = key(9310);
    final byte[] positionData = new byte[165];
    vaultState.tokenMint().write(positionData, 0);
    positionKey.write(positionData, 32);

    final var lendTableAccounts = keys(9720, 2);
    final var firstPhaseKeys = new ArrayList<List<PublicKey>>();
    final var firstPhase = vaultTableBuilder.fetchAccountsNeeded(accountsClient(
        firstPhaseKeys,
        Arrays.asList(
            accountInfo(kaminoAccounts.mainMarketLUT(), altProgram, tableData(lendTableAccounts)),
            accountInfo(OBLIGATION_KEY, kaminoAccounts.kLendProgram(),
                obligation(MARKET_KEY, SOL_RESERVE_KEY, BORROW_RESERVE_KEY)),
            accountInfo(positionKey, tokenProgram, positionData),
            // the base-asset MINT, exactly as the real first phase serves it:
            // token-program-owned and 82 bytes — the kamino vault collector
            // must skip it, not crash reading it as a TokenAccount
            accountInfo(key(9003), tokenProgram, new byte[82]),
            null
        ))).join();
    assertEquals(List.of(List.copyOf(vaultTableBuilder.accountsNeeded())), firstPhaseKeys);

    vaultTableBuilder.addAccounts(firstPhase, Map.of(vaultState.tokenMint(), vaultState));
    final var needed = vaultTableBuilder.accountsNeeded();
    // each ACL-gated phase ran: jupiter, kamino lend, kamino vaults
    assertTrue(needed.contains(JupiterAccounts.MAIN_NET.swapProgram()), "jupiter phase skipped");
    assertTrue(needed.contains(OBLIGATION_KEY), "kamino lend phase skipped");
    assertTrue(needed.contains(vaultState._address()), "kamino vault phase skipped");
    // and the unconditional glam surface rode along, tokens included
    assertTrue(needed.contains(accountClient.glamAccounts().protocolProgram()));
    assertTrue(needed.contains(accountClient.findATA(tokenProgram, key(9003)).publicKey()),
        "the vault token phase never derived the base-asset ATA");

    // the second phase fetches exactly what the first phase queued
    final var reserveData = readResource("accounts/kamino/" + SOL_RESERVE_KEY + ".dat.gz");
    final var vaultTableAccounts = keys(9730, 2);
    final var secondPhaseKeys = new ArrayList<List<PublicKey>>();
    final var secondPhase = vaultTableBuilder.fetchSecondPhaseAccountsNeeded(accountsClient(
        secondPhaseKeys,
        Arrays.asList(
            accountInfo(SOL_RESERVE_KEY, kaminoAccounts.kLendProgram(), reserveData),
            accountInfo(vaultState.vaultLookupTable(), altProgram, tableData(vaultTableAccounts)),
            null
        ))).join();
    assertEquals(List.of(List.copyOf(vaultTableBuilder.secondPhaseAccountsNeeded())), secondPhaseKeys);

    vaultTableBuilder.addAccountsSecondPhase(secondPhase);
    final var solReserve = Reserve.read(SOL_RESERVE_KEY, reserveData);
    // both second-phase collectors ran: lend reserve surface + vault table
    assertTrue(vaultTableBuilder.accountsNeeded().contains(solReserve.collateral().supplyVault()),
        "kamino lend second phase skipped");
    final var impl = (VaultTableBuilderImpl) vaultTableBuilder;
    assertNotNull(impl.kaminoVaultLookupTables().get(vaultState._address()),
        "kamino vault second phase skipped");

    // removing external-protocol table accounts drops keys covered by BOTH
    // registered tables and keeps the rest
    final var kept = key(9740);
    impl.glamVaultTableAccounts().clear();
    impl.glamVaultTableAccounts().addAll(lendTableAccounts);
    impl.glamVaultTableAccounts().addAll(vaultTableAccounts);
    impl.glamVaultTableAccounts().add(kept);
    vaultTableBuilder.removeExternalProtocolTableAccounts();
    assertEquals(Set.of(kept), impl.glamVaultTableAccounts());
  }

  /// The same defaults on a state with no integration ACLs: every gated
  /// phase must stay silent.
  @Test
  void disabledIntegrationsAddNothing() {
    final var vaultTableBuilder = VaultTableBuilder.build().create(stateAccountClient());
    vaultTableBuilder.addAccounts(List.of(), Map.of());

    final var needed = vaultTableBuilder.accountsNeeded();
    final var kaminoAccounts = KaminoAccounts.MAIN_NET;
    assertFalse(needed.contains(JupiterAccounts.MAIN_NET.swapProgram()),
        "jupiter accounts were collected without the ACL");
    assertFalse(needed.contains(kaminoAccounts.farmsGlobalConfig()),
        "kamino lend accounts were collected without the ACL");
    assertFalse(needed.contains(kaminoAccounts.kVaultsProgram()),
        "kamino vault accounts were collected without the ACL");
  }

  @Test
  void aStateWithoutAMintSkipsTheMintSurface() {
    final var name = Arrays.copyOf("Mintless".getBytes(US_ASCII), StateAccount.NAME_LEN);
    final var stateAccount = new StateAccount(
        STATE_KEY, StateAccount.DISCRIMINATOR, AccountType.Vault, true,
        key(9001), key(9002),
        new byte[StateAccount.PORTFOLIO_MANAGER_NAME_LEN],
        new CreatedModel(new byte[8], FEE_PAYER, 1_650_000_000L),
        key(9003), 9, 0,
        name,
        0L, 0L,
        PublicKey.NONE,
        new PublicKey[0],
        new IntegrationAcl[0],
        new DelegateAcl[0],
        new PublicKey[0],
        new PricedProtocol[0],
        new EngineField[0][]
    );
    final var client = StateAccountClient.createClient(stateAccount, FEE_PAYER);
    final var builder = new VaultTableBuilderImpl(
        client,
        TABLE_PREFIX,
        new LinkedHashSet<>(),
        new LinkedHashSet<>(),
        new LinkedHashSet<>(),
        JupiterAccounts.MAIN_NET,
        KaminoAccounts.MAIN_NET,
        new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
        MarinadeAccounts.MAIN_NET,
        MeteoraAccounts.MAIN_NET
    );
    builder.addGlamVaultAccounts(List.of());

    final var needed = builder.accountsNeeded();
    final var glamAccounts = client.accountClient().glamAccounts();
    assertTrue(needed.contains(glamAccounts.protocolProgram()));
    assertTrue(needed.contains(client.baseAssetMint()));
    // no mint: none of the mint surface may be collected
    assertFalse(needed.contains(glamAccounts.readMintIntegrationAuthority().publicKey()));
    assertFalse(needed.contains(glamAccounts.mintEventAuthority()));
  }

  @Test
  void jupiterSwapAccountsAreCollected() {
    final var builder = kaminoBuilder();
    builder.addJupiterSwapAccounts(List.of());
    final var needed = builder.accountsNeeded();
    assertTrue(needed.contains(JupiterAccounts.MAIN_NET.swapProgram()));
    assertTrue(needed.contains(JupiterAccounts.MAIN_NET.aggregatorEventAuthority()));
  }

  private static byte[] tableData(final List<PublicKey> accounts) {
    final byte[] data = new byte[LOOKUP_TABLE_META_SIZE + accounts.size() * PublicKey.PUBLIC_KEY_LENGTH];
    int offset = LOOKUP_TABLE_META_SIZE;
    for (final var account : accounts) {
      offset += account.write(data, offset);
    }
    return data;
  }

  /// A zero-filled Obligation image with a real discriminator: unset
  /// deposit/borrow slots hold the all-zero key, which the collectors must
  /// filter as the NONE sentinel.
  private static byte[] obligation(final PublicKey market,
                                   final PublicKey depositReserve,
                                   final PublicKey borrowReserve) {
    final byte[] data = new byte[Obligation.BYTES];
    System.arraycopy(Obligation.DISCRIMINATOR.data(), 0, data, 0, 8);
    market.write(data, Obligation.LENDING_MARKET_OFFSET);
    depositReserve.write(data, Obligation.DEPOSITS_OFFSET + ObligationCollateral.DEPOSIT_RESERVE_OFFSET);
    borrowReserve.write(data, Obligation.BORROWS_OFFSET + ObligationLiquidity.BORROW_RESERVE_OFFSET);
    return data;
  }

  private static final PublicKey MARKET_KEY = key(9800);
  private static final PublicKey BORROW_RESERVE_KEY = key(9801);
  private static final PublicKey OBLIGATION_KEY = key(9802);

  /// Registers the mainnet main-market table plus one obligation depositing
  /// into the SOL reserve fixture and borrowing from a second reserve.
  private static VaultTableBuilderImpl lendBuilder(final List<PublicKey> mainMarketTableAccounts) {
    final var builder = kaminoBuilder();
    final var kaminoAccounts = KaminoAccounts.MAIN_NET;
    final var altProgram = builder.stateAccountClient().accountClient()
        .solanaAccounts().addressLookupTableProgram();
    final byte[] obligationData = obligation(MARKET_KEY, SOL_RESERVE_KEY, BORROW_RESERVE_KEY);
    final byte[] wrongDiscriminator = obligationData.clone();
    wrongDiscriminator[0] ^= 0x01;
    builder.addKaminoLendAccounts(Arrays.asList(
        accountInfo(kaminoAccounts.mainMarketLUT(), altProgram, tableData(mainMarketTableAccounts)),
        accountInfo(OBLIGATION_KEY, kaminoAccounts.kLendProgram(), obligationData),
        // wrong length, wrong discriminator, wrong owner: all skipped
        accountInfo(key(9803), kaminoAccounts.kLendProgram(), new byte[16]),
        accountInfo(key(9804), kaminoAccounts.kLendProgram(), wrongDiscriminator),
        accountInfo(key(9805), altProgram, obligationData),
        // valid discriminator but truncated: the length guard is what stands
        // between this account and an out-of-bounds Obligation read
        accountInfo(key(9806), kaminoAccounts.kLendProgram(), Arrays.copyOf(obligationData, 16)),
        null
    ));
    return builder;
  }

  @Test
  void kaminoLendAccountsCollectObligationsAndQueueReserves() {
    final var builder = lendBuilder(keys(9700, 2));
    final var kaminoAccounts = KaminoAccounts.MAIN_NET;

    // only the well-formed kLend-owned obligation registers
    assertEquals(Set.of(OBLIGATION_KEY), builder.glamVaultKaminoObligations().keySet());

    final var needed = builder.accountsNeeded();
    final var glamAccounts = builder.stateAccountClient().accountClient().glamAccounts();
    assertTrue(needed.contains(glamAccounts.readKaminoIntegrationAuthority().publicKey()));
    assertTrue(needed.contains(kaminoAccounts.farmsGlobalConfig()));
    assertTrue(needed.contains(OBLIGATION_KEY));
    assertTrue(needed.contains(MARKET_KEY));
    assertTrue(needed.contains(
        KaminoAccounts.lendingMarketAuthPda(MARKET_KEY, kaminoAccounts.kLendProgram()).publicKey()));

    // the main-market table was mapped and registered for later removal
    final var mainMarketTable = builder.kaminoLookupTables().get(kaminoAccounts.mainMarketLUT());
    assertNotNull(mainMarketTable, "the main market lookup table was not registered");
    assertEquals(2, mainMarketTable.numAccounts());

    // deposit and borrow reserves are queued for the second fetch; the
    // obligation's empty slots hold the all-zero key and must be filtered
    final var secondPhase = builder.secondPhaseAccountsNeeded();
    assertTrue(secondPhase.contains(SOL_RESERVE_KEY));
    assertTrue(secondPhase.contains(BORROW_RESERVE_KEY));
    assertFalse(secondPhase.contains(PublicKey.NONE));
    assertEquals(2, secondPhase.size());
  }

  /// An RPC response without the main-market table account must not take the
  /// collection phase down: the obligation still registers, and no table
  /// entry appears.
  @Test
  void anAbsentMainMarketTableIsSkippedNotFatal() {
    final var builder = kaminoBuilder();
    final var kaminoAccounts = KaminoAccounts.MAIN_NET;
    builder.addKaminoLendAccounts(List.of(
        accountInfo(OBLIGATION_KEY, kaminoAccounts.kLendProgram(),
            obligation(MARKET_KEY, SOL_RESERVE_KEY, BORROW_RESERVE_KEY))
    ));
    assertEquals(Set.of(OBLIGATION_KEY), builder.glamVaultKaminoObligations().keySet());
    assertTrue(builder.kaminoLookupTables().isEmpty(),
        "no lookup table may register from an absent account");
    assertTrue(builder.accountsNeeded().contains(MARKET_KEY));
  }

  @Test
  void removeKaminoLendTableAccountsDropsCoveredKeys() {
    final var covered = keys(9700, 2);
    final var builder = lendBuilder(covered);
    final var kept = key(9706);
    builder.glamVaultTableAccounts().addAll(covered);
    builder.glamVaultTableAccounts().add(kept);

    builder.removeKaminoLendTableAccounts();
    assertEquals(Set.of(kept), builder.glamVaultTableAccounts(),
        "keys covered by the kamino market table must not be re-added to the glam table");
  }

  @Test
  void removeKaminoVaultTableAccountsDropsCoveredKeys() {
    final var builder = kaminoBuilder();
    final var covered = keys(9710, 2);
    builder.kaminoVaultLookupTables().put(key(9711), table(key(9712), covered));
    final var kept = key(9716);
    builder.glamVaultTableAccounts().addAll(covered);
    builder.glamVaultTableAccounts().add(kept);

    builder.removeKaminoVaultTableAccounts();
    assertEquals(Set.of(kept), builder.glamVaultTableAccounts(),
        "keys covered by a kamino vault table must not be re-added to the glam table");
  }

  @Test
  void kaminoLendSecondPhaseAddsTheReserveSurface() {
    final byte[] reserveData = readResource("accounts/kamino/" + SOL_RESERVE_KEY + ".dat.gz");
    final var solReserve = Reserve.read(SOL_RESERVE_KEY, reserveData);
    final var builder = lendBuilder(keys(9700, 2));
    final var kLendProgram = KaminoAccounts.MAIN_NET.kLendProgram();

    builder.addKaminoAccountsSecondPhase(Arrays.asList(
        accountInfo(SOL_RESERVE_KEY, kLendProgram, reserveData),
        // the borrow reserve serves the same image under its own key: the
        // borrow loop must have queued it or this contributes nothing
        accountInfo(BORROW_RESERVE_KEY, kLendProgram, reserveData),
        // not a reserve any obligation references: skipped
        accountInfo(key(9900), kLendProgram, reserveData),
        null
    ));

    final var needed = builder.accountsNeeded();
    final var solanaAccounts = builder.stateAccountClient().accountClient().solanaAccounts();
    assertTrue(needed.contains(solanaAccounts.instructionsSysVar()));
    assertTrue(needed.contains(SOL_RESERVE_KEY));
    assertTrue(needed.contains(BORROW_RESERVE_KEY));
    assertFalse(needed.contains(key(9900)), "an unreferenced reserve was collected");
    assertTrue(needed.contains(solReserve.liquidity().mintPubkey()));
    assertTrue(needed.contains(solReserve.collateral().supplyVault()));
    assertTrue(needed.contains(solReserve.collateral().mintPubkey()));
  }

  @Test
  void kaminoVaultPositionsOwnedByToken2022AreCollected() {
    final byte[] vaultStateData = readResource("accounts/kamino/" + VAULT_STATE_KEY + ".dat.gz");
    final var vaultState = software.sava.idl.clients.kamino.vaults.gen.types.VaultState
        .read(VAULT_STATE_KEY, vaultStateData);
    final var builder = kaminoBuilder();
    final var solanaAccounts = builder.stateAccountClient().accountClient().solanaAccounts();

    final var positionKey = key(9301);
    final byte[] tokenAccountData = new byte[165];
    vaultState.tokenMint().write(tokenAccountData, 0);
    positionKey.write(tokenAccountData, 32);
    // same position bytes owned by a non-token program: must contribute nothing
    final var foreignKey = key(9302);
    builder.addKaminoVaultAccounts(
        Arrays.asList(
            accountInfo(positionKey, solanaAccounts.token2022Program(), tokenAccountData),
            accountInfo(foreignKey, solanaAccounts.addressLookupTableProgram(), tokenAccountData)
        ),
        Map.of(vaultState.tokenMint(), vaultState)
    );

    final var needed = builder.accountsNeeded();
    assertTrue(needed.contains(positionKey), "a token-2022 owned position was not collected");
    assertFalse(needed.contains(foreignKey), "a non-token-program account was collected");
  }

  /// The vault second phase without the vault's lookup-table account and with
  /// the reserve re-pointed at an unknown price feed: neither a null table nor
  /// null scope-feed accounts may land in the collections.
  @Test
  void kaminoVaultSecondPhaseSkipsAbsentTableAndUnknownFeed() {
    final byte[] vaultStateData = readResource("accounts/kamino/" + VAULT_STATE_KEY + ".dat.gz");
    final byte[] reserveData = readResource("accounts/kamino/" + SOL_RESERVE_KEY + ".dat.gz");
    final var vaultState = software.sava.idl.clients.kamino.vaults.gen.types.VaultState
        .read(VAULT_STATE_KEY, vaultStateData);
    final var builder = kaminoBuilder();
    final var tokenProgram = builder.stateAccountClient().accountClient().solanaAccounts().tokenProgram();
    final var positionKey = key(9300);
    final byte[] tokenAccountData = new byte[165];
    vaultState.tokenMint().write(tokenAccountData, 0);
    positionKey.write(tokenAccountData, 32);
    builder.addKaminoVaultAccounts(
        List.of(accountInfo(positionKey, tokenProgram, tokenAccountData)),
        Map.of(vaultState.tokenMint(), vaultState)
    );

    final byte[] repointed = reserveData.clone();
    key(9950).write(repointed, Reserve.CONFIG_OFFSET
        + ReserveConfig.TOKEN_INFO_OFFSET
        + TokenInfo.SCOPE_CONFIGURATION_OFFSET
        + ScopeConfiguration.PRICE_FEED_OFFSET);
    builder.addKaminoVaultAccountsSecondPhase(List.of(
        accountInfo(SOL_RESERVE_KEY, KaminoAccounts.MAIN_NET.kLendProgram(), repointed)
    ));

    final var needed = builder.accountsNeeded();
    assertTrue(needed.contains(SOL_RESERVE_KEY));
    assertTrue(builder.kaminoVaultLookupTables().isEmpty(),
        "an absent vault lookup table must not register an entry");
    final var hubble = KaminoAccounts.MAIN_NET.scopeMainnetHubbleFeed();
    final var klend = KaminoAccounts.MAIN_NET.scopeMainnetKLendFeed();
    assertFalse(needed.contains(hubble.oraclePrices()));
    assertFalse(needed.contains(klend.oraclePrices()),
        "scope accounts were collected for a reserve on an unknown feed");
  }

  /// Rolling from the fullest table to the next must re-derive the free space
  /// from that table's own population.
  @Test
  void secondTableSpaceIsComputedFromItsOwnPopulation() {
    final var bigKey = key(600);
    final var smallKey = key(601);
    final var big = table(bigKey, keys(2000, 254));    // 2 free
    final var small = table(smallKey, keys(3000, 250)); // 6 free
    final var accounts = keys(1, 40);
    final var builder = builder(accounts);

    final var tasks = builder.batchTableTasks(List.of(small, big));
    assertEquals(4, tasks.size());
    final var fillBig = assertInstanceOf(ExtendTable.class, tasks.get(0));
    assertEquals(bigKey, fillBig.tableKey());
    final var fillSmall = assertInstanceOf(ExtendTable.class, tasks.get(1));
    assertEquals(smallKey, fillSmall.tableKey());
    final var create = assertInstanceOf(CreateTable.class, tasks.get(2));
    assertEquals(TABLE_PREFIX.size() + 24, create.accounts.size());
    final var overflow = assertInstanceOf(DynamicExtendTable.class, tasks.get(3));
    // 40 = 2 into big + 6 into small + 24 with the create + 8 dynamic
    assertEquals(8, overflow.accounts.size());
  }

  @Test
  void glamVaultTablesAreFetchedByPrefix() {
    final var builder = kaminoBuilder();
    final var altProgram = builder.stateAccountClient().accountClient()
        .solanaAccounts().addressLookupTableProgram();
    final var tableA = key(700);
    final var tableB = key(701);
    final var capturedProgram = new ArrayList<PublicKey>();
    final var capturedFilters = new ArrayList<List<Filter>>();
    final var client = (software.sava.rpc.json.http.client.SolanaRpcClient) java.lang.reflect.Proxy.newProxyInstance(
        software.sava.rpc.json.http.client.SolanaRpcClient.class.getClassLoader(),
        new Class<?>[]{software.sava.rpc.json.http.client.SolanaRpcClient.class},
        (proxy, method, args) -> {
          if (method.getName().equals("getProgramAccounts")) {
            capturedProgram.add((PublicKey) args[0]);
            @SuppressWarnings("unchecked") final var filters = (List<Filter>) args[1];
            capturedFilters.add(filters);
            return java.util.concurrent.CompletableFuture.completedFuture(List.of(
                accountInfo(tableA, altProgram, tableData(keys(710, 2))),
                accountInfo(tableB, altProgram, tableData(keys(720, 5)))
            ));
          }
          throw new UnsupportedOperationException(method.getName());
        }
    );

    final var tables = builder.fetchGlamVaultTables(client).join();
    assertEquals(2, tables.size());
    assertEquals(tableA, tables.getFirst().address());
    assertEquals(2, tables.getFirst().numAccounts());
    assertEquals(tableB, tables.getLast().address());
    assertEquals(5, tables.getLast().numAccounts());

    // one fetch: active tables under the ALT program whose stored keys open
    // with this vault's exact table prefix, matched as one contiguous region
    assertEquals(List.of(altProgram), capturedProgram);
    final byte[] prefixKeys = new byte[PublicKey.PUBLIC_KEY_LENGTH * TABLE_PREFIX.size()];
    int offset = 0;
    for (final var prefixKey : TABLE_PREFIX) {
      offset += prefixKey.write(prefixKeys, offset);
    }
    final var expectedFilters = List.of(
        AddressLookupTable.activeFilter(),
        Filter.createMemCompFilter(LOOKUP_TABLE_META_SIZE, prefixKeys)
    );
    assertEquals(
        expectedFilters.stream().map(Filter::toJson).toList(),
        capturedFilters.getFirst().stream().map(Filter::toJson).toList()
    );
  }
}
