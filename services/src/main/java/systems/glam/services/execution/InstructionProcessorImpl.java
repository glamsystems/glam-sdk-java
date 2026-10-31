package systems.glam.services.execution;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;
import software.sava.core.tx.Transaction;
import software.sava.rpc.json.http.response.IxError;
import software.sava.rpc.json.http.response.TransactionError;
import software.sava.services.core.net.http.NotifyClient;
import software.sava.services.solana.transactions.InstructionService;
import software.sava.services.solana.transactions.SendNotAcceptedException;
import software.sava.services.solana.transactions.TransactionProcessor;
import software.sava.services.solana.transactions.TransactionResult;
import software.sava.services.solana.transactions.TxMonitorStoppedException;
import systems.glam.sdk.idl.programs.glam.protocol.gen.GlamProtocolError;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
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

  /// How long [#fatalPost] gives the posts of a page before the failure it reports is thrown: the
  /// fulfillment entrypoint exits the process once its worker ends on such a failure, and ravina's
  /// NotifyClientImpl posts asynchronously, so the page should leave first. The wait is a best
  /// effort, not a guarantee. Each post is a courteous call that retries a send that failed (a
  /// refused connection, a timeout) with the webhook's backoff up to the notify client's
  /// maxRetries (BaseDelegateServiceConfig builds it with five, over a fibonacci backoff from one
  /// second by default), each attempt under WebHookClientImpl's 8 s request timeout, so a post can
  /// legitimately outlast this bound and is then left to complete on its own; and a rejection the
  /// webhook answers (Slack's 429, a 5xx) completes the post normally, so it is never seen here.
  private static final long FATAL_PAGE_DELIVERY_SECONDS = 30L;

  /// The most of a throwable's message a page carries; see [#summarize].
  static final int THROWABLE_SUMMARY_LENGTH = 200;

  /// What ravina's NotifyClientImpl.escape leaves as it is although the webhook's JSON cannot carry
  /// it unescaped: it rewrites only `"` and a newline, so a control character or a backslash in a
  /// page makes the JSON invalid and the page is lost, while the webhook's 400 completes the post
  /// normally.
  private static final Pattern UNESCAPED = Pattern.compile("[\\p{Cntrl}\\\\]");

  /// The status fragment of sava's HTTP failure message, "HTTP request failed with [httpCode:%d],
  /// [body=%s]", which tells a key to fix (a 401) from a rate to wait out (a 429).
  private static final Pattern HTTP_CODE = Pattern.compile("\\[httpCode:\\d+]");

  /// A bounded, single-line summary of `failure` for a page, the whole throwable being the log's:
  /// its simple class name and the first line of its message, less the characters [#UNESCAPED]
  /// names (sava's HTTP failure message carries the provider's raw body, and a carriage return, a
  /// tab or a backslash in it would cost the page), cut to [#THROWABLE_SUMMARY_LENGTH] characters
  /// with the `[httpCode:NNN]` fragment kept past the cut. A throwable with no message, or none
  /// left, is summarized by its class name alone.
  static String summarize(final Throwable failure) {
    final var name = failure.getClass().getSimpleName();
    final var message = failure.getMessage();
    if (message == null) {
      return name;
    }
    final var line = UNESCAPED.matcher(message.lines().findFirst().orElse("")).replaceAll("");
    if (line.isBlank()) {
      return name;
    }
    if (line.length() <= THROWABLE_SUMMARY_LENGTH) {
      return name + ": " + line;
    }
    final var cut = line.substring(0, THROWABLE_SUMMARY_LENGTH) + "...";
    final var httpCode = HTTP_CODE.matcher(line);
    return httpCode.find() && httpCode.end() > THROWABLE_SUMMARY_LENGTH
        ? name + ": " + cut + ' ' + httpCode.group()
        : name + ": " + cut;
  }

  /// Logs `msg` at `level` with `failure` and posts it as a page, returning its posts, with none
  /// for a null list or a null element: the notify client is anyone's implementation, and a null
  /// out of it must not become a NullPointerException in place of the failure being paged. None
  /// either when the notify client threw, since a notify client that throws (ravina's does, with
  /// RejectedExecutionException, once its executor has shut down) loses the page and nothing else:
  /// the lost page is logged at ERROR under `event`, and `failure` is neither replaced by the
  /// notify failure nor given it as suppressed, since a ravina exception's instance is not this
  /// call's alone: every dependent of the future the monitor failed sees it.
  private List<CompletableFuture<String>> post(final System.Logger.Level level,
                                               final String event,
                                               final String msg,
                                               final Throwable failure) {
    logger.log(level, msg, failure);
    final List<CompletableFuture<String>> posts;
    try {
      posts = notifyClient.postMsg(msg);
    } catch (final RuntimeException notifyFailure) {
      logger.log(ERROR, String.format("Failed to post the page: %s", event), notifyFailure);
      return List.of();
    }
    return posts == null ? List.of() : posts.stream().filter(Objects::nonNull).toList();
  }

  /// [#post] of `event` formatted with the batch.
  private List<CompletableFuture<String>> page(final System.Logger.Level level,
                                               final String event,
                                               final List<Instruction> ixBatch,
                                               final Throwable failure) {
    return post(level, event, FormatUtil.formatInstructionException(event, ixBatch), failure);
  }

  /// [#post] at ERROR for a failure the call ends on, returned for the caller to throw once
  /// [#awaitPosts] has given the page's posts [#FATAL_PAGE_DELIVERY_SECONDS]: the caller exits the
  /// process on these failures, so the page should leave before the throw, as far as that bound
  /// sees to it.
  private <T extends Throwable> T fatalPost(final String event, final String msg, final T failure) {
    awaitPosts(event, post(ERROR, event, msg, failure), FATAL_PAGE_DELIVERY_SECONDS, TimeUnit.SECONDS);
    return failure;
  }

  /// [#fatalPost] of `event` formatted with the batch.
  private <T extends Throwable> T fatalPage(final String event,
                                            final List<Instruction> ixBatch,
                                            final T failure) {
    return fatalPost(event, FormatUtil.formatInstructionException(event, ixBatch), failure);
  }

  /// Waits for `posts` (none of them null) to complete, or for `timeout` to pass, whichever comes
  /// first: a best effort that changes nothing but the time of the throw it precedes. A post that
  /// failed within the bound or is still running at it is logged at ERROR under `event` and left
  /// to itself; a rejection the webhook answered completes its post normally and is not seen here;
  /// an interrupt ends the wait and is kept for the caller.
  static void awaitPosts(final String event,
                         final List<CompletableFuture<String>> posts,
                         final long timeout,
                         final TimeUnit unit) {
    try {
      CompletableFuture.allOf(posts.toArray(new CompletableFuture<?>[0])).get(timeout, unit);
    } catch (final TimeoutException | ExecutionException ex) {
      logger.log(ERROR, String.format("The page may not have been delivered: %s", event), ex);
    } catch (final InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
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
      } catch (final TxMonitorStoppedException stopped) {
        // The monitor that would have settled this batch has stopped and does not restart. With
        // sent() the batch's outcome is unknown: the transaction named may still land until its
        // block hash expires, and a rebuild before then can execute these instructions twice;
        // without it nothing sent can land. The page says which, and the exception is thrown as
        // itself so that the caller tells it apart by type and decides about a rebuild for itself
        // rather than treating the batch as failed and rebuilding it as one (the fulfillment
        // service exits for a supervisor restart, which rebuilds, since the on-chain fulfill
        // tolerates a duplicate); ravina refuses every later call on that monitor the same way.
        throw fatalPage(stopped.sent()
                ? String.format(
                "Transaction monitor stopped; %s transaction %s may still land until block height %s passes: a rebuild before then can execute these instructions twice.",
                logContext,
                transactionProcessor.formatter().formatSig(stopped.sig()),
                Long.toUnsignedString(stopped.lastValidBlockHeight())
            )
                : String.format("Transaction monitor stopped; nothing sent for %s can still land.", logContext),
            ixBatch, stopped
        );
      } catch (final SendNotAcceptedException notAccepted) {
        if (notAccepted.expiryVerified()) {
          // Every send of this batch's transaction was refused (a 429, a 401, a 5xx), timed out or
          // went unanswered, and its block hash then expired with nothing of the call landed, so a
          // rebuild cannot execute these instructions twice: the batch is dropped like any failed
          // batch, and the caller backs off before it re-fetches state and rebuilds. The cause is
          // not classified (a 401 to fix against a 429 to wait out): the send clients are a load
          // balancer that fails a refused send over to the next peer and sinks a peer that keeps
          // refusing, so this exception means no peer accepted for a whole window, the HTTP status
          // survives only in the text of sava's exception message, and a rethrow would end the
          // service, which its supervisor restarts into the same refusal. A verified expiry
          // therefore always rebuilds, and the page carrying the cause is what tells an operator
          // a key from a rate limit.
          final var cause = notAccepted.getCause();
          // ravina's cause is the last failed send; the first is attached as suppressed when it
          // is another one, and a 401 on one peer followed by 429s on the next must not read as a
          // rate limit alone
          final var suppressed = notAccepted.getSuppressed();
          final var first = suppressed.length == 0 ? null : suppressed[0];
          final var failures = cause == null
              ? "no send was answered"
              : first == null
              ? summarize(cause)
              : summarize(cause) + "; first failure: " + summarize(first);
          // posted, not awaited: the worker goes on, to back off and rebuild, and the post
          // completes on its own
          page(WARNING, String.format(
                  "No send of the %s transaction was accepted (%d attempted) and nothing landed: its block hash expired with nothing of this batch on chain, so the caller backs off and rebuilds. Last failure: %s.",
                  logContext, notAccepted.attempted(), failures
              ),
              ixBatch, notAccepted
          );
          // dropped like every failed batch, under the invariant the clear below the try states:
          // the caller re-fetches state and rebuilds, and the size-limit halving never sees it
          ixBatch.clear();
          return false;
        }
        // Unreachable from here, which passes verifyExpired true, but not misreported should that
        // flag ever change: the attempt's status was only missing, so this attempt or an earlier
        // one of the call may still land, and the batch is treated exactly like a stopped
        // monitor's sent() case: paged with its transaction, left at the head of the list, and the
        // exception thrown as itself.
        throw fatalPage(String.format(
                "No send of the %s transaction %s was accepted (%d attempted) and its expiry was not verified: this attempt or an earlier one of the call may still land, and a rebuild before its status is known can execute these instructions twice.",
                logContext,
                transactionProcessor.formatter().formatSig(notAccepted.sig()),
                notAccepted.attempted()
            ),
            ixBatch, notAccepted
        );
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
          // one cannot halve, and the refusal falls through to the page below
          if (batchSize > 1) {
            batchSize = (batchSize & 1) == 1 ? (batchSize >> 1) + 1 : batchSize >> 1;
            if (instructions.isEmpty()) {
              // the refused batch was the whole remaining list: nothing is left to send at the
              // halved size, and an empty transaction must never go out in its place, so the
              // call ends on the refusal, logged and paged like a failed batch
              post(WARNING, event, msg, null);
              return false;
            }
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
