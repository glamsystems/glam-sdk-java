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
}
