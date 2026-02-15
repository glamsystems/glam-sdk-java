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
  private final Map<PublicKey, AccountMeta> vaultTokenAccounts;

  public KaminoLendingPositions(final KaminoAccounts kaminoAccounts) {
    this.refreshReserveBatchIx = KaminoLendingProgram.refreshReservesBatch(kaminoAccounts.invokedKLendProgram(), false);
    this.obligationAccounts = new HashMap<>();
    this.vaultTokenAccounts = new HashMap<>();
  }

  public void addObligation(final PublicKey obligationAccount) {
    obligationAccounts.put(obligationAccount, AccountMeta.createWrite(obligationAccount));
  }

  public void addVaultTokenAccount(final PublicKey vaultTokenAccount, final KaminoVaultContext kVaultContext) {
    vaultTokenAccounts.put(kVaultContext.sharesMint(), AccountMeta.createRead(vaultTokenAccount));
  }

  @Override
  public void removeAccount(final PublicKey account) {
    if (obligationAccounts.remove(account) == null) {
      final var iterator = vaultTokenAccounts.entrySet().iterator();
      while (iterator.hasNext()) {
        if (iterator.next().getValue().publicKey().equals(account)) {
          iterator.remove();
          return;
        }
      }
    }
  }

  public boolean isVaultTokenAccount(final PublicKey vaultTokenAccount) {
    return !obligationAccounts.containsKey(vaultTokenAccount)
        && vaultTokenAccounts.values().stream().anyMatch(meta -> meta.publicKey().equals(vaultTokenAccount));
  }


  public PublicKey sharesMint(final PublicKey vaultTokenAccount) {
    if (!obligationAccounts.containsKey(vaultTokenAccount)) {
      for (final var entry : vaultTokenAccounts.entrySet()) {
        if (entry.getValue().publicKey().equals(vaultTokenAccount)) {
          return entry.getKey();
        }
      }
    }
    return null;
  }

  private static boolean addReserve(final Set<PublicKey> returnAccounts,
                                    final List<AccountMeta> refreshReservesAccounts,
                                    final List<AccountMeta> reserveAccounts,
                                    final KaminoCache kaminoCache,
                                    final PublicKey reserve) {
    if (returnAccounts.add(reserve)) {
      final var reserveContext = kaminoCache.reserveContext(reserve);
      if (reserveContext == null) {
        return false;
      } else {
        reserveContext.refreshReserveAccounts(refreshReservesAccounts);
        reserveAccounts.add(reserveContext.writeReserve());
      }
    }
    return true;
  }

  private boolean priceKaminoObligations(final GlamAccountClient glamAccountClient,
                                         final PublicKey solUSDOracleKey,
                                         final PublicKey baseAssetUSDOracleKey,
                                         final Map<PublicKey, AccountInfo<byte[]>> accountMap,
                                         final Set<PublicKey> returnAccounts,
                                         final KaminoCache kaminoCache,
                                         final AccountMeta kLendProgram,
                                         final int numObligations,
                                         final List<Instruction> instructions,
                                         final List<AccountMeta> refreshReservesAccounts) {
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
        } else if (!addReserve(returnAccounts, refreshReservesAccounts, reserveAccounts, kaminoCache, reserve)) {
          return false;
        }
      }

      for (int i = 0, offset = Obligation.BORROWS_OFFSET; i < Obligation.BORROWS_LEN; ++i, offset += ObligationLiquidity.BYTES) {
        final var reserve = PublicKey.readPubKey(data, offset);
        if (reserve.equals(PublicKey.NONE)) {
          break;
        } else if (!addReserve(returnAccounts, refreshReservesAccounts, reserveAccounts, kaminoCache, reserve)) {
          return false;
        }
      }

      final var refreshObligationIx = KaminoLendingProgram.refreshObligation(
          kLendProgram,
          lendingMarket,
          obligationKey
      ).extraAccounts(reserveAccounts);
      instructions.add(refreshObligationIx);
    }

    final var priceIx = glamAccountClient.priceKaminoObligations(
        kLendProgram.publicKey(),
        solUSDOracleKey, baseAssetUSDOracleKey,
        true
    ).extraAccounts(priceObligationAccounts);
    instructions.add(priceIx);

    return true;
  }

  private Instruction priceVaults(final IntegrationServiceContext serviceContext,
                                  final GlamAccountClient glamAccountClient,
                                  final PublicKey solUSDOracleKey,
                                  final PublicKey baseAssetUSDOracleKey,
                                  final Set<PublicKey> returnAccounts,
                                  final KaminoCache kaminoCache,
                                  final int numVaults,
                                  final List<AccountMeta> refreshReservesAccounts) {
    final var extraAccounts = new ArrayList<AccountMeta>(numVaults << 2);
    int numReserves = 0;
    for (final var entry : vaultTokenAccounts.entrySet()) {
      extraAccounts.add(entry.getValue());
      final var vaultContext = kaminoCache.vaultForShareMint(entry.getKey());
      extraAccounts.add(vaultContext.readSharesMint());
      extraAccounts.add(vaultContext.readVaultState());
      final var assetMeta = serviceContext.globalConfigAssetMeta(vaultContext.tokenMint());
      extraAccounts.add(assetMeta.readOracle());
      numReserves += vaultContext.numReserves();
    }

    final var priceIx = glamAccountClient.priceKaminoVaultShares(
        solUSDOracleKey,
        baseAssetUSDOracleKey,
        numVaults,
        true
    ).extraAccounts(extraAccounts);

    final var reserveAccounts = new ArrayList<AccountMeta>(numReserves);
    for (final var shareMint : vaultTokenAccounts.keySet()) {
      final var vaultContext = kaminoCache.vaultForShareMint(shareMint);
      for (final var reserve : vaultContext.reserves()) {
        if (!addReserve(returnAccounts, refreshReservesAccounts, reserveAccounts, kaminoCache, reserve)) {
          return null;
        }
      }
    }

    return priceIx.extraAccounts(reserveAccounts);
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
    final var kaminoCache = serviceContext.kaminoCache();
    final int numObligations = obligationAccounts.size();
    final int numVaults = vaultTokenAccounts.size();

    final var refreshReservesAccounts = new ArrayList<AccountMeta>((numObligations + numVaults) << 4);

    final var instructions = new ArrayList<Instruction>(1 + numObligations + numVaults);
    if (numObligations > 0) {
      if (!priceKaminoObligations(
          glamAccountClient,
          solUSDOracleKey, baseAssetUSDOracleKey,
          accountMap,
          returnAccounts,
          kaminoCache,
          serviceContext.kaminoAccounts().invokedKLendProgram(),
          numObligations,
          instructions,
          refreshReservesAccounts
      )) {
        return false;
      }
    }

    if (numVaults > 0) {
      final var priceIx = priceVaults(
          serviceContext,
          glamAccountClient,
          solUSDOracleKey, baseAssetUSDOracleKey,
          returnAccounts,
          kaminoCache,
          numVaults,
          refreshReservesAccounts
      );
      if (priceIx == null) {
        return false;
      } else {
        instructions.add(priceIx);
      }
    }

    final var refreshReserveBatchIx = this.refreshReserveBatchIx.extraAccounts(refreshReservesAccounts);
    priceInstructions.add(refreshReserveBatchIx);
    priceInstructions.addAll(instructions);

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
    int priceIxIndex = ixIndex + 1; // refresh reserve batch instruction.
    final int numObligations = obligationAccounts.size();
    if (numObligations > 0) {
      priceIxIndex += numObligations;
      final var innerInstructions = innerInstructionsArray[priceIxIndex];
      final var positionAmount = Position.parseAnchorEvent(innerInstructions, mintProgram, stateAccount.baseAssetDecimals());
      final var reportNode = new PositionReportNode(positionAmount, List.of());
      positionReportsList.add(reportNode);
      ++priceIxIndex;
    }
    final int numVaults = vaultTokenAccounts.size();
    if (numVaults > 0) {
      final var innerInstructions = innerInstructionsArray[priceIxIndex];
      final var positionAmount = Position.parseAnchorEvent(innerInstructions, mintProgram, stateAccount.baseAssetDecimals());
      final var reportNode = new PositionReportNode(positionAmount, List.of());
      positionReportsList.add(reportNode);
      ++priceIxIndex;
    }

    return priceIxIndex;
  }
}
