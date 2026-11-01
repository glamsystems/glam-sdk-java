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
  /// publication verdicts included, is documented there.
  boolean processInstructions(final String logContext,
                              final List<Instruction> instructions) throws InterruptedException;

  /// Sends `instructions` in as many v1 transactions as they need, batched at the 64-account limit
  /// and halved on a size refusal. Each batch whose send returned, settled or refused, is removed
  /// from the list, and so is one dropped on a verdict that nothing of it landed or can land (a
  /// stopped monitor's excepted, below); any other batch whose send threw stays at the head of
  /// it, ahead of the batches never sent. A size-refused batch is
  /// dropped with a warning while the batch bound can still halve, and the rest carries on. Any
  /// other failed batch returns false, and the caller should re-fetch state and rebuild; every
  /// such failure but a stale price on [#mintProgram()] is also posted to the notify client. An
  /// instruction that cannot fit a transaction even alone, its program and accounts with the fee
  /// payer exceeding the account limit, ends the call with an `IllegalStateException` carrying its
  /// details, posted and awaited like the failures below.
  ///
  /// A send that ends ravina's call without a result ends it with one sealed
  /// [software.sava.services.solana.transactions.PublicationException], whose accessors are the
  /// whole verdict, read in this order. With its `mayStillLand()` true, a transaction of this call
  /// may still land until the settled block height passes its `landableUntil()`, so a rebuild of
  /// the list it heads before then can execute the batch twice: the exception is thrown as itself
  /// after a post naming each transaction of its `landable()` and that height, and the batch
  /// heads the list. Whether a caller may rebuild is the caller's own: the fulfillment service
  /// exits for a supervisor restart, which rebuilds after the restart, since the on-chain fulfill
  /// tolerates a duplicate. A
  /// [software.sava.services.solana.transactions.TxMonitorStoppedException] nothing of which can
  /// land is thrown as itself too, after a post saying so, since ravina refuses every later call
  /// on that monitor the same way and the service should exit. Every other verdict says that
  /// nothing of the batch landed or can land: no send of its last attempt was accepted (refused,
  /// paced, timed out or unanswered) and its block hash then expired with nothing of the call on
  /// chain, which this processor's `verifyExpired` makes every such
  /// [software.sava.services.solana.transactions.SendNotAcceptedException], or no send of its last
  /// attempt reached a server ([software.sava.services.solana.transactions.SendNeverLeftException], which
  /// arrives within a round of connection attempts rather than a block hash window). Such a batch
  /// is dropped and the call returns false like any failed batch, after a post carrying the
  /// verdict's `reason()`, the classes of the last attempt's failed sends and their count, the
  /// last failure and, when it differs, the first, each as a bounded summary (the log beside it
  /// has the whole throwable); the caller's rebuild cannot execute it twice, and its backoff paces
  /// the retry. That post is at WARNING, or at ERROR for a reason of `UNAUTHORIZED`, a refusal to
  /// fix rather than wait out; a refusal that lasts is posted once per attempt, at the caller's
  /// cadence. Every failure the call ends on, the verdicts thrown above, the account-limit refusal
  /// and any other exception of the send, is thrown after a best-effort wait for the posts of its
  /// page of up to [Pages#PAGE_DELIVERY_SECONDS], so that a caller that ends the process on it
  /// does not cut the page off: a rejection the webhook answers (a 429, a 5xx) completes the post
  /// and is not seen here, and a post still running at the bound is left to complete on its own.
  /// A caller that catches `RuntimeException` to retry must exclude first a `PublicationException`
  /// whose `mayStillLand()` is true, a `TxMonitorStoppedException`, and the account-limit
  /// `IllegalStateException`, which a check on the instruction alone throws before that pass's
  /// send, so a rebuilt list meets it again on every pass.
  boolean processInstructions(final String logContext,
                              final List<Instruction> instructions,
                              final double cuBudgetMultiplier,
                              final BigDecimal maxLamportPriorityFee,
                              final int maxRetries) throws InterruptedException;
}
