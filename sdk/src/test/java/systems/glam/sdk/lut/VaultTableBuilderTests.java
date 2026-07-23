package systems.glam.sdk.lut;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.lookup.AddressLookupTable;
import software.sava.idl.clients.jupiter.JupiterAccounts;
import software.sava.idl.clients.kamino.KaminoAccounts;
import software.sava.idl.clients.marinade.stake_pool.MarinadeAccounts;
import software.sava.idl.clients.meteora.MeteoraAccounts;
import software.sava.idl.clients.spl.lut.gen.AddressLookupTablePDAs;
import systems.glam.sdk.GlamAccounts;
import systems.glam.sdk.StateAccountClient;
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
    final var name = Arrays.copyOf("Table Vault".getBytes(US_ASCII), StateAccount.NAME_LEN);
    final var stateAccount = new StateAccount(
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
    return StateAccountClient.createClient(stateAccount, FEE_PAYER);
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
    // NOTE: the impl calls add(systemProgram), but the system program's
    // address IS the all-zero key -- identical to the PublicKey.NONE sentinel
    // addAccount() filters -- so it never lands. Recorded as a finding; if the
    // filter is fixed to admit it, flip this assertion.
    assertFalse(needed.contains(solanaAccounts.systemProgram()));
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
}
