package systems.glam.sdk.idl.programs.glam.jupiter;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.kamino.KaminoAccounts;
import software.sava.idl.clients.kamino.lend.gen.KaminoLendingProgram;
import software.sava.idl.clients.kamino.lend.gen.types.Reserve;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/// The accounts refreshing one Kamino reserve reads, as the lending program's `refresh_reserves_batch`
/// takes them: the reserve itself, its lending market, and the four oracle positions the reserve's own
/// configuration names, a position it leaves empty being handed the lending program's address, which is
/// how the program reads a position as none.
///
/// A swap priced through a reserve (a `KaminoReserve` oracle in the global configuration) reads the
/// reserve's last price write, so the reserve is refreshed in front of the swap; see
/// [JupiterSwapContext#kaminoReserves()].
public record KaminoReserveRefresh(PublicKey reserve,
                                   PublicKey lendingMarket,
                                   PublicKey liquidityMint,
                                   PublicKey pythOracle,
                                   PublicKey switchboardPriceOracle,
                                   PublicKey switchboardTwapOracle,
                                   PublicKey scopePrices) {

  /// The refresh accounts of a decoded reserve: the market and the oracles from its configuration, with
  /// an empty position reported as null. Kamino spells an empty position two ways, the all-zero key and
  /// its `nu111…` null key (the manager writes the latter into every unused position), and the lending
  /// program takes only its own address as none, so both spellings are read as empty here.
  ///
  /// @throws IllegalArgumentException if the reserve names no lending market, since the instruction has a
  ///                                  market account in every reserve's second position
  public static KaminoReserveRefresh of(final Reserve reserve) {
    final var lendingMarket = named(reserve.lendingMarket());
    if (lendingMarket == null) {
      throw new IllegalArgumentException("Reserve " + reserve._address() + " names no lending market, so it cannot be refreshed.");
    }
    final var tokenInfo = reserve.config().tokenInfo();
    return new KaminoReserveRefresh(
        reserve._address(),
        lendingMarket,
        reserve.liquidity().mintPubkey(),
        named(tokenInfo.pythConfiguration().price()),
        named(tokenInfo.switchboardConfiguration().priceAggregator()),
        named(tokenInfo.switchboardConfiguration().twapAggregator()),
        named(tokenInfo.scopeConfiguration().priceFeed())
    );
  }

  private static PublicKey named(final PublicKey slot) {
    return KaminoAccounts.isNullKey(slot) ? null : slot;
  }

  /// This reserve's six accounts in the order the batch declares them: the reserve writable, the market and
  /// the four oracle positions read-only, an empty position carrying `lendingProgram`.
  public List<AccountMeta> accounts(final PublicKey lendingProgram) {
    return List.of(
        AccountMeta.createWrite(reserve),
        AccountMeta.createRead(lendingMarket),
        AccountMeta.createRead(pythOracle == null ? lendingProgram : pythOracle),
        AccountMeta.createRead(switchboardPriceOracle == null ? lendingProgram : switchboardPriceOracle),
        AccountMeta.createRead(switchboardTwapOracle == null ? lendingProgram : switchboardTwapOracle),
        AccountMeta.createRead(scopePrices == null ? lendingProgram : scopePrices)
    );
  }

  /// One `refresh_reserves_batch` over `reserves` in order, each reserve once, with the price updates the
  /// swap's pricing reads not skipped.
  public static Instruction refreshInstruction(final AccountMeta invokedLendingProgram,
                                               final Collection<KaminoReserveRefresh> reserves) {
    final var lendingProgram = invokedLendingProgram.publicKey();
    final var accounts = new ArrayList<AccountMeta>();
    final var seen = new ArrayList<PublicKey>();
    for (final var refresh : reserves) {
      if (!seen.contains(refresh.reserve())) {
        seen.add(refresh.reserve());
        accounts.addAll(refresh.accounts(lendingProgram));
      }
    }
    return KaminoLendingProgram.refreshReservesBatch(invokedLendingProgram, false).extraAccounts(accounts);
  }
}
