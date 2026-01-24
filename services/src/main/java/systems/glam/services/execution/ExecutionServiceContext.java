package systems.glam.services.execution;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.sysvar.Clock;
import software.sava.core.tx.Instruction;
import software.sava.core.tx.Transaction;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.services.solana.epoch.EpochInfoService;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.services.ServiceContext;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public interface ExecutionServiceContext {

  static ExecutionServiceContext createContext(final ServiceContext serviceContext,
                                               final EpochInfoService epochInfoService,
                                               final InstructionProcessor instructionProcessor,
                                               final Function<List<Instruction>, Transaction> transactionFactory) {
    return new ExecutionServiceContextImpl(
        serviceContext,
        epochInfoService,
        instructionProcessor,
        transactionFactory
    );
  }

  long minCheckStateDelayNanos();

  long maxCheckStateDelayNanos();

  Clock clock(final Map<PublicKey, AccountInfo<byte[]>> accountsNeededMap);

  boolean feePayerBalanceLow();

  RpcCaller rpcCaller();

  EpochInfoService epochInfoService();

  InstructionProcessor instructionProcessor();

  Function<List<Instruction>, Transaction> transactionFactory();

  long medianMillisPerSlot();

  boolean processInstructions(final String logContext,
                              final List<Instruction> instructions) throws InterruptedException;

  void backoff(final long failureCount) throws InterruptedException;
}
