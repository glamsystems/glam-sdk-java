package systems.glam.services.integrations.kamino;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.idl.clients.kamino.KaminoAccounts;
import software.sava.idl.clients.kamino.lend.gen.types.PythConfiguration;
import software.sava.idl.clients.kamino.lend.gen.types.Reserve;
import software.sava.idl.clients.kamino.lend.gen.types.ReserveConfig;
import software.sava.idl.clients.kamino.lend.gen.types.SwitchboardConfiguration;
import software.sava.idl.clients.kamino.lend.gen.types.TokenInfo;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.Context;
import systems.glam.services.tests.ResourceUtil;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static software.sava.core.accounts.PublicKey.fromBase58Encoded;

final class ReserveContextTests {

  private static final PublicKey SOL_RESERVE_KEY = fromBase58Encoded("d4A2prbA2whesmvHaL88BH6Ewn5N4bTSU2Ze8P6Bc4Q");

  private static final int TOKEN_INFO_BASE = Reserve.CONFIG_OFFSET + ReserveConfig.TOKEN_INFO_OFFSET;
  private static final int SCOPE_FEED_OFFSET = TOKEN_INFO_BASE + TokenInfo.SCOPE_CONFIGURATION_OFFSET;
  private static final int PYTH_PRICE_OFFSET = TOKEN_INFO_BASE + TokenInfo.PYTH_CONFIGURATION_OFFSET + PythConfiguration.PRICE_OFFSET;
  private static final int SWITCHBOARD_PRICE_OFFSET = TOKEN_INFO_BASE + TokenInfo.SWITCHBOARD_CONFIGURATION_OFFSET + SwitchboardConfiguration.PRICE_AGGREGATOR_OFFSET;
  private static final int SWITCHBOARD_TWAP_OFFSET = TOKEN_INFO_BASE + TokenInfo.SWITCHBOARD_CONFIGURATION_OFFSET + SwitchboardConfiguration.TWAP_AGGREGATOR_OFFSET;

  private static byte[] reserveFixture;

  private static PublicKey key(final int id) {
    final byte[] bytes = new byte[PublicKey.PUBLIC_KEY_LENGTH];
    bytes[0] = (byte) id;
    bytes[31] = 9;
    return PublicKey.createPubKey(bytes);
  }

  @BeforeAll
  static void beforeAll() throws IOException {
    reserveFixture = ResourceUtil.readResource("accounts/kamino/" + SOL_RESERVE_KEY + ".dat.gz");
  }

  /// The real SOL reserve with every oracle slot rewritten explicitly.
  private static byte[] reserveWithOracles(final PublicKey scopeFeed,
                                           final PublicKey pythPrice,
                                           final PublicKey switchboardPrice,
                                           final PublicKey switchboardTwap) {
    final var data = reserveFixture.clone();
    scopeFeed.write(data, SCOPE_FEED_OFFSET);
    pythPrice.write(data, PYTH_PRICE_OFFSET);
    switchboardPrice.write(data, SWITCHBOARD_PRICE_OFFSET);
    switchboardTwap.write(data, SWITCHBOARD_TWAP_OFFSET);
    return data;
  }

  private static ReserveContext context(final byte[] data) {
    final var accountInfo = new AccountInfo<>(
        SOL_RESERVE_KEY, new Context(1L, null), false, 0, KaminoAccounts.MAIN_NET.kLendProgram(),
        BigInteger.ZERO, 0, data
    );
    return ReserveContext.createContext(accountInfo, Map.of());
  }

  private static List<AccountMeta> refreshAccounts(final ReserveContext context) {
    final var accounts = new ArrayList<AccountMeta>(6);
    context.refreshReserveAccounts(accounts);
    assertEquals(6, accounts.size());
    // the reserve is written, its market is written, and both lead
    assertEquals(context.pubKey(), accounts.get(0).publicKey());
    assertTrue(accounts.get(0).write());
    assertEquals(context.market(), accounts.get(1).publicKey());
    assertTrue(accounts.get(1).write());
    return accounts;
  }

  private static void assertNullMeta(final AccountMeta meta) {
    assertEquals(KaminoAccounts.MAIN_NET.kLendProgram(), meta.publicKey());
  }

  @Test
  void nullKeysAreBothSpellings() {
    assertTrue(ReserveContext.isNullKey(PublicKey.NONE));
    assertTrue(ReserveContext.isNullKey(KaminoAccounts.NULL_KEY));
    assertFalse(ReserveContext.isNullKey(key(1)));
    assertFalse(ReserveContext.isNullKey(KaminoAccounts.MAIN_NET.kLendProgram()));
  }

  @Test
  void metaCachesServeTheSameInstancePerKey() {
    final var feed = key(2);
    assertSame(ReserveContext.readPriceFeedMeta(feed), ReserveContext.readPriceFeedMeta(feed));
    assertEquals(feed, ReserveContext.readPriceFeedMeta(feed).publicKey());
    assertFalse(ReserveContext.readPriceFeedMeta(feed).write());

    final var market = key(3);
    assertSame(ReserveContext.writeMarketMeta(market), ReserveContext.writeMarketMeta(market));
    assertEquals(market, ReserveContext.writeMarketMeta(market).publicKey());
    assertTrue(ReserveContext.writeMarketMeta(market).write());

    // the refresh sequence serves the very same cached instances
    final var context = context(reserveWithOracles(key(4), PublicKey.NONE, PublicKey.NONE, PublicKey.NONE));
    final var accounts = refreshAccounts(context);
    assertSame(ReserveContext.writeMarketMeta(context.market()), accounts.get(1));
    assertSame(ReserveContext.readPriceFeedMeta(key(4)), accounts.get(5));
  }

  @Test
  void aScopeFeedFillsTheLastOracleSlot() {
    final var feed = key(4);
    final var accounts = refreshAccounts(context(reserveWithOracles(
        feed, PublicKey.NONE, PublicKey.NONE, PublicKey.NONE
    )));
    assertNullMeta(accounts.get(2));
    assertNullMeta(accounts.get(3));
    assertNullMeta(accounts.get(4));
    assertEquals(feed, accounts.get(5).publicKey());
    assertFalse(accounts.get(5).write());
  }

  @Test
  void aPythOracleFillsTheFirstOracleSlot() {
    final var pyth = key(5);
    // the NULL_KEY sentinel spelling of "no feed" takes the same path
    final var accounts = refreshAccounts(context(reserveWithOracles(
        KaminoAccounts.NULL_KEY, pyth, PublicKey.NONE, PublicKey.NONE
    )));
    assertEquals(pyth, accounts.get(2).publicKey());
    assertNullMeta(accounts.get(3));
    assertNullMeta(accounts.get(4));
    assertNullMeta(accounts.get(5));
  }

  @Test
  void aSwitchboardOracleFillsTheMiddleSlots() {
    final var price = key(6);
    final var twap = key(7);
    final var accounts = refreshAccounts(context(reserveWithOracles(
        PublicKey.NONE, KaminoAccounts.NULL_KEY, price, twap
    )));
    assertNullMeta(accounts.get(2));
    assertEquals(price, accounts.get(3).publicKey());
    assertEquals(twap, accounts.get(4).publicKey());
    assertNullMeta(accounts.get(5));
  }

  @Test
  void noOracleConfigurationAtAllIsFatal() {
    final var context = context(reserveWithOracles(
        PublicKey.NONE, KaminoAccounts.NULL_KEY, PublicKey.NONE, KaminoAccounts.NULL_KEY
    ));
    final var ex = assertThrows(IllegalStateException.class,
        () -> context.refreshReserveAccounts(new ArrayList<>()));
    assertTrue(ex.getMessage().contains("No oracle configuration for Kamino Reserve"), ex::getMessage);
    assertTrue(ex.getMessage().contains(SOL_RESERVE_KEY.toBase58()), ex::getMessage);
  }
}
