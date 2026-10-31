package systems.glam.services.execution;

import software.sava.core.accounts.PublicKey;
import software.sava.core.tx.Instruction;
import software.sava.services.core.net.http.NotifyClient;
import software.sava.services.solana.transactions.InstructionService;
import software.sava.services.solana.transactions.TransactionProcessor;
import systems.glam.sdk.GlamAccounts;

import java.math.BigDecimal;
import java.util.List;

public interface InstructionProcessor {

  /// `glamAccounts` is the deployment the instructions go to: a stale price on its mint program is
  /// the one failure retried without a page.
  static InstructionProcessor createProcessor(final TransactionProcessor transactionProcessor,
                                              final InstructionService instructionService,
                                              final GlamAccounts glamAccounts,
                                              final BigDecimal maxLamportPriorityFee,
                                              final NotifyClient notifyClient,
                                              final double cuBudgetMultiplier,
                                              final int maxRetries) {
    return new InstructionProcessorImpl(
        transactionProcessor,
        instructionService,
        glamAccounts.mintProgram(),
        maxLamportPriorityFee,
        notifyClient,
        cuBudgetMultiplier,
        maxRetries
    );
  }

  NotifyClient notifyClient();

  TransactionProcessor transactionProcessor();

  InstructionService instructionService();

  PublicKey mintProgram();

  BigDecimal maxLamportPriorityFee();

  double cuBudgetMultiplier();

  int maxRetries();

  /// [#processInstructions(String, List, double, BigDecimal, int)] with this processor's
  /// [#cuBudgetMultiplier()], [#maxLamportPriorityFee()] and [#maxRetries()]; the contract, the
  /// stopped-monitor exception included, is documented there.
  boolean processInstructions(final String logContext,
                              final List<Instruction> instructions) throws InterruptedException;

  /// Sends `instructions` in as many v1 transactions as they need, batched at the 64-account limit
  /// and halved on a size refusal. Each batch whose send returned, settled or refused, is removed
  /// from the list, and so is one whose transaction expired with no send of it accepted; any other
  /// batch whose send threw stays at the head of it, ahead of the batches never sent. A
  /// size-refused batch is dropped with a warning while the batch bound can still halve, and the
  /// rest carries on. Any other failed batch returns false, and the caller should re-fetch state
  /// and rebuild; every such failure but a stale price on [#mintProgram()] is also posted to the
  /// notify client. An instruction that cannot fit a transaction even alone, its program and
  /// accounts with the fee payer exceeding the account limit, ends the call with an
  /// `IllegalStateException` carrying its details, posted and awaited like the failures below.
  ///
  /// A transaction monitor that has stopped ends the call with
  /// [software.sava.services.solana.transactions.TxMonitorStoppedException], thrown as itself
  /// after a post that says whether a transaction of this call may still land (its `sent()`,
  /// `sig()` and `lastValidBlockHeight()`): with `sent()` that batch's outcome is unknown, its
  /// transaction may still land until that height passes, and a rebuild of the list it heads
  /// before then can execute the batch twice; without it nothing of the batch can land. Whether a
  /// caller may rebuild is the caller's own: the fulfillment service exits for a supervisor
  /// restart, which rebuilds after the restart, since the on-chain fulfill tolerates a duplicate.
  /// Either way the service should exit, since ravina refuses every later call on that monitor the
  /// same way. A batch no send of whose transaction was accepted (every send refused, timed out or
  /// unanswered) and whose status then settled as not landed ends ravina's call with
  /// [software.sava.services.solana.transactions.SendNotAcceptedException]. With its
  /// `expiryVerified()` true, which this processor's `verifyExpired` makes it, the block hash
  /// expired with nothing of the batch landed, so the batch is dropped and the call returns false
  /// like any failed batch, after a post carrying the sends `attempted()`, the last failure and,
  /// when it differs, the first, each as a bounded summary (the log beside it has the whole
  /// throwable), and the caller's rebuild cannot execute it twice; the processor does not tell a
  /// refusal to fix (a 401) from one to wait out (a 429), the page does. With it false this attempt
  /// or an earlier one may still land, so the exception is thrown as itself after a post naming the
  /// transaction, and the batch heads the list like a stopped monitor's `sent()` batch, with the
  /// same rebuild caveat. Every failure the call ends on, these two, the account-limit refusal and
  /// any other exception of the send, is thrown after a best-effort wait for the posts of its page
  /// of up to [Pages#PAGE_DELIVERY_SECONDS], so that a caller that ends the process on it does not
  /// cut the page off: a rejection the webhook answers (a 429, a 5xx) completes the post and is not
  /// seen here, and a post still running at the bound is left to complete on its own. A caller
  /// that catches `RuntimeException` to retry must exclude all three first: the two exceptions
  /// above and the account-limit `IllegalStateException`, which a check on the instruction alone
  /// throws before that pass's send, so a rebuilt list meets it again on every pass.
  boolean processInstructions(final String logContext,
                              final List<Instruction> instructions,
                              final double cuBudgetMultiplier,
                              final BigDecimal maxLamportPriorityFee,
                              final int maxRetries) throws InterruptedException;
}
