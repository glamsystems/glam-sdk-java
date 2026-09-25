package systems.glam.services.execution;

import software.sava.core.tx.Instruction;
import software.sava.services.solana.epoch.EpochInfoService;
import systems.glam.services.ServiceContext;

import java.util.List;

final class ExecutionServiceContextImpl extends BaseServiceContext implements ExecutionServiceContext {

  private final EpochInfoService epochInfoService;
  private final InstructionProcessor instructionProcessor;

  ExecutionServiceContextImpl(final ServiceContext serviceContext,
                              final EpochInfoService epochInfoService,
                              final InstructionProcessor instructionProcessor) {
    super(serviceContext);
    this.epochInfoService = epochInfoService;
    this.instructionProcessor = instructionProcessor;
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
  public long medianMillisPerSlot() {
    return epochInfoService.epochInfo().medianMillisPerSlot();
  }

  @Override
  public boolean processInstructions(final String logContext,
                                     final List<Instruction> instructions) throws InterruptedException {
    return instructionProcessor.processInstructions(logContext, instructions);
  }

}
