package systems.glam.services.pricing.accounting;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.LookupTableAccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.core.tx.Transaction;
import software.sava.rpc.json.http.request.Commitment;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.IxError;
import software.sava.rpc.json.http.response.TransactionError;
import software.sava.rpc.json.http.response.TxSimulation;
import software.sava.services.core.net.http.NotifyClient;
import software.sava.services.solana.alt.ScoredTableMeta;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.sdk.GlamAccountClient;
import systems.glam.sdk.idl.programs.glam.config.gen.types.AssetMeta;
import systems.glam.sdk.idl.programs.glam.protocol.gen.GlamProtocolError;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateAccount;
import systems.glam.services.pricing.exceptions.GlamProtocolException;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static java.lang.System.Logger.Level.ERROR;
import static software.sava.core.accounts.SolanaAccounts.MAIN_NET;
import static software.sava.solana.programs.compute_budget.ComputeBudgetProgram.MAX_COMPUTE_BUDGET;
import static software.sava.solana.programs.compute_budget.ComputeBudgetProgram.setComputeUnitLimit;
import static systems.glam.services.execution.FormatUtil.formatSimulation;

public final class TxSimulationPriceGroup implements PriceGroup {

  private static final System.Logger logger = System.getLogger(TxSimulationPriceGroup.class.getName());
  public static final Instruction MAIN_NET_CU_LIMIT_SIMULATION = setComputeUnitLimit(MAIN_NET.invokedComputeBudgetProgram(), MAX_COMPUTE_BUDGET);

  private final NotifyClient notifyClient;
  private final RpcCaller rpcCaller;
  private final GlamAccountClient glamClient;
  private final List<TxSimulationPriceGroupConstituent> priceGroups;
  private final LookupTableAccountMeta[] tableAccountMetas;

  public TxSimulationPriceGroup(final NotifyClient notifyClient,
                                final RpcCaller rpcCaller,
                                final GlamAccountClient glamClient,
                                final List<TxSimulationPriceGroupConstituent> priceGroups,
                                final LookupTableAccountMeta[] tableAccountMetas) {
    this.notifyClient = notifyClient;
    this.rpcCaller = rpcCaller;
    this.glamClient = glamClient;
    this.priceGroups = priceGroups;
    this.tableAccountMetas = tableAccountMetas;
  }

  public TxSimulationPriceGroup(final NotifyClient notifyClient,
                                final RpcCaller rpcCaller,
                                final GlamAccountClient glamClient,
                                final List<TxSimulationPriceGroupConstituent> priceGroups) {
    this(notifyClient, rpcCaller, glamClient, priceGroups, null);
  }

  public LookupTableAccountMeta[] tableAccountMetas() {
    return tableAccountMetas;
  }

  @Override
  public boolean priceInstructions(final Map<PublicKey, AccountInfo<byte[]>> accounts,
                                   final Map<PublicKey, AssetMeta> assetMetaMap,
                                   final StateAccount stateAccount,
                                   final Set<PublicKey> externalVaultAccounts,
                                   final List<Instruction> instructions) {
    for (final var priceGroup : priceGroups) {
      if (!priceGroup.priceInstructions(accounts, assetMetaMap, stateAccount, externalVaultAccounts, instructions)) {
        return false;
      }
    }
    return true;
  }

  public TxSimulationPriceGroup scoreTables(final Map<PublicKey, AccountInfo<byte[]>> accounts,
                                            final Map<PublicKey, AssetMeta> assetMetaMap,
                                            final List<Instruction> instructions,
                                            final LookupTableAccountMeta[] tables) {
    final var stateAccountKey = glamClient.vaultAccounts().glamStateKey();
    final var stateAccount = StateAccount.read(stateAccountKey, accounts.get(stateAccountKey).data());
    priceInstructions(accounts, assetMetaMap, stateAccount, null, instructions);
    final var invokedOrSignerKeys = HashSet.<PublicKey>newHashSet(1 + instructions.size());
    invokedOrSignerKeys.add(glamClient.feePayer().publicKey());
    int numAccounts = 0;
    for (final var ix : instructions) {
      invokedOrSignerKeys.add(ix.programId().publicKey());
      numAccounts += ix.accounts().size();
    }
    final var accountsToIndex = HashSet.<PublicKey>newHashSet(numAccounts);
    for (final var ix : instructions) {
      for (final var account : ix.accounts()) {
        final var key = account.publicKey();
        if (!invokedOrSignerKeys.contains(key)) {
          accountsToIndex.add(key);
        }
      }
    }

    final var scoredTables = ScoredTableMeta.scoreTables(5, accountsToIndex, tables);
    return new TxSimulationPriceGroup(
        notifyClient, rpcCaller, glamClient, priceGroups,
        scoredTables.toArray(LookupTableAccountMeta[]::new)
    );
  }

  private Transaction createTransaction(final Map<PublicKey, AccountInfo<byte[]>> accounts,
                                        final Set<PublicKey> externalVaultAccounts,
                                        final Map<PublicKey, AssetMeta> assetMetaMap,
                                        final StateAccount stateAccount) {
    final var instructions = new ArrayList<Instruction>(1 + priceGroups.size());
    instructions.add(MAIN_NET_CU_LIMIT_SIMULATION);
    priceInstructions(accounts, assetMetaMap, stateAccount, externalVaultAccounts, instructions);
    return Transaction.createTx(glamClient.feePayer(), instructions, tableAccountMetas);
  }

  private CompletableFuture<TxSimulation> simulationTx(final Map<PublicKey, AccountInfo<byte[]>> accounts,
                                                       final Set<PublicKey> externalVaultAccounts,
                                                       final Map<PublicKey, AssetMeta> assetMetaMap,
                                                       final StateAccount stateAccount) {
    final var transaction = createTransaction(accounts, externalVaultAccounts, assetMetaMap, stateAccount);
    final var returnAccountsSet = HashSet.<PublicKey>newHashSet(64);
    for (final var ix : transaction.instructions()) {
      for (final var account : ix.accounts()) {
        returnAccountsSet.add(account.publicKey());
      }
    }
    final var returnAccounts = List.copyOf(returnAccountsSet);
    final var base64Encoded = transaction.base64EncodeToString();
    return rpcCaller.courteousCall(
        rpcClient -> rpcClient.simulateTransaction(
            Commitment.PROCESSED, base64Encoded,
            true, true,
            returnAccounts
        ),
        "rpcClient::simulatePriceGLAMVault"
    ).thenApply(txSimulation -> {
      if (txSimulation.error() instanceof TransactionError.InstructionError(_, final IxError ixError)) {
        if (ixError instanceof IxError.Custom(final long errorCode)) {
          try {
            final var error = GlamProtocolError.getInstance(Math.toIntExact(errorCode));
            if (error == null) {
              throw notifyPriceException(transaction, txSimulation, ("Unknown error code: " + ixError));
            }
            return switch (error) {
              case GlamProtocolError.PriceTooOld _ -> null; // The main loop will break out and retry.
              case GlamProtocolError.InvalidPricingOracle _ -> throw new GlamProtocolException(error);
              default -> throw notifyPriceException(transaction, txSimulation, ("Unhandled error: " + error));
            };
          } catch (final RuntimeException e) {
            throw notifyPriceException(transaction, txSimulation, ("Failed to parse error: " + ixError));
          }
        } else {
          throw notifyPriceException(transaction, txSimulation, txSimulation.logs().getLast());
        }
      } else {
        return txSimulation;
      }
    });
  }

  private RuntimeException notifyPriceException(final Transaction transaction,
                                                final TxSimulation txSimulation,
                                                final String exceptionMsg) {
    final var msg = formatSimulation("Price Simulation Failed", transaction, txSimulation);
    logger.log(ERROR, msg);
    notifyClient.postMsg(msg);
    throw new IllegalStateException(exceptionMsg);
  }

  @Override
  public boolean pricePositions(final Map<PublicKey, AccountInfo<byte[]>> accounts,
                                final Map<PublicKey, AssetMeta> assetMetaMap,
                                final StateAccount stateAccount,
                                final Set<PublicKey> externalVaultAccounts) {
    final var txSimulationFuture = simulationTx(accounts, externalVaultAccounts, assetMetaMap, stateAccount);

    final var txSimulation = txSimulationFuture.join();

    final var simulationAccounts = txSimulation.accounts();
    final var simulationAccountMap = HashMap.<PublicKey, AccountInfo<byte[]>>newHashMap(simulationAccounts.size());
    for (final var accountInfo : simulationAccounts) {
      simulationAccountMap.put(accountInfo.pubKey(), accountInfo);
    }

    // TODO: Parse Oracle Prices

    for (final var priceGroup : priceGroups) {
      priceGroup.valuePositions(simulationAccountMap, null, txSimulation);
    }

    return false;
  }
}
