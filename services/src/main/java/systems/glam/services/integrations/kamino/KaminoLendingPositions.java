package systems.glam.services.integrations.kamino;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.kamino.KaminoAccounts;
import software.sava.idl.clients.kamino.lend.gen.KaminoLendingProgram;
import software.sava.idl.clients.kamino.lend.gen.types.Obligation;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.InnerInstructions;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.services.integrations.IntegrationServiceContext;
import systems.glam.services.pricing.accounting.AggregatePositionReport;
import systems.glam.services.pricing.accounting.Position;
import systems.glam.services.pricing.accounting.PositionReportNode;
import systems.glam.services.rpc.AccountFetcher;
import systems.glam.services.state.MinGlamStateAccount;

import java.math.BigDecimal;
import java.util.*;

public final class KaminoLendingPositions implements Position {

  private final Instruction refreshReserveBatchIx;
  private final Map<PublicKey, AccountMeta> obligationAccounts;

  public KaminoLendingPositions(final KaminoAccounts kaminoAccounts) {
    this.refreshReserveBatchIx = KaminoLendingProgram.refreshReservesBatch(kaminoAccounts.invokedKLendProgram(), false);
    this.obligationAccounts = HashMap.newHashMap(8);
  }

  public void addAccount(final PublicKey obligationAccount) {
    obligationAccounts.put(obligationAccount, AccountMeta.createWrite(obligationAccount));
  }

  @Override
  public void removeAccount(final PublicKey account) {
    obligationAccounts.remove(account);
  }

  @Override
  public void accountsForPriceInstruction(final Set<PublicKey> keys) {
    keys.addAll(obligationAccounts.keySet());
  }

  @Override
  public boolean priceInstruction(final IntegrationServiceContext serviceContext,
                                  final GlamAccountClient glamAccountClient,
                                  final PublicKey solUSDOracleKey,
                                  final PublicKey baseAssetUSDOracleKey,
                                  final MinGlamStateAccount stateAccount,
                                  final Map<PublicKey, AccountInfo<byte[]>> accountMap,
                                  final SequencedCollection<Instruction> priceInstructions,
                                  final Set<PublicKey> returnAccounts) {
    final var kLendProgram = serviceContext.kaminoAccounts().invokedKLendProgram();
    final var kaminoCache = serviceContext.kaminoCache();
    final int numObligations = obligationAccounts.size();

    final var refreshObligationInstructions = new ArrayList<Instruction>(numObligations);
    final var refreshReservesAccounts = new ArrayList<AccountMeta>(numObligations << 4);
    final var priceObligationAccounts = new ArrayList<AccountMeta>(numObligations);
    for (final var entry : obligationAccounts.entrySet()) {
      final var obligationKey = entry.getKey();
      final var obligationAccount = accountMap.get(obligationKey);
      if (AccountFetcher.isNull(obligationAccount)) {
        continue;
      }
      priceObligationAccounts.add(entry.getValue());

      final var obligation = Obligation.read(obligationAccount);
      returnAccounts.add(obligationKey);
      final var lendingMarket = obligation.lendingMarket();
      returnAccounts.add(lendingMarket);

      final var reserveAccounts = new ArrayList<AccountMeta>(Obligation.DEPOSITS_LEN + Obligation.BORROWS_LEN);
      for (final var deposit : obligation.deposits()) {
        final var reserve = deposit.depositReserve();
        if (reserve.equals(PublicKey.NONE)) {
          break;
        }
        if (returnAccounts.add(reserve)) {
          final var reserveContext = kaminoCache.reserveContext(reserve);
          if (reserveContext == null) {
            return false;
          } else {
            reserveContext.refreshReserveAccounts(refreshReservesAccounts);
            reserveAccounts.add(reserveContext.writeReserve());
          }
        }
      }

      for (final var borrow : obligation.borrows()) {
        final var reserve = borrow.borrowReserve();
        if (reserve.equals(PublicKey.NONE)) {
          break;
        }
        if (returnAccounts.add(reserve)) {
          final var reserveContext = kaminoCache.reserveContext(reserve);
          if (reserveContext == null) {
            return false;
          } else {
            reserveContext.refreshReserveAccounts(refreshReservesAccounts);
            reserveAccounts.add(reserveContext.writeReserve());
          }
        }
      }

      final var refreshObligationIx = KaminoLendingProgram.refreshObligation(
          kLendProgram,
          lendingMarket,
          obligationKey
      ).extraAccounts(reserveAccounts);
      refreshObligationInstructions.add(refreshObligationIx);
    }

    final var refreshReserveBatchIx = this.refreshReserveBatchIx.extraAccounts(refreshReservesAccounts);
    priceInstructions.add(refreshReserveBatchIx);
    priceInstructions.addAll(refreshObligationInstructions);

    final var priceIx = glamAccountClient.priceKaminoObligations(
        serviceContext.kLendProgram(),
        solUSDOracleKey, baseAssetUSDOracleKey,
        true
    ).extraAccounts(priceObligationAccounts);
    priceInstructions.add(priceIx);

    return true;
  }

  @Override
  public int positionReport(final IntegrationServiceContext serviceContext,
                            final PublicKey mintProgram,
                            final MinGlamStateAccount stateAccount,
                            final Map<PublicKey, AccountInfo<byte[]>> returnedAccountsMap,
                            final int ixIndex,
                            final List<Instruction> priceInstructions,
                            final InnerInstructions[] innerInstructionsArray,
                            final Map<PublicKey, BigDecimal> assetPrices,
                            final List<AggregatePositionReport> positionReportsList) {
    final int priceIxIndex = ixIndex + 1 + obligationAccounts.size();
    final var innerInstructions = innerInstructionsArray[priceIxIndex];
    final var positionAmount = Position.parseAnchorEvent(innerInstructions, mintProgram, stateAccount.baseAssetDecimals());
    final var reportNode = new PositionReportNode(positionAmount, List.of());
    positionReportsList.add(reportNode);
    return priceIxIndex + 1;
  }
}
