package systems.glam.services.integrations.kamino;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.idl.clients.kamino.KaminoAccounts;
import software.sava.idl.clients.kamino.lend.gen.KaminoLendingProgram;
import software.sava.idl.clients.kamino.lend.gen.types.Obligation;
import software.sava.idl.clients.kamino.lend.gen.types.ObligationCollateral;
import software.sava.idl.clients.kamino.lend.gen.types.ObligationLiquidity;
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

      returnAccounts.add(obligationKey);
      final byte[] data = obligationAccount.data();
      final var lendingMarket = PublicKey.readPubKey(data, Obligation.LENDING_MARKET_OFFSET);
      returnAccounts.add(lendingMarket);

      final var reserveAccounts = new ArrayList<AccountMeta>(Obligation.DEPOSITS_LEN + Obligation.BORROWS_LEN);

      for (int i = 0, offset = Obligation.DEPOSITS_OFFSET; i < Obligation.DEPOSITS_LEN; ++i, offset += ObligationCollateral.BYTES) {
        final var reserve = PublicKey.readPubKey(data, offset);
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

      for (int i = 0, offset = Obligation.BORROWS_OFFSET; i < Obligation.BORROWS_LEN; ++i, offset += ObligationLiquidity.BYTES) {
        final var reserve = PublicKey.readPubKey(data, offset);
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
        kLendProgram.publicKey(),
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
