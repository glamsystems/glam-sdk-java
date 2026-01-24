package systems.glam.services.execution;

import software.sava.core.tx.Instruction;
import software.sava.core.tx.Transaction;
import software.sava.services.solana.epoch.EpochInfoService;
import systems.glam.services.ServiceContext;

import java.util.List;
import java.util.function.Function;

final class ExecutionServiceContextImpl extends BaseServiceContext implements ExecutionServiceContext {

  private final EpochInfoService epochInfoService;
  private final InstructionProcessor instructionProcessor;
  private final Function<List<Instruction>, Transaction> transactionFactory;

  ExecutionServiceContextImpl(final ServiceContext serviceContext,
                              final EpochInfoService epochInfoService,
                              final InstructionProcessor instructionProcessor,
                              final Function<List<Instruction>, Transaction> transactionFactory) {
    super(serviceContext);
    this.epochInfoService = epochInfoService;
    this.instructionProcessor = instructionProcessor;
    this.transactionFactory = transactionFactory;
  }

  @Override
  public boolean feePayerBalanceLow() {
    return serviceContext.feePayerBalanceLow();
  }

  @Override
  public EpochInfoService epochInfoService() {
    return epochInfoService;
  }

  @Override
  public InstructionProcessor instructionProcessor() {
    return instructionProcessor;
  }

  @Override
  public Function<List<Instruction>, Transaction> transactionFactory() {
    return transactionFactory;
  }

  @Override
  public long medianMillisPerSlot() {
    return epochInfoService.epochInfo().medianMillisPerSlot();
  }

  @Override
  public boolean processInstructions(final String logContext,
                                     final List<Instruction> instructions) throws InterruptedException {
    return instructionProcessor.processInstructions(logContext, instructions, transactionFactory);
  }

}
