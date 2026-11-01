package systems.glam.services.execution;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.core.tx.Transaction;
import software.sava.rpc.json.http.response.IxError;
import software.sava.rpc.json.http.response.TransactionError;
import software.sava.services.core.net.http.NotifyClient;
import software.sava.services.solana.config.ChainItemFormatter;
import software.sava.services.solana.transactions.InstructionService;
import software.sava.services.solana.transactions.PublicationException;
import software.sava.services.solana.transactions.TransactionProcessor;
import software.sava.services.solana.transactions.TransactionResult;
import software.sava.services.solana.transactions.TxAttempt;
import software.sava.services.solana.transactions.TxMonitorStoppedException;
import systems.glam.sdk.idl.programs.glam.protocol.gen.GlamProtocolError;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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

  /// Logs `msg` at `level` with `failure` and posts it as a page through [Pages#post], guarded as
  /// every page of this module is: a notify client that answers null, or throws (ravina's does,
  /// with RejectedExecutionException, once its executor has shut down), loses the page and nothing
  /// else, so that `failure` is neither replaced by the notify failure nor given it as suppressed:
  /// the failure is the caller's to throw as it arrived, and a lost page is the log's.
  private List<CompletableFuture<String>> post(final System.Logger.Level level,
                                               final String event,
                                               final String msg,
                                               final Throwable failure) {
    logger.log(level, msg, failure);
    return Pages.post(notifyClient, msg, logger, event);
  }

  /// [#post] of `event` formatted with the batch.
  private List<CompletableFuture<String>> page(final System.Logger.Level level,
                                               final String event,
                                               final List<Instruction> ixBatch,
                                               final Throwable failure) {
    return post(level, event, FormatUtil.formatInstructionException(event, ixBatch), failure);
  }

  /// [#post] at ERROR for a failure the call ends on, returned for the caller to throw once
  /// [Pages#awaitDelivery] has given the page's posts [Pages#PAGE_DELIVERY_SECONDS]: the caller
  /// exits the process on these failures, so the page should leave before the throw, as far as
  /// that bound sees to it.
  private <T extends Throwable> T fatalPost(final String event, final String msg, final T failure) {
    Pages.awaitDelivery(event, post(ERROR, event, msg, failure), Pages.PAGE_DELIVERY_SECONDS, TimeUnit.SECONDS, logger);
    return failure;
  }

  /// [#fatalPost] of `event` formatted with the batch.
  private <T extends Throwable> T fatalPage(final String event,
                                            final List<Instruction> ixBatch,
                                            final T failure) {
    return fatalPost(event, FormatUtil.formatInstructionException(event, ixBatch), failure);
  }

  /// The level of the page of a batch nothing of which landed or can land, by the verdict's
  /// reason: ERROR for [PublicationException.Reason#UNAUTHORIZED], a key, plan or allow-list
  /// refusal that no backoff and no restart clears and an operator must; WARNING for every other
  /// reason, which the caller's backoff paces (a rate limit, a server error, a peer unreachable
  /// or unanswering, any other refusal). A refusal that lasts therefore pages once per attempt,
  /// at the fulfillment loop's cadence, and never more often: the processor keeps no count and
  /// no clock, and a cap on that cadence is the owner's call, not taken here.
  /// [PublicationException.Reason#MONITOR_STOPPED] never reaches this choice, since a stopped
  /// monitor ends the call. Ravina reads the reason from the HTTP status sava-rpc carries since
  /// 25.13.2, a JSON-RPC error code, or a `retry-after` hint; which statuses read `UNAUTHORIZED`
  /// (a 401 and a 403 among them) is ravina's table, not this module's.
  static System.Logger.Level dropLevel(final PublicationException.Reason reason) {
    return reason == PublicationException.Reason.UNAUTHORIZED ? ERROR : WARNING;
  }

  /// The failed sends of `failed`'s last attempt for a page, each as its bounded summary (the log
  /// beside the page has the whole throwable): ravina's cause, the last failed send, then the
  /// first when ravina attached it as suppressed, since a 401 on one peer behind 429s on the next
  /// must not read as a rate limit alone; "no send was answered" for a verdict without a cause,
  /// every send of which went unanswered.
  static String failures(final PublicationException failed) {
    final var cause = failed.getCause();
    if (cause == null) {
      return "no send was answered";
    }
    final var suppressed = failed.getSuppressed();
    return suppressed.length == 0
        ? Pages.summarize(cause)
        : Pages.summarize(cause) + "; first failure: " + Pages.summarize(suppressed[0]);
  }

  /// `sig` as `formatter` renders it, or bare when the formatter throws: the formatter is
  /// configuration (a `sigFormat` with the wrong conversion throws on every signature, and ravina
  /// keeps it out of its verdicts for that reason), and the page that says what may still land
  /// must not be lost to it.
  static String formatOrBare(final ChainItemFormatter formatter, final String sig) {
    try {
      return formatter.formatSig(sig);
    } catch (final RuntimeException ex) {
      return sig;
    }
  }

  /// The page of a verdict a transaction of which may still land: `logContext`, ravina's message,
  /// which says how the call ended and what may land until when, each landable transaction as
  /// the formatter renders it (ravina prints base58; see [#formatOrBare]), and the rebuild caveat.
  private String landablePage(final String logContext, final PublicationException failed) {
    final var formatter = transactionProcessor.formatter();
    final var landable = failed.landable().stream()
        .map(TxAttempt::sig)
        .map(sig -> formatOrBare(formatter, sig))
        .collect(Collectors.joining(", "));
    return String.format(
        "%s: %s May still land: %s. A rebuild before block height %s passes can execute these instructions twice.",
        logContext, failed.getMessage(), landable, Long.toUnsignedString(failed.landableUntil())
    );
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
              // no transaction can carry this instruction even alone, so the call ends: paged,
              // with the post guarded and awaited like every failure the call ends on
              throw fatalPost("Instruction Exceeds Account Limit", msg, new IllegalStateException(msg));
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
      } catch (final PublicationException failed) {
        // ravina's one verdict for a call that ends without a result after a send, or that a
        // stopped monitor refused. Its accessors decide, in this order: whether anything the call
        // sent may still land, then whether the monitor stopped, then why nothing landed.
        if (failed.mayStillLand()) {
          // The batch's outcome is unknown: a transaction it sent may still land until the
          // settled block height passes its last valid height, and a rebuild before then can
          // execute these instructions twice. Thrown as itself, the batch left at the head of
          // the list, so that the caller tells it apart by type and decides about a rebuild for
          // itself rather than treating the batch as failed and rebuilding it as one (the
          // fulfillment service exits for a supervisor restart, which rebuilds, since the
          // on-chain fulfill tolerates a duplicate).
          throw fatalPage(landablePage(logContext, failed), ixBatch, failed);
        }
        if (failed instanceof TxMonitorStoppedException) {
          // Nothing sent can land, but the monitor that would have settled this batch has
          // stopped and does not restart, and ravina refuses every later call on it the same
          // way: thrown as itself, for the service to exit.
          throw fatalPage(String.format("Transaction monitor stopped; nothing sent for %s can still land.", logContext), ixBatch, failed);
        }
        // Nothing of this batch landed or can land: no send of its last attempt was accepted
        // (refused, paced, timed out or unanswered) and its block hash then expired with nothing
        // of the call on chain, or no send of its last attempt reached a server. Dropped like any failed
        // batch, so that the caller backs off, re-fetches state and rebuilds, which cannot
        // execute these instructions twice: the send clients are a load balancer that routes
        // around a refusing peer, a send that never left arrives within a round of connection
        // attempts and the loop's backoff paces its retry, and a rethrow would end the service
        // for a supervisor to restart it into the same refusal or outage. The page is what tells
        // an operator a key to fix from a rate to wait out, by the verdict's reason, the classes
        // of the last attempt's failed sends, and the failures it names; posted, not awaited,
        // since the worker goes on and the post completes on its own.
        // present on every verdict that reaches here: only a stop refused before the call's first
        // attempt names no attempt, and the stop was told above
        final var sends = failed.lastAttempt().orElseThrow().sends();
        page(dropLevel(failed.reason()), String.format(
                "Nothing of the %s transaction landed or can land [reason=%s] [failures=%s] [attempted=%d]; the caller backs off and rebuilds. Last failure: %s.",
                logContext, failed.reason(), sends.failures(), sends.attempted(), failures(failed)
            ),
            ixBatch, failed
        );
        // dropped like every failed batch, under the invariant the clear below the try states:
        // the caller re-fetches state and rebuilds, and the size-limit halving never sees it
        ixBatch.clear();
        return false;
      } catch (final RuntimeException ex) {
        throw fatalPage(String.format("Failed to process %s instructions.", logContext), ixBatch, ex);
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
        final var event = String.format("%s Failed", logContext);
        final var msg = String.format("""
                %s
                %s
                """,
            event, formattedTxResult
        );
        if (error == SIZE_LIMIT_EXCEEDED) {
          // batchSize bounds the batch, which the account limit may have cut shorter: a bound of
          // one cannot halve, and a refused batch that was the whole remaining list leaves
          // nothing to send at the halved size, where an empty transaction must never go out in
          // its place; either refusal falls through to the one exit below, logged and paged like
          // a failed batch. Otherwise the rest carries on under the halved bound, with a warning.
          if (batchSize > 1 && !instructions.isEmpty()) {
            batchSize = (batchSize & 1) == 1 ? (batchSize >> 1) + 1 : batchSize >> 1;
            logger.log(WARNING, msg);
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
        // dropped for the caller to re-fetch state and rebuild: logged and paged, the post guarded
        // so that a notify client that throws cannot turn this false into an exception
        post(WARNING, event, msg, null);
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
