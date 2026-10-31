package systems.glam.services.execution;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.sysvar.Clock;
import software.sava.core.tx.Instruction;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.services.solana.epoch.EpochInfoService;
import software.sava.services.solana.remote.call.RpcCaller;
import systems.glam.services.ServiceContext;

import java.util.List;
import java.util.Map;

public interface ExecutionServiceContext {

  static ExecutionServiceContext createContext(final ServiceContext serviceContext,
                                               final EpochInfoService epochInfoService,
                                               final InstructionProcessor instructionProcessor) {
    return new ExecutionServiceContextImpl(
        serviceContext,
        epochInfoService,
        instructionProcessor
    );
  }

  long minCheckStateDelayNanos();

  long maxCheckStateDelayNanos();

  Clock clock(final Map<PublicKey, AccountInfo<byte[]>> accountsNeededMap);

  boolean feePayerBalanceLow();

  RpcCaller rpcCaller();

  EpochInfoService epochInfoService();

  InstructionProcessor instructionProcessor();

  long medianMillisPerSlot();

  /// [InstructionProcessor#processInstructions(String, List)] on [#instructionProcessor()], whose
  /// contract applies here: a transaction monitor that has stopped ends the call with
  /// [software.sava.services.solana.transactions.TxMonitorStoppedException], thrown as itself,
  /// whose `sent()` says whether the batch at the head of `instructions` may still land, until its
  /// `lastValidBlockHeight()` passes, so that a rebuild before then can execute it twice (whether
  /// to rebuild is the caller's own; the fulfillment service exits for a supervisor restart, which
  /// does, since the on-chain fulfill tolerates a duplicate), and a batch no send of which was
  /// accepted ends it with [software.sava.services.solana.transactions.SendNotAcceptedException]
  /// when its expiry was not verified; a catch of `RuntimeException` that retries must exclude
  /// both. Each is thrown after a best-effort wait, of up to thirty seconds, for the posts of its
  /// page.
  boolean processInstructions(final String logContext,
                              final List<Instruction> instructions) throws InterruptedException;

  void backoff(final long failureCount) throws InterruptedException;
}
