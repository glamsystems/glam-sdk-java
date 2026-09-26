package systems.glam.sdk.idl.programs.glam.jupiter;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.jupiter.JupiterAccounts;
import software.sava.idl.clients.kamino.KaminoAccounts;
import software.sava.idl.clients.kamino.lend.gen.KaminoLendingProgram;
import software.sava.idl.clients.kamino.lend.gen.types.Reserve;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.sdk.GlamAccounts;
import systems.glam.sdk.idl.programs.glam.protocol.gen.GlamProtocolProgram;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;
import static software.sava.core.accounts.meta.AccountMeta.*;

final class GlamJupiterProgramClientTests {

  private static final SolanaAccounts SOLANA_ACCOUNTS = SolanaAccounts.MAIN_NET;
  private static final PublicKey FEE_PAYER = fromBase58Encoded("F1oQY1jbdiJyxxeeuMBF2NsUckboyWo6TSXNqzJbrhxs");
  private static final PublicKey STATE_KEY = fromBase58Encoded("9fkan2jCsS7Xq3fLqgxgZT5pDCbj2MhQ5MAoEKSHrcAT");
  private static final PublicKey VAULT_KEY = fromBase58Encoded("ApgsxNeZbi9P2pCAjzYR8VauqnWZpNkbN1iRWH1QsSwH");

  private static PublicKey key(final int id) {
    final byte[] bytes = new byte[PublicKey.PUBLIC_KEY_LENGTH];
    bytes[0] = (byte) id;
    bytes[31] = 3;
    return PublicKey.createPubKey(bytes);
  }

  private static GlamJupiterProgramClient createClient() {
    return GlamJupiterProgramClient.createClient(GlamAccountClient.createClient(FEE_PAYER, STATE_KEY));
  }

  private static Instruction routeIx() {
    // shaped like a jupiter route: some reads, the vault as a writable signer,
    // and a later signer that must keep its rights
    return Instruction.createInstruction(
        AccountMeta.createInvoked(JupiterAccounts.MAIN_NET.swapProgram()),
        List.of(
            createRead(key(11)),
            createWritableSigner(VAULT_KEY),
            createWrite(key(12)),
            createReadOnlySigner(key(13))
        ),
        new byte[]{9, 8, 7, 6}
    );
  }

  /// The vault's signature requirement is removed wherever the route seats it, keeping its write
  /// access; every other seat, signer or not, is left as it is, and a route without a vault seat is
  /// returned unchanged. The instruction form rewrites the accounts the same way and keeps the
  /// program and data.
  @Test
  void fixCPICallerRightsStripsTheVaultWhereverItSits() {
    final var fixed = GlamJupiterProgramClient.fixCPICallerRights(routeIx().accounts(), VAULT_KEY);
    assertEquals(createRead(key(11)), fixed.get(0));
    // the vault keeps write access but loses its signer requirement
    assertEquals(createWrite(VAULT_KEY), fixed.get(1));
    assertFalse(fixed.get(1).signer());
    assertEquals(createWrite(key(12)), fixed.get(2));
    // a later signer keeps its rights
    assertEquals(createReadOnlySigner(key(13)), fixed.get(3));
    assertTrue(fixed.get(3).signer());

    final var fixedIx = GlamJupiterProgramClient.fixCPICallerRights(routeIx(), VAULT_KEY);
    assertEquals(JupiterAccounts.MAIN_NET.swapProgram(), fixedIx.programId().publicKey());
    assertArrayEquals(new byte[]{9, 8, 7, 6}, fixedIx.data());
    assertEquals(fixed, fixedIx.accounts());

    // no vault seat at all: nothing is rewritten, another signer included
    final var noVault = List.of(createRead(key(16)), createWritableSigner(key(17)));
    assertEquals(noVault, GlamJupiterProgramClient.fixCPICallerRights(noVault, VAULT_KEY));
  }

  @Test
  void clientAccessors() {
    final var client = createClient();
    assertSame(SOLANA_ACCOUNTS, client.solanaAccounts());
    assertSame(JupiterAccounts.MAIN_NET, client.jupiterAccounts());
    assertEquals(STATE_KEY, client.glamVaultAccounts().glamStateKey());
  }

  private static JupiterSwapContext.Builder contextBuilder(final PublicKey inputMint) {
    return JupiterSwapContext.build()
        .inputMintKey(inputMint)
        .inputTokenProgram(SOLANA_ACCOUNTS.tokenProgram())
        .outputMintKey(key(22))
        .outputTokenProgram(SOLANA_ACCOUNTS.tokenProgram())
        .amount(1_000_000L)
        .swapInstruction(routeIx());
  }

  @Test
  void uncheckedSwapWrapsTheRouteInAGlamCpi() {
    final var client = createClient();
    final var context = contextBuilder(key(21))
        .skipQuotePriceCheck(true)
        .create();

    final var instructions = client.swap(context);
    assertEquals(1, instructions.size());
    final var swapIx = instructions.getFirst();
    final var protocolProgram = GlamAccounts.MAIN_NET.protocolProgram();
    assertEquals(protocolProgram, swapIx.programId().publicKey());

    final var accounts = swapIx.accounts();
    assertEquals(createWrite(STATE_KEY), accounts.get(0));
    assertEquals(createWrite(VAULT_KEY), accounts.get(1));
    assertEquals(createWritableSigner(FEE_PAYER), accounts.get(2));
    assertEquals(createRead(JupiterAccounts.MAIN_NET.swapProgram()), accounts.get(3));
    // no program state: both slots degrade to the protocol program key
    assertEquals(createRead(protocolProgram), accounts.get(4));
    assertEquals(createRead(protocolProgram), accounts.get(5));
    // the global configuration rides along even when a skip is requested: the program reads
    // it to decide a limited skip, and prices the swap when it declines one
    assertEquals(createRead(GlamAccounts.MAIN_NET.globalConfigPDA().publicKey()), accounts.get(6));
    // no oracles given: those slots degrade to the protocol program key
    for (int i = 7; i <= 9; ++i) {
      assertEquals(createRead(protocolProgram), accounts.get(i), "slot " + i);
    }
    // the route's accounts follow, with the vault's signer bit stripped and the later signer kept
    assertEquals(
        List.of(createRead(key(11)), createWrite(VAULT_KEY), createWrite(key(12)), createReadOnlySigner(key(13))),
        accounts.subList(10, accounts.size())
    );

    final var ixData = GlamProtocolProgram.JupiterSwapV2IxData.read(swapIx);
    assertTrue(ixData.skipQuotePriceCheck());
    assertArrayEquals(new byte[]{9, 8, 7, 6}, ixData.data());
  }

  @Test
  void priceCheckedSwapCarriesConfigAndOracles() {
    final var client = createClient();
    final var solOracle = key(31);
    final var inputOracle = key(32);
    final var outputOracle = key(33);
    final var context = contextBuilder(key(21))
        .solUsdOracleKey(solOracle)
        .inputTokenOracleKey(inputOracle)
        .outputTokenOracleKey(outputOracle)
        .create();

    final var swapIx = client.swap(context).getFirst();
    final var accounts = swapIx.accounts();
    assertEquals(createRead(GlamAccounts.MAIN_NET.globalConfigPDA().publicKey()), accounts.get(6));
    assertEquals(createRead(solOracle), accounts.get(7));
    assertEquals(createRead(inputOracle), accounts.get(8));
    assertEquals(createRead(outputOracle), accounts.get(9));
    assertFalse(GlamProtocolProgram.JupiterSwapV2IxData.read(swapIx).skipQuotePriceCheck());
  }

  /// A requested skip is the program's to grant: a delegate holding only the limited permission,
  /// or one the program declines, is priced with the configuration and the oracles the caller
  /// supplied, so they ride along with the flag rather than being dropped for it.
  @Test
  void aRequestedSkipStillCarriesTheConfigAndOracles() {
    final var client = createClient();
    final var solOracle = key(31);
    final var inputOracle = key(32);
    final var outputOracle = key(33);
    final var context = contextBuilder(key(21))
        .skipQuotePriceCheck(true)
        .solUsdOracleKey(solOracle)
        .inputTokenOracleKey(inputOracle)
        .outputTokenOracleKey(outputOracle)
        .create();

    final var swapIx = client.swap(context).getFirst();
    final var accounts = swapIx.accounts();
    assertEquals(createRead(GlamAccounts.MAIN_NET.globalConfigPDA().publicKey()), accounts.get(6));
    assertEquals(createRead(solOracle), accounts.get(7));
    assertEquals(createRead(inputOracle), accounts.get(8));
    assertEquals(createRead(outputOracle), accounts.get(9));
    assertTrue(GlamProtocolProgram.JupiterSwapV2IxData.read(swapIx).skipQuotePriceCheck());
  }

  /// The GLAM system transfer that funds a wSOL token account must carry the Token program as its
  /// one remaining account, else the program fails it with MissingAccount before syncing: every
  /// wrap prelude, checked or unchecked, context-driven or program-state, appends it.
  @Test
  void everyWrapPreludeCarriesTheTokenProgram() {
    final var client = createClient();
    final var accountClient = GlamAccountClient.createClient(FEE_PAYER, STATE_KEY);
    final var tokenProgram = SOLANA_ACCOUNTS.tokenProgram();
    final var wSol = SOLANA_ACCOUNTS.wrappedSolTokenMint();
    final var wSolAta = accountClient.findATA(tokenProgram, wSol).publicKey();
    final var wrappedSolPDA = accountClient.wrappedSolPDA().publicKey();
    final var route = routeIx();

    final var uncheckedContext = client.swap(contextBuilder(wSol).skipQuotePriceCheck(true).wrapSOL(true).create());
    final var checkedContext = client.swap(contextBuilder(wSol).skipQuotePriceCheck(true).wrapSOL(true).createATA(true).create());
    final var programStateUnchecked = client.swapWithProgramStateUnchecked(
        key(23), wSol, tokenProgram, key(24), key(22), tokenProgram, 1_234L, route, true);
    final var programStateChecked = client.swapWithProgramStateChecked(
        key(23), wSol, tokenProgram, key(24), key(22), tokenProgram, 1_234L, route, true);

    assertFundsWrappedSol("unchecked", uncheckedContext.get(0), wrappedSolPDA, 1_000_000L);
    assertFundsWrappedSol("checked", checkedContext.get(1), wSolAta, 1_000_000L);
    assertFundsWrappedSol("program-state unchecked", programStateUnchecked.get(0), wrappedSolPDA, 1_234L);
    assertFundsWrappedSol("program-state checked", programStateChecked.get(1), wSolAta, 1_234L);
  }

  private static void assertFundsWrappedSol(final String path,
                                            final Instruction transfer,
                                            final PublicKey wrappedSolTokenAccount,
                                            final long lamports) {
    assertEquals(GlamAccounts.MAIN_NET.protocolProgram(), transfer.programId().publicKey(), path);
    assertEquals(
        List.of(
            createRead(STATE_KEY),
            createWrite(VAULT_KEY),
            createWritableSigner(FEE_PAYER),
            createRead(SOLANA_ACCOUNTS.systemProgram()),
            createWrite(wrappedSolTokenAccount),
            createRead(SOLANA_ACCOUNTS.tokenProgram())
        ),
        transfer.accounts(),
        path + ": the wSOL destination and the Token program as the one remaining account"
    );
    assertEquals(lamports, GlamProtocolProgram.SystemTransferIxData.read(transfer).lamports(), path);
  }

  /// The vault's signer bit is stripped by key, wherever the route seats it, and no other signer's is:
  /// a route may seat another signer first.
  @Test
  void fixCPICallerRightsByKeyStripsOnlyTheVault() {
    final var route = Instruction.createInstruction(
        AccountMeta.createInvoked(JupiterAccounts.MAIN_NET.swapProgram()),
        List.of(
            createReadOnlySigner(key(13)),
            createRead(key(11)),
            createWritableSigner(VAULT_KEY),
            createWrite(key(12))
        ),
        new byte[]{9, 8, 7, 6}
    );
    final var byKey = GlamJupiterProgramClient.fixCPICallerRights(route.accounts(), VAULT_KEY);
    assertEquals(createReadOnlySigner(key(13)), byKey.get(0), "another signer keeps its rights");
    assertEquals(createWrite(VAULT_KEY), byKey.get(2), "the vault keeps write access and loses its signer bit");
    // a read-only vault seat loses the bit the same way
    assertEquals(createRead(VAULT_KEY),
        GlamJupiterProgramClient.fixCPICallerRights(List.of(createReadOnlySigner(VAULT_KEY)), VAULT_KEY).getFirst());

    // and both CPIs are built by key: the route's earlier signer reaches the program with its bit
    final var client = createClient();
    final var swapIx = client.swap(contextBuilder(key(21)).skipQuotePriceCheck(true).swapInstruction(route).create()).getFirst();
    assertEquals(byKey, swapIx.accounts().subList(10, swapIx.accounts().size()));
    final var programStateIx = client.swapWithProgramStateUncheckedAndNoWrap(
        key(23), key(21), SOLANA_ACCOUNTS.tokenProgram(), key(24), key(22), SOLANA_ACCOUNTS.tokenProgram(), route);
    assertEquals(byKey, programStateIx.accounts().subList(6, programStateIx.accounts().size()),
        "the program-state CPI strips the vault by key too");
  }

  private static KaminoReserveRefresh reserve(final int id, final PublicKey scopePrices) {
    return new KaminoReserveRefresh(key(id), key(id + 100), key(id + 200), null, null, null, scopePrices);
  }

  /// A reserve that prices a role is refreshed in front of the swap, once however many roles it
  /// prices, in role order, with its six accounts as the batch declares them and an empty oracle
  /// position carrying the lending program; a reserve pricing no role is left alone, and a
  /// requested skip changes nothing, since the program may still price the swap.
  @Test
  void reservesThatPriceARoleAreRefreshedFirst() {
    final var client = createClient();
    final var lendingProgram = KaminoAccounts.MAIN_NET.kLendProgram();
    final var inputReserve = reserve(41, key(51));
    final var solReserve = reserve(42, null);
    final var unrelated = reserve(43, key(53));
    final var context = contextBuilder(key(21))
        .skipQuotePriceCheck(true)
        .solUsdOracleKey(solReserve.reserve())
        .inputTokenOracleKey(inputReserve.reserve())
        .outputTokenOracleKey(inputReserve.reserve())
        .kaminoReserves(List.of(unrelated, solReserve, inputReserve))
        .create();

    final var instructions = client.swap(context);
    assertEquals(2, instructions.size(), "one refresh, then the swap");
    final var refresh = instructions.getFirst();
    assertEquals(lendingProgram, refresh.programId().publicKey());
    assertFalse(KaminoLendingProgram.RefreshReservesBatchIxData.read(refresh).skipPriceUpdates(),
        "the pricing reads the prices the refresh writes, so nothing is skipped");
    assertEquals(
        List.of(
            createWrite(inputReserve.reserve()), createRead(inputReserve.lendingMarket()),
            createRead(lendingProgram), createRead(lendingProgram), createRead(lendingProgram), createRead(key(51)),
            createWrite(solReserve.reserve()), createRead(solReserve.lendingMarket()),
            createRead(lendingProgram), createRead(lendingProgram), createRead(lendingProgram), createRead(lendingProgram)
        ),
        refresh.accounts(),
        "input then SOL/USD, the shared output reserve once, unrelated reserves left out"
    );
    assertEquals(GlamAccounts.MAIN_NET.protocolProgram(), instructions.get(1).programId().publicKey());

    // the refresh leads every form of the swap: checked with and without the wrap prelude,
    // unchecked with it (the unchecked form without it is the case above)
    final var wSol = SOLANA_ACCOUNTS.wrappedSolTokenMint();
    final var checkedWrapped = client.swap(contextBuilder(wSol)
        .inputTokenOracleKey(inputReserve.reserve()).kaminoReserves(List.of(inputReserve))
        .wrapSOL(true).createATA(true).create());
    assertEquals(6, checkedWrapped.size(), "refresh, create input ata, fund, sync, create output ata, swap");
    assertEquals(lendingProgram, checkedWrapped.getFirst().programId().publicKey());
    final var checked = client.swap(contextBuilder(key(21))
        .inputTokenOracleKey(inputReserve.reserve()).kaminoReserves(List.of(inputReserve))
        .createATA(true).create());
    assertEquals(3, checked.size(), "refresh, create output ata, swap");
    assertEquals(lendingProgram, checked.getFirst().programId().publicKey());
    assertEquals(SOLANA_ACCOUNTS.associatedTokenAccountProgram(), checked.get(1).programId().publicKey());
    final var uncheckedWrapped = client.swap(contextBuilder(wSol)
        .inputTokenOracleKey(inputReserve.reserve()).kaminoReserves(List.of(inputReserve))
        .wrapSOL(true).create());
    assertEquals(4, uncheckedWrapped.size(), "refresh, fund, sync, swap");
    assertEquals(lendingProgram, uncheckedWrapped.getFirst().programId().publicKey());
    assertEquals(GlamAccounts.MAIN_NET.protocolProgram(), uncheckedWrapped.get(1).programId().publicKey(), "the funding transfer follows the refresh");

    // reserves pricing no role, or no reserves at all, add nothing
    assertEquals(1, client.swap(contextBuilder(key(21))
        .inputTokenOracleKey(key(32)).kaminoReserves(List.of(unrelated)).create()).size());
    assertEquals(1, client.swap(contextBuilder(key(21)).inputTokenOracleKey(key(32)).create()).size());
  }

  /// Each oracle a reserve's configuration names rides in its own position, and only an empty
  /// position takes the lending program's address; the batch refreshes a reserve once however
  /// many times it is listed, in first-seen order.
  @Test
  void aReserveForwardsEveryConfiguredOracleAndTheBatchListsItOnce() {
    final var lendingProgram = KaminoAccounts.MAIN_NET.kLendProgram();
    final var full = new KaminoReserveRefresh(key(61), key(62), key(63), key(64), key(65), key(66), key(67));
    assertEquals(
        List.of(createWrite(key(61)), createRead(key(62)), createRead(key(64)), createRead(key(65)), createRead(key(66)), createRead(key(67))),
        full.accounts(lendingProgram)
    );
    final var pythOnly = new KaminoReserveRefresh(key(71), key(72), key(73), key(74), null, null, null);
    assertEquals(
        List.of(createWrite(key(71)), createRead(key(72)), createRead(key(74)), createRead(lendingProgram), createRead(lendingProgram), createRead(lendingProgram)),
        pythOnly.accounts(lendingProgram)
    );

    final var batch = KaminoReserveRefresh.refreshInstruction(
        KaminoAccounts.MAIN_NET.invokedKLendProgram(), List.of(pythOnly, full, pythOnly, full));
    assertEquals(lendingProgram, batch.programId().publicKey());
    final var expected = new ArrayList<>(pythOnly.accounts(lendingProgram));
    expected.addAll(full.accounts(lendingProgram));
    assertEquals(expected, batch.accounts(), "each reserve once, in first-seen order");
    assertFalse(KaminoLendingProgram.RefreshReservesBatchIxData.read(batch).skipPriceUpdates());
    assertEquals(9, batch.data().length, "the discriminator and the one flag byte");

    // a vault seat that does not sign is left as it is by the keyed rewrite
    final var unsigned = List.of(createWrite(VAULT_KEY), createRead(VAULT_KEY), createRead(key(11)));
    assertEquals(unsigned, GlamJupiterProgramClient.fixCPICallerRights(unsigned, VAULT_KEY));
  }

  private static byte[] readGzipResource(final String name) {
    try (final var in = new GZIPInputStream(
        Objects.requireNonNull(GlamJupiterProgramClientTests.class.getResourceAsStream("/" + name), name))) {
      return in.readAllBytes();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /// A reserve's refresh accounts come from its own bytes: the mainnet SOL reserve names its market
  /// and a Scope feed, and no Pyth or Switchboard oracle, which the accounts hand the lending
  /// program's address in place of.
  @Test
  void aReserveRefreshIsReadFromTheReserve() {
    final var reserveKey = fromBase58Encoded("d4A2prbA2whesmvHaL88BH6Ewn5N4bTSU2Ze8P6Bc4Q");
    final var data = readGzipResource("accounts/kamino/" + reserveKey + ".dat.gz");
    final var refresh = KaminoReserveRefresh.of(Reserve.read(reserveKey, data));

    assertEquals(reserveKey, refresh.reserve());
    assertEquals(fromBase58Encoded("7u3HeHxYDLhnCoErrtycNokbQYbWGzLs6JSDqGAv5PfF"), refresh.lendingMarket());
    assertEquals(SOLANA_ACCOUNTS.wrappedSolTokenMint(), refresh.liquidityMint());
    assertNull(refresh.pythOracle());
    assertNull(refresh.switchboardPriceOracle());
    assertNull(refresh.switchboardTwapOracle());
    final var scope = fromBase58Encoded("3t4JZcueEzTbVP6kLxXrL3VpWx45jDer4eqysweBchNH");
    assertEquals(scope, refresh.scopePrices());
    final var lendingProgram = KaminoAccounts.MAIN_NET.kLendProgram();
    assertEquals(
        List.of(
            createWrite(reserveKey), createRead(refresh.lendingMarket()),
            createRead(lendingProgram), createRead(lendingProgram), createRead(lendingProgram), createRead(scope)
        ),
        refresh.accounts(lendingProgram)
    );

    // Kamino's other empty spelling, the nu111... null key its manager writes into unused
    // positions, is empty too: the lending program takes only its own address as none, so a
    // reserve carrying it in a position must hand the program's address there, not the null key
    final var nulled = data.clone();
    final int tokenInfo = Reserve.CONFIG_OFFSET + software.sava.idl.clients.kamino.lend.gen.types.ReserveConfig.TOKEN_INFO_OFFSET;
    final var nullKey = KaminoAccounts.NULL_KEY.toByteArray();
    System.arraycopy(nullKey, 0, nulled, tokenInfo + software.sava.idl.clients.kamino.lend.gen.types.TokenInfo.PYTH_CONFIGURATION_OFFSET, nullKey.length);
    System.arraycopy(nullKey, 0, nulled, tokenInfo + software.sava.idl.clients.kamino.lend.gen.types.TokenInfo.SCOPE_CONFIGURATION_OFFSET, nullKey.length);
    final var nulledRefresh = KaminoReserveRefresh.of(Reserve.read(reserveKey, nulled));
    assertNull(nulledRefresh.pythOracle());
    assertNull(nulledRefresh.scopePrices());
    assertEquals(
        List.of(createWrite(reserveKey), createRead(refresh.lendingMarket()),
            createRead(lendingProgram), createRead(lendingProgram), createRead(lendingProgram), createRead(lendingProgram)),
        nulledRefresh.accounts(lendingProgram)
    );

    // a reserve naming no market cannot be refreshed: the batch has a market in every second position
    final var marketless = data.clone();
    java.util.Arrays.fill(marketless, Reserve.LENDING_MARKET_OFFSET, Reserve.LENDING_MARKET_OFFSET + PublicKey.PUBLIC_KEY_LENGTH, (byte) 0);
    final var refused = assertThrows(IllegalArgumentException.class, () -> KaminoReserveRefresh.of(Reserve.read(reserveKey, marketless)));
    assertTrue(refused.getMessage().contains("names no lending market"), refused.getMessage());
  }

  /// The staging deployment's client sends the CPI to the staging protocol program with its own
  /// configuration, and takes its Jupiter and Kamino programs from the accounts it was given: a
  /// refresh goes to the injected lending program and fills empty positions with its address.
  @Test
  void aStagingClientTargetsTheStagingProgram() {
    final var staging = GlamAccounts.MAIN_NET_STAGING;
    final var accountClient = GlamAccountClient.createClient(SOLANA_ACCOUNTS, staging, FEE_PAYER, STATE_KEY);
    final var kamino = KaminoAccounts.createAccounts(key(91), key(92), key(93), key(94), key(95), key(96));
    final var client = GlamJupiterProgramClient.createClient(accountClient, JupiterAccounts.MAIN_NET, kamino);
    assertSame(kamino, client.kaminoAccounts());
    assertEquals(accountClient.vaultAccounts().vaultPublicKey(), client.glamVaultAccounts().vaultPublicKey());

    final var swapIx = client.swap(contextBuilder(key(21)).create()).getFirst();
    assertEquals(staging.protocolProgram(), swapIx.programId().publicKey());
    final var accounts = swapIx.accounts();
    assertEquals(createWrite(accountClient.vaultAccounts().vaultPublicKey()), accounts.get(1));
    assertEquals(createRead(staging.globalConfigPDA().publicKey()), accounts.get(6));
    assertEquals(createRead(staging.protocolProgram()), accounts.get(7), "absent oracle: the staging program's key");

    final var priced = reserve(41, null);
    final var refresh = client.swap(contextBuilder(key(21))
        .inputTokenOracleKey(priced.reserve()).kaminoReserves(List.of(priced)).create()).getFirst();
    assertEquals(key(91), refresh.programId().publicKey(), "the injected lending program");
    assertEquals(createRead(key(91)), refresh.accounts().get(2), "its address in an empty position");
  }

  /// A context without the keys every swap path reads is refused when built, naming the key.
  @Test
  void aContextWithoutItsRequiredKeysIsRefused() {
    for (final var missing : List.of("inputMintKey", "inputTokenProgram", "outputMintKey", "outputTokenProgram", "swapInstruction")) {
      final var builder = JupiterSwapContext.build()
          .inputMintKey("inputMintKey".equals(missing) ? null : key(21))
          .inputTokenProgram("inputTokenProgram".equals(missing) ? null : SOLANA_ACCOUNTS.tokenProgram())
          .outputMintKey("outputMintKey".equals(missing) ? null : key(22))
          .outputTokenProgram("outputTokenProgram".equals(missing) ? null : SOLANA_ACCOUNTS.tokenProgram())
          .swapInstruction("swapInstruction".equals(missing) ? null : routeIx());
      final var refused = assertThrows(NullPointerException.class, builder::create, missing);
      assertEquals(missing, refused.getMessage());
    }
    // reserves left unset are an empty list, never null
    assertEquals(List.of(), contextBuilder(key(21)).create().kaminoReserves());
    assertEquals(List.of(), contextBuilder(key(21)).kaminoReserves(null).create().kaminoReserves());
  }

  /// The context keeps its own copy of the caller's reserves: a list mutated or reused after the
  /// context is built does not reach the swap, the copy cannot be modified through the accessor at
  /// any size, and a list holding a null reserve is refused when the context is built.
  @Test
  void kaminoReservesAreCopiedWhenTheContextIsBuilt() {
    final var client = createClient();
    final var priced = reserve(41, key(51));
    final var reserves = new ArrayList<KaminoReserveRefresh>();
    reserves.add(priced);
    final var context = contextBuilder(key(21)).inputTokenOracleKey(priced.reserve()).kaminoReserves(reserves).create();
    reserves.clear();
    reserves.add(reserve(43, key(53)));

    assertEquals(List.of(priced), context.kaminoReserves(), "the context holds what it was built with");
    final var instructions = client.swap(context);
    assertEquals(2, instructions.size(), "the refresh still leads the swap");
    assertEquals(priced.accounts(KaminoAccounts.MAIN_NET.kLendProgram()), instructions.getFirst().accounts());

    assertThrows(UnsupportedOperationException.class, () -> context.kaminoReserves().add(priced));
    assertThrows(UnsupportedOperationException.class, () -> contextBuilder(key(21)).create().kaminoReserves().add(priced));
    final var withNull = java.util.Arrays.asList(priced, null);
    assertThrows(NullPointerException.class, () -> contextBuilder(key(21)).kaminoReserves(withNull).create());
  }

  /// A reserve is refreshed once however many records name it: records are told apart by reserve
  /// key, not by identity or equality, and the first listed one supplies the accounts, in the batch
  /// builder and through the client.
  @Test
  void aReserveNamedByDistinctRecordsIsRefreshedOnce() {
    final var lendingProgram = KaminoAccounts.MAIN_NET.kLendProgram();
    final var first = new KaminoReserveRefresh(key(41), key(141), key(241), null, null, null, key(51));
    final var second = new KaminoReserveRefresh(key(41), key(142), key(242), key(64), null, null, null);
    assertNotEquals(first, second, "the same reserve under two decodes");

    final var batch = KaminoReserveRefresh.refreshInstruction(
        KaminoAccounts.MAIN_NET.invokedKLendProgram(), List.of(first, second, first));
    assertEquals(first.accounts(lendingProgram), batch.accounts(), "six accounts, the first record's");

    final var instructions = createClient().swap(contextBuilder(key(21))
        .inputTokenOracleKey(key(41)).outputTokenOracleKey(key(41))
        .kaminoReserves(List.of(second, first))
        .create());
    assertEquals(2, instructions.size(), "one refresh, then the swap");
    assertEquals(second.accounts(lendingProgram), instructions.getFirst().accounts(),
        "one reserve pricing both roles under two records: refreshed once, as first listed");
  }

  /// Reserves are refreshed in role order, input, output then SOL/USD, whatever order the caller
  /// lists them in.
  @Test
  void reservesAreRefreshedInRoleOrder() {
    final var lendingProgram = KaminoAccounts.MAIN_NET.kLendProgram();
    final var inputReserve = reserve(41, key(51));
    final var outputReserve = reserve(44, key(54));
    final var solReserve = reserve(42, null);
    final var refresh = createClient().swap(contextBuilder(key(21))
        .solUsdOracleKey(solReserve.reserve())
        .inputTokenOracleKey(inputReserve.reserve())
        .outputTokenOracleKey(outputReserve.reserve())
        .kaminoReserves(List.of(solReserve, outputReserve, inputReserve))
        .create()).getFirst();
    final var expected = new ArrayList<>(inputReserve.accounts(lendingProgram));
    expected.addAll(outputReserve.accounts(lendingProgram));
    expected.addAll(solReserve.accounts(lendingProgram));
    assertEquals(expected, refresh.accounts(), "input, output, SOL/USD");
  }

  @Test
  void wrappingSwapPrependsTransferAndSync() {
    final var client = createClient();
    final var wSolMint = SOLANA_ACCOUNTS.wrappedSolTokenMint();
    final var context = contextBuilder(wSolMint)
        .skipQuotePriceCheck(true)
        .wrapSOL(true)
        .create();

    final var instructions = client.swap(context);
    assertEquals(3, instructions.size());

    final var wrappedSolPDA = GlamAccountClient.createClient(FEE_PAYER, STATE_KEY).wrappedSolPDA().publicKey();
    final var transferIx = instructions.getFirst();
    assertEquals(createWrite(wrappedSolPDA), transferIx.accounts().get(4));
    assertEquals(createRead(SOLANA_ACCOUNTS.tokenProgram()), transferIx.accounts().get(5),
        "the Token program is the transfer's one remaining account");
    assertEquals(6, transferIx.accounts().size());
    assertEquals(1_000_000L, GlamProtocolProgram.SystemTransferIxData.read(transferIx).lamports());
    final var syncIx = instructions.get(1);
    assertEquals(SOLANA_ACCOUNTS.tokenProgram(), syncIx.programId().publicKey());
    assertEquals(GlamAccounts.MAIN_NET.protocolProgram(), instructions.get(2).programId().publicKey());

    // wrapSOL without a wSOL input mint must not wrap
    final var nonSolContext = contextBuilder(key(21))
        .skipQuotePriceCheck(true)
        .wrapSOL(true)
        .create();
    assertEquals(1, client.swap(nonSolContext).size());

    // and a wSOL input without wrapSOL must not wrap either — both operands
    // of the wrap condition matter, in both the checked and unchecked paths
    final var noWrapContext = contextBuilder(wSolMint)
        .skipQuotePriceCheck(true)
        .create();
    assertEquals(1, client.swap(noWrapContext).size());
    final var noWrapChecked = contextBuilder(wSolMint)
        .skipQuotePriceCheck(true)
        .createATA(true)
        .create();
    assertEquals(2, client.swap(noWrapChecked).size());
  }

  @Test
  void checkedSwapCreatesTheOutputTokenAccount() {
    final var client = createClient();
    final var accountClient = GlamAccountClient.createClient(FEE_PAYER, STATE_KEY);
    final var outputATA = accountClient.findATA(SOLANA_ACCOUNTS.tokenProgram(), key(22)).publicKey();

    final var context = contextBuilder(key(21))
        .skipQuotePriceCheck(true)
        .createATA(true)
        .create();
    final var instructions = client.swap(context);
    assertEquals(2, instructions.size());
    final var createAtaIx = instructions.getFirst();
    assertEquals(SOLANA_ACCOUNTS.associatedTokenAccountProgram(), createAtaIx.programId().publicKey());
    assertTrue(createAtaIx.accounts().stream().anyMatch(meta -> meta.publicKey().equals(outputATA)));
    assertEquals(GlamAccounts.MAIN_NET.protocolProgram(), instructions.getLast().programId().publicKey());

    // a wSOL input adds the input ata creation, funding transfer and sync
    final var wrappingContext = contextBuilder(SOLANA_ACCOUNTS.wrappedSolTokenMint())
        .skipQuotePriceCheck(true)
        .createATA(true)
        .wrapSOL(true)
        .create();
    assertEquals(5, client.swap(wrappingContext).size());
  }

  @Test
  void createSwapTokenAccountsIdempotent() {
    final var client = createClient();
    final var accountClient = GlamAccountClient.createClient(FEE_PAYER, STATE_KEY);
    final var tokenProgram = SOLANA_ACCOUNTS.tokenProgram();

    final var context = contextBuilder(key(21)).create();
    final var byAta = client.createSwapTokenAccountsIdempotent(context);
    final var outputATA = accountClient.findATA(tokenProgram, key(22)).publicKey();
    assertEquals(1, byAta.size());
    assertNotNull(byAta.get(outputATA));

    // a wSOL input also needs its own vault ata
    final var wSolMint = SOLANA_ACCOUNTS.wrappedSolTokenMint();
    final var wSolContext = contextBuilder(wSolMint).create();
    final var bothAtas = client.createSwapTokenAccountsIdempotent(wSolContext);
    assertEquals(2, bothAtas.size());
    assertNotNull(bothAtas.get(accountClient.findATA(tokenProgram, wSolMint).publicKey()));
    assertNotNull(bothAtas.get(outputATA));

    // the explicit-key overload matches the context-driven one
    final var explicit = client.createSwapTokenAccountsIdempotent(
        tokenProgram, wSolMint, tokenProgram, key(22)
    );
    assertEquals(bothAtas.keySet(), explicit.keySet());
    // and for a non-wSOL input it must create only the output ata
    final var explicitSingle = client.createSwapTokenAccountsIdempotent(
        tokenProgram, key(21), tokenProgram, key(22)
    );
    assertEquals(1, explicitSingle.size());
    assertNotNull(explicitSingle.get(outputATA));
  }

  private static void assertSameInstruction(final String name, final Instruction expected, final Instruction actual) {
    assertEquals(expected.programId().publicKey(), actual.programId().publicKey(), name);
    assertEquals(expected.accounts(), actual.accounts(), name);
    assertArrayEquals(expected.data(), actual.data(), name);
  }

  private static void assertSameInstructions(final String name,
                                             final java.util.List<Instruction> expected,
                                             final java.util.List<Instruction> actual) {
    assertEquals(expected.size(), actual.size(), name);
    for (int i = 0; i < expected.size(); ++i) {
      assertSameInstruction(name + "[" + i + "]", expected.get(i), actual.get(i));
    }
  }

  /// Every convenience overload must produce exactly its fully-explicit
  /// form — this delegation family already produced a real dropped-argument
  /// bug (priceExternalPositions), so each hop is pinned.
  @Test
  void convenienceOverloadsMatchTheirExplicitForms() {
    final var client = createClient();
    final var tokenProgram = SOLANA_ACCOUNTS.tokenProgram();
    final var in = key(21);
    final var out = key(22);
    final long amount = 1_234L;
    final var route = routeIx();
    final var inState = key(23);
    final var outState = key(24);

    final var checked = client.swapWithProgramStateChecked(
        null, in, tokenProgram, null, out, tokenProgram, amount, route, false);
    final var checkedWrap = client.swapWithProgramStateChecked(
        null, in, tokenProgram, null, out, tokenProgram, amount, route, true);
    final var unchecked = client.swapWithProgramStateUnchecked(
        null, in, tokenProgram, null, out, tokenProgram, amount, route, false);
    final var uncheckedWrap = client.swapWithProgramStateUnchecked(
        null, in, tokenProgram, null, out, tokenProgram, amount, route, true);
    final var noWrap = client.swapWithProgramStateUncheckedAndNoWrap(
        null, in, tokenProgram, null, out, tokenProgram, route);

    assertSameInstructions("swapChecked/7",
        client.swapChecked(in, tokenProgram, out, tokenProgram, amount, route, false), checked);
    assertSameInstructions("swapChecked/5",
        client.swapChecked(in, out, amount, route, false), checked);
    assertSameInstructions("swapChecked/4 defaults to wrap",
        client.swapChecked(in, out, amount, route), checkedWrap);
    assertSameInstruction("swapUncheckedAndNoWrap/5",
        client.swapUncheckedAndNoWrap(in, tokenProgram, out, tokenProgram, route), noWrap);
    assertSameInstruction("swapUncheckedAndNoWrap/3",
        client.swapUncheckedAndNoWrap(in, out, route), noWrap);
    assertSameInstructions("swapUnchecked/7",
        client.swapUnchecked(in, tokenProgram, out, tokenProgram, amount, route, false), unchecked);
    assertSameInstructions("swapUnchecked/5",
        client.swapUnchecked(in, out, amount, route, false), unchecked);
    assertSameInstructions("swapUnchecked/4 defaults to wrap",
        client.swapUnchecked(in, out, amount, route), uncheckedWrap);

    // the program-state keys must survive the token-program-defaulting hops
    final var stateChecked = client.swapWithProgramStateChecked(
        inState, in, tokenProgram, outState, out, tokenProgram, amount, route, false);
    assertSameInstructions("swapWithProgramStateChecked/7",
        client.swapWithProgramStateChecked(inState, in, outState, out, amount, route, false), stateChecked);
    assertSameInstructions("swapWithProgramStateChecked/6 defaults to wrap",
        client.swapWithProgramStateChecked(inState, in, outState, out, amount, route),
        client.swapWithProgramStateChecked(inState, in, tokenProgram, outState, out, tokenProgram, amount, route, true));
    final var stateUnchecked = client.swapWithProgramStateUnchecked(
        inState, in, tokenProgram, outState, out, tokenProgram, amount, route, false);
    assertSameInstructions("swapWithProgramStateUnchecked/7",
        client.swapWithProgramStateUnchecked(inState, in, outState, out, amount, route, false), stateUnchecked);
    assertSameInstructions("swapWithProgramStateUnchecked/6 defaults to wrap",
        client.swapWithProgramStateUnchecked(inState, in, outState, out, amount, route),
        client.swapWithProgramStateUnchecked(inState, in, tokenProgram, outState, out, tokenProgram, amount, route, true));
    assertSameInstruction("swapWithProgramStateUncheckedAndNoWrap/5",
        client.swapWithProgramStateUncheckedAndNoWrap(inState, in, outState, out, route),
        client.swapWithProgramStateUncheckedAndNoWrap(inState, in, tokenProgram, outState, out, tokenProgram, route));

    // and they actually land in the CPI: with vs without must differ
    assertNotEquals(checked.getLast().accounts(), stateChecked.getLast().accounts(),
        "the program-state keys never reached the checked swap CPI");
    assertNotEquals(unchecked.getLast().accounts(), stateUnchecked.getLast().accounts(),
        "the program-state keys never reached the unchecked swap CPI");

    // the route's accounts ride the CPI as extra accounts — Instruction is
    // immutable, so a dropped extraAccounts() result loses the whole route
    // (the wrapSOL variant of this was a real bug)
    assertTrue(noWrap.accounts().contains(createRead(key(11))),
        "the route accounts never reached the swap CPI");

    // a non-wSOL input must not trigger the wrap prelude even when allowed
    assertEquals(2, checkedWrap.size(), "wrap prelude added for a non-wSOL input");
    assertEquals(1, uncheckedWrap.size(), "wrap prelude added for a non-wSOL input");
  }

  /// The program-state swap variants share the wrap gate: a wSOL input plus
  /// wrapSOL prepends the fund-and-sync prelude; either alone must not.
  @Test
  void programStateSwapsWrapOnlyWrappedSolInputs() {
    final var client = createClient();
    final var tokenProgram = SOLANA_ACCOUNTS.tokenProgram();
    final var wSol = SOLANA_ACCOUNTS.wrappedSolTokenMint();
    final var out = key(22);
    final long amount = 1_234L;
    final var route = routeIx();
    final var inState = key(23);
    final var outState = key(24);

    final var checkedWrapped = client.swapWithProgramStateChecked(
        inState, wSol, tokenProgram, outState, out, tokenProgram, amount, route, true);
    // create input ATA, fund it, sync, create output ATA, swap
    assertEquals(5, checkedWrapped.size());
    final var checkedUnwrapped = client.swapWithProgramStateChecked(
        inState, wSol, tokenProgram, outState, out, tokenProgram, amount, route, false);
    assertEquals(2, checkedUnwrapped.size(), "wrapSOL=false must skip the prelude");

    final var uncheckedWrapped = client.swapWithProgramStateUnchecked(
        inState, wSol, tokenProgram, outState, out, tokenProgram, amount, route, true);
    // fund the wSOL PDA, sync, swap
    assertEquals(3, uncheckedWrapped.size());
    assertEquals(1, client.swapWithProgramStateUnchecked(
            inState, wSol, tokenProgram, outState, out, tokenProgram, amount, route, false).size(),
        "wrapSOL=false must skip the prelude");

    // the prelude funds the vault's wSOL account with exactly the swap amount
    final var transfer = checkedWrapped.get(1);
    assertEquals(GlamAccounts.MAIN_NET.protocolProgram(), transfer.programId().publicKey());
  }

  @Test
  void contextBuilderProgramStateSettersReturnTheBuilder() {
    final var builder = contextBuilder(key(21));
    assertSame(builder, builder.inputProgramStateKey(key(23)));
    assertSame(builder, builder.outputProgramStateKey(key(24)));
  }
}
