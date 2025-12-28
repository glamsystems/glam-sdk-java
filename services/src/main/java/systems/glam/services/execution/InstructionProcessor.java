package systems.glam.services.execution;

import software.sava.core.accounts.PublicKey;
import software.sava.core.tx.Instruction;
import software.sava.core.tx.Transaction;
import software.sava.services.core.net.http.NotifyClient;
import software.sava.services.solana.transactions.InstructionService;
import software.sava.services.solana.transactions.TransactionProcessor;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public interface InstructionProcessor {

  static InstructionProcessor createProcessor(final TransactionProcessor transactionProcessor,
                                              final InstructionService instructionService,
                                              final BigDecimal maxLamportPriorityFee,
                                              final NotifyClient notifyClient,
                                              final double cuBudgetMultiplier,
                                              final int maxRetries) {
    return new InstructionProcessorImpl(
        transactionProcessor,
        instructionService,
        maxLamportPriorityFee,
        notifyClient,
        cuBudgetMultiplier,
        maxRetries
    );
  }

  NotifyClient notifyClient();

  TransactionProcessor transactionProcessor();

  InstructionService instructionService();

  BigDecimal maxLamportPriorityFee();

  double cuBudgetMultiplier();

  int maxRetries();

  boolean processInstructions(final String logContext,
                              final List<Instruction> instructions,
                              final Collection<PublicKey> lookupTableKeys,
                              final Function<List<Instruction>, Transaction> transactionFactory) throws InterruptedException;

  default boolean processInstructions(final String logContext,
                                      final List<Instruction> instructions,
                                      final Function<List<Instruction>, Transaction> transactionFactory) throws InterruptedException {
    return processInstructions(logContext, instructions, null, transactionFactory);
  }

  boolean processInstructions(final String logContext,
                              final List<Instruction> instructions,
                              final double cuBudgetMultiplier,
                              final BigDecimal maxLamportPriorityFee,
                              final int maxRetries,
                              final Collection<PublicKey> lookupTableKeys,
                              final Function<List<Instruction>, Transaction> transactionFactory) throws InterruptedException;
}
