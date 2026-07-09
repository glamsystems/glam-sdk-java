package systems.glam.services.execution;

import software.sava.core.tx.Instruction;
import software.sava.core.tx.Transaction;
import software.sava.services.core.net.http.NotifyClient;
import software.sava.services.solana.transactions.InstructionService;
import software.sava.services.solana.transactions.TransactionProcessor;
import software.sava.services.solana.transactions.TxRequest;

import java.util.List;
import java.util.function.Function;

/// Processes instructions in batches which fit within the transaction account limit, delegating
/// to the {@link InstructionService} and halving the batch size when a transaction exceeds the
/// serialized size limit. Fee, compute budget, and retry defaults are resolved by the service.
public interface InstructionProcessor {

  static InstructionProcessor createProcessor(final TransactionProcessor transactionProcessor,
                                              final InstructionService instructionService,
                                              final NotifyClient notifyClient) {
    return new InstructionProcessorImpl(
        transactionProcessor,
        instructionService,
        notifyClient
    );
  }

  NotifyClient notifyClient();

  TransactionProcessor transactionProcessor();

  InstructionService instructionService();

  default boolean processInstructions(final String logContext,
                                      final List<Instruction> instructions,
                                      final Function<List<Instruction>, Transaction> transactionFactory) throws InterruptedException {
    return processInstructions(
        TxRequest.createRequest(instructions, logContext)
            .transactionFactory(transactionFactory)
            .retrySend(true)
    );
  }

  boolean processInstructions(final TxRequest request) throws InterruptedException;
}
