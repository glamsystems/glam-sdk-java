package systems.glam.services.execution;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.core.tx.Transaction;
import software.sava.rpc.json.http.response.IxError;
import software.sava.rpc.json.http.response.TransactionError;
import software.sava.services.core.net.http.NotifyClient;
import software.sava.services.solana.transactions.InstructionService;
import software.sava.services.solana.transactions.TransactionProcessor;
import software.sava.services.solana.transactions.TransactionResult;
import systems.glam.sdk.GlamAccounts;
import systems.glam.sdk.idl.programs.glam.protocol.gen.GlamProtocolError;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.System.Logger.Level.*;
import static software.sava.rpc.json.http.request.Commitment.CONFIRMED;
import static software.sava.services.solana.transactions.TransactionResult.SIZE_LIMIT_EXCEEDED;

public record InstructionProcessorImpl(TransactionProcessor transactionProcessor,
                                       InstructionService instructionService,
                                       BigDecimal maxLamportPriorityFee,
                                       NotifyClient notifyClient,
                                       double cuBudgetMultiplier,
                                       int maxRetries) implements InstructionProcessor {

  private static final System.Logger logger = System.getLogger(InstructionProcessorImpl.class.getName());

  @Override
  public boolean processInstructions(final String logContext,
                                     final List<Instruction> instructions,
                                     final Collection<PublicKey> lookupTableKeys,
                                     final Function<List<Instruction>, Transaction> transactionFactory) throws InterruptedException {
    return processInstructions(
        logContext,
        instructions,
        cuBudgetMultiplier,
        maxLamportPriorityFee,
        maxRetries,
        lookupTableKeys,
        transactionFactory
    );
  }

  @Override
  public boolean processInstructions(final String logContext,
                                     final List<Instruction> instructions,
                                     final double cuBudgetMultiplier,
                                     final BigDecimal maxLamportPriorityFee,
                                     final int maxRetries,
                                     final Collection<PublicKey> lookupTableKeys,
                                     final Function<List<Instruction>, Transaction> transactionFactory) throws InterruptedException {
    final var distinctAccounts = HashSet.<PublicKey>newHashSet(64);

    for (int batchSize = instructions.size(); ; ) {
      var ixBatch = batchSize < instructions.size()
          ? instructions.subList(0, batchSize)
          : instructions;

      distinctAccounts.clear();
      distinctAccounts.add(SolanaAccounts.MAIN_NET.computeBudgetProgram());
      if (lookupTableKeys != null) {
        distinctAccounts.addAll(lookupTableKeys);
      }
      int numDistinctAccounts = distinctAccounts.size();
      int numInstructions = 0;
      BATCHED:
      for (final var ix : ixBatch) {
        for (final var account : ix.accounts()) {
          if (distinctAccounts.add(account.publicKey())) {
            if (++numDistinctAccounts > 64) {
              if (numInstructions == 0) {
                final var accounts = ix.accounts();
                final var msg = String.format("""
                        {
                         "event": "Instruction Exceeds Account Limit",
                         "program": "%s",
                         "data": "%s",
                         "numTables": %d,
                         "numAccounts": %d,
                         "accounts": ["%s"],
                        }""",
                    ix.programId(),
                    Base64.getEncoder().encodeToString(ix.copyData()),
                    lookupTableKeys == null ? 0 : lookupTableKeys.size(),
                    accounts.size(),
                    accounts.stream()
                        .map(AccountMeta::publicKey)
                        .map(PublicKey::toBase58)
                        .collect(Collectors.joining("\",\""))
                );
                notifyClient.postMsg(msg);
                throw new IllegalStateException(msg);
              }
              ixBatch = ixBatch.subList(0, numInstructions);
              break BATCHED;
            }
          }
        }
        ++numInstructions;
      }

      final TransactionResult txResult;
      try {
        txResult = instructionService.processInstructions(
            cuBudgetMultiplier,
            ixBatch,
            maxLamportPriorityFee,
            CONFIRMED, CONFIRMED,
            true,
            true,
            maxRetries,
            transactionFactory,
            logContext
        );
      } catch (final RuntimeException ex) {
        final var msg = FormatUtil.formatInstructionException(
            String.format("Failed to process %s instructions.", logContext),
            ixBatch,
            lookupTableKeys
        );
        logger.log(ERROR, msg, ex);
        notifyClient.postMsg(msg);
        throw ex;
      }

      ixBatch.clear();

      final var error = txResult.error();
      final var formattedTxResult = FormatUtil.formatTransactionResult(txResult);
      if (error != null) {
        final var msg = String.format("""
                %s Failed
                %s
                """,
            logContext, formattedTxResult
        );
        if (batchSize > 1) {
          if (error == SIZE_LIMIT_EXCEEDED) {
            batchSize = (batchSize & 1) == 1 ? (batchSize >> 1) + 1 : batchSize >> 1;
            logger.log(WARNING, msg);
            continue;
          } else if (error instanceof TransactionError.InstructionError(final int index, final IxError ixError)) {
            if (ixError instanceof IxError.Custom(final long errorId)) {
              final var transaction = txResult.transaction();
              final var failedIx = transaction.instructions().get(index);
              if (failedIx.programId().publicKey().equals(GlamAccounts.MAIN_NET.mintProgram())) {
                final var glamError = GlamProtocolError.getInstance((int) errorId);
                if (glamError instanceof GlamProtocolError.PriceTooOld) {
                  logger.log(WARNING, msg);
                  // TODO: refresh oracles
                  return false; // re-fetch and retry
                }
              }
            }
          }
        }
        notifyClient.postMsg(msg);
        return false;
      } else {
        logger.log(INFO, String.format("""
                    %s Success
                    %s
                    """,
                logContext, formattedTxResult
            )
        );
        if (instructions.isEmpty()) {
          return true;
        }
      }
    }
  }
}
