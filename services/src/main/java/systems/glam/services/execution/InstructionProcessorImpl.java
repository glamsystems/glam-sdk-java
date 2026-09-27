package systems.glam.services.execution;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.core.tx.Transaction;
import software.sava.rpc.json.http.response.IxError;
import software.sava.rpc.json.http.response.TransactionError;
import software.sava.services.core.net.http.NotifyClient;
import software.sava.services.solana.transactions.InstructionService;
import software.sava.services.solana.transactions.TransactionProcessor;
import software.sava.services.solana.transactions.TransactionResult;
import systems.glam.sdk.idl.programs.glam.protocol.gen.GlamProtocolError;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.System.Logger.Level.*;
import static software.sava.rpc.json.http.request.Commitment.CONFIRMED;
import static software.sava.services.solana.transactions.TransactionResult.SIZE_LIMIT_EXCEEDED;

public record InstructionProcessorImpl(TransactionProcessor transactionProcessor,
                                       InstructionService instructionService,
                                       PublicKey mintProgram,
                                       BigDecimal maxLamportPriorityFee,
                                       NotifyClient notifyClient,
                                       double cuBudgetMultiplier,
                                       int maxRetries) implements InstructionProcessor {

  private static final System.Logger logger = System.getLogger(InstructionProcessorImpl.class.getName());

  /// Adds `key` to the accounts a transaction carries unless it is already among them or they are at
  /// the limit: false exactly when the transaction cannot take `key`. A key already carried always
  /// fits, even in a full transaction.
  static boolean fits(final Set<PublicKey> accounts, final PublicKey key) {
    if (accounts.contains(key)) {
      return true;
    }
    if (accounts.size() == Transaction.MAX_ACCOUNTS) {
      return false;
    }
    accounts.add(key);
    return true;
  }

  @Override
  public boolean processInstructions(final String logContext,
                                     final List<Instruction> instructions) throws InterruptedException {
    return processInstructions(
        logContext,
        instructions,
        cuBudgetMultiplier,
        maxLamportPriorityFee,
        maxRetries
    );
  }

  @Override
  public boolean processInstructions(final String logContext,
                                     final List<Instruction> instructions,
                                     final double cuBudgetMultiplier,
                                     final BigDecimal maxLamportPriorityFee,
                                     final int maxRetries) throws InterruptedException {
    final var feePayer = transactionProcessor.feePayer();
    final var distinctAccounts = HashSet.<PublicKey>newHashSet(Transaction.MAX_ACCOUNTS);

    for (int batchSize = instructions.size(); ; ) {
      var ixBatch = batchSize < instructions.size()
          ? instructions.subList(0, batchSize)
          : instructions;

      // what the v1 transaction carries against its account limit: the fee payer, every program
      // invoked and every instruction account, each once. Compute budget rides as ConfigValues,
      // so no ComputeBudget program (and no instruction of it) takes a slot.
      distinctAccounts.clear();
      distinctAccounts.add(feePayer);
      int numInstructions = 0;
      BATCHED:
      for (final var ix : ixBatch) {
        final var accounts = ix.accounts();
        // the invoked program first, then the instruction's accounts
        for (int i = -1, numAccounts = accounts.size(); i < numAccounts; ++i) {
          final var key = i < 0 ? ix.programId().publicKey() : accounts.get(i).publicKey();
          if (!fits(distinctAccounts, key)) {
            if (numInstructions == 0) {
              final var msg = String.format("""
                      {
                       "event": "Instruction Exceeds Account Limit",
                       "program": "%s",
                       "data": "%s",
                       "numAccounts": %d,
                       "accounts": ["%s"],
                      }""",
                  ix.programId(),
                  Base64.getEncoder().encodeToString(ix.copyData()),
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
            logContext
        );
      } catch (final RuntimeException ex) {
        final var msg = FormatUtil.formatInstructionException(
            String.format("Failed to process %s instructions.", logContext),
            ixBatch
        );
        logger.log(ERROR, msg, ex);
        notifyClient.postMsg(msg);
        throw ex;
      }

      // formatted before the batch is cleared below: ravina's result has kept its own copy of the
      // batch since d0262bc, but one that held the list itself would report zero instructions
      final var formattedTxResult = FormatUtil.formatTransactionResult(txResult);

      // Deliberately cleared before the error check: failed batches are
      // DROPPED, never retried here — retries below the send belong to the
      // InstructionService (maxRetries), and a failed result means the caller
      // re-fetches state and rebuilds. The size-limit halving below therefore
      // applies to the REMAINING instructions, not the dropped batch.
      ixBatch.clear();

      final var error = txResult.error();
      if (error != null) {
        final var msg = String.format("""
                %s Failed
                %s
                """,
            logContext, formattedTxResult
        );
        if (error == SIZE_LIMIT_EXCEEDED) {
          // batchSize bounds the batch, which the account limit may have cut shorter: a bound of
          // one cannot halve, and the refusal falls through to the page below
          if (batchSize > 1) {
            batchSize = (batchSize & 1) == 1 ? (batchSize >> 1) + 1 : batchSize >> 1;
            logger.log(WARNING, msg);
            if (instructions.isEmpty()) {
              // the refused batch was the whole remaining list: nothing is left to send at the
              // halved size, and an empty transaction must never go out in its place
              notifyClient.postMsg(msg);
              return false;
            }
            continue;
          }
        } else if (error instanceof TransactionError.InstructionError(final int index, final IxError ixError)) {
          // PriceTooOld is the protocol program's error, reaching a mint instruction through its
          // pricing CPI; a simulation failure arrives here too, with the simulated transaction
          if (ixError instanceof IxError.Custom(final long errorId)) {
            final var transaction = txResult.transaction();
            final var failedIx = transaction.instructions().get(index);
            if (failedIx.programId().publicKey().equals(mintProgram)) {
              final var glamError = GlamProtocolError.getInstance((int) errorId);
              if (glamError instanceof GlamProtocolError.PriceTooOld) {
                logger.log(WARNING, msg);
                // TODO: refresh oracles
                return false; // re-fetch and retry
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
