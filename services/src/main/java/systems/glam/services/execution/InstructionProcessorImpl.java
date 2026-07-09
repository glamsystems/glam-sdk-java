package systems.glam.services.execution;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Transaction;
import software.sava.rpc.json.http.response.IxError;
import software.sava.rpc.json.http.response.TransactionError;
import software.sava.services.core.net.http.NotifyClient;
import software.sava.services.solana.transactions.InstructionService;
import software.sava.services.solana.transactions.TransactionProcessor;
import software.sava.services.solana.transactions.TransactionResult;
import software.sava.services.solana.transactions.TxRequest;
import systems.glam.sdk.GlamAccounts;
import systems.glam.sdk.idl.programs.glam.protocol.gen.GlamProtocolError;

import java.util.Base64;
import java.util.HashSet;
import java.util.stream.Collectors;

import static java.lang.System.Logger.Level.*;

public record InstructionProcessorImpl(TransactionProcessor transactionProcessor,
                                       InstructionService instructionService,
                                       NotifyClient notifyClient) implements InstructionProcessor {

  private static final System.Logger logger = System.getLogger(InstructionProcessorImpl.class.getName());

  @Override
  public boolean processInstructions(final TxRequest request) throws InterruptedException {
    final var instructions = request.instructions();
    final var logContext = request.logContext();
    final var distinctAccounts = HashSet.<PublicKey>newHashSet(Transaction.MAX_ACCOUNTS);

    for (int batchSize = instructions.size(); ; ) {
      var ixBatch = batchSize < instructions.size()
          ? instructions.subList(0, batchSize)
          : instructions;

      // The v1 account limit counts the fee payer, program accounts, and instruction accounts.
      distinctAccounts.clear();
      distinctAccounts.add(transactionProcessor.feePayer());
      int numDistinctAccounts = distinctAccounts.size();
      int numInstructions = 0;
      BATCHED:
      for (final var ix : ixBatch) {
        if (distinctAccounts.add(ix.programId().publicKey())) {
          if (++numDistinctAccounts > Transaction.MAX_ACCOUNTS && numInstructions > 0) {
            ixBatch = ixBatch.subList(0, numInstructions);
            break BATCHED;
          }
        }
        for (final var account : ix.accounts()) {
          if (distinctAccounts.add(account.publicKey())) {
            if (++numDistinctAccounts > Transaction.MAX_ACCOUNTS) {
              if (numInstructions == 0) {
                final var accounts = ix.accounts();
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
        }
        ++numInstructions;
      }

      final TransactionResult txResult;
      try {
        txResult = instructionService.process(request.instructions(ixBatch));
      } catch (final RuntimeException ex) {
        final var msg = FormatUtil.formatInstructionException(
            String.format("Failed to process %s instructions.", logContext),
            ixBatch
        );
        logger.log(ERROR, msg, ex);
        notifyClient.postMsg(msg);
        throw ex;
      }

      ixBatch.clear();

      final var formattedTxResult = FormatUtil.formatTransactionResult(txResult);
      switch (txResult.outcome()) {
        case SENT -> {
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
        case SIZE_LIMIT_EXCEEDED -> {
          final var msg = String.format("""
                  %s Failed
                  %s
                  """,
              logContext, formattedTxResult
          );
          if (batchSize > 1) {
            batchSize = (batchSize & 1) == 1 ? (batchSize >> 1) + 1 : batchSize >> 1;
            logger.log(WARNING, msg);
          } else {
            notifyClient.postMsg(msg);
            return false;
          }
        }
        case FAILED -> {
          final var msg = String.format("""
                  %s Failed
                  %s
                  """,
              logContext, formattedTxResult
          );
          if (txResult.error() instanceof TransactionError.InstructionError(final int index, final IxError ixError)
              && ixError instanceof IxError.Custom(final long errorId)) {
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
          notifyClient.postMsg(msg);
          return false;
        }
        case SIMULATION_FAILED, EXPIRED, BLOCK_HASH_UNAVAILABLE -> {
          final var msg = String.format("""
                  %s Failed
                  %s
                  """,
              logContext, formattedTxResult
          );
          notifyClient.postMsg(msg);
          return false;
        }
      }
    }
  }
}
