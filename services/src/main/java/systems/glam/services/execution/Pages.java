package systems.glam.services.execution;

import software.sava.services.core.net.http.NotifyClient;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import static java.lang.System.Logger.Level.ERROR;

/// The one policy for every page this module posts through ravina's [NotifyClient]: how a
/// throwable is summarized for a page ([#summarize]), how a post is guarded ([#post]) and how the
/// delivery of a page that precedes an exit is awaited ([#awaitDelivery], for
/// [#PAGE_DELIVERY_SECONDS]). The instruction processor's pages, the fulfillment loop's page of its
/// own end and the fulfillment entrypoint's refusals to start all go through here, so that a
/// change to what a page may carry, or to how long an exit holds for one, is made once; a new page
/// of this module goes through here too. A page is an alert, not a log: the caller logs the whole
/// failure beside it, and the page carries the bounded summary.
public final class Pages {

  /// How long a page's posts are awaited before the exit that follows them: the fulfillment
  /// entrypoint ends the process once its worker ends on a failure, or refuses to start, and
  /// ravina's NotifyClientImpl posts asynchronously, so the page should leave first. The
  /// instruction processor awaits the page of a failure it throws for this long, and the
  /// fulfillment loop awaits the page of its end for this long again, on the same thread, so an
  /// exit waits at most twice this bound; the owner accepted the duplicate page, not a longer
  /// wait. The wait is a best effort: a post is a courteous call of up to six attempts under the
  /// webhook's own backoff (fibonacci by default) and an 8 s header and 16 s exchange timeout, so it holds the exit for a
  /// webhook that answers and gives up on one that does not, leaving the post to complete on its
  /// own; and a rejection the webhook answers (a 400, a 429, a 5xx) completes the post normally
  /// and is never seen here.
  public static final long PAGE_DELIVERY_SECONDS = 15L;

  /// The most code points of a throwable's message a page carries; see [#summarize].
  public static final int THROWABLE_SUMMARY_LENGTH = 200;

  /// What sava's HTTP clients write before the response body in the message of a failed request,
  /// `HTTP request failed with [httpCode:%d], [body=%s]`: the body is the provider's, of any length
  /// and content, and the status fragment that tells a key to fix (a 401) from a rate to wait out
  /// (a 429) precedes the marker.
  private static final String HTTP_BODY_MARKER = "[body=";

  /// A run of whitespace, line breaks included, which [#summarize] collapses to one space.
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  /// What ravina's NotifyClientImpl.escape leaves as it is although the webhook's JSON cannot carry
  /// it unescaped: it rewrites only `"` and a newline, so a C0 control character other than the
  /// newline, or a backslash, in a page makes the JSON invalid and the page is lost, while the
  /// webhook's 400 completes the post normally. [#post] removes these from every page; the
  /// root fix belongs to ravina's escape.
  private static final Pattern UNESCAPED = Pattern.compile("[\\x00-\\x09\\x0B-\\x1F\\\\]");

  /// Every control character, C0 and C1, which [#summarize] removes from a throwable's message
  /// beyond [#UNESCAPED]: a summary is one line of printable text.
  private static final Pattern CONTROLS = Pattern.compile("\\p{Cc}");

  /// A bounded, single-line summary of `failure` for a page, the whole throwable being the log's:
  /// its simple class name, then its message cut at [#HTTP_BODY_MARKER] with `[body omitted]` in
  /// the body's place, collapsed to one line (every run of whitespace, line breaks included,
  /// becomes one space, so a multi-line message keeps every line), less every control character
  /// and backslash, and cut to [#THROWABLE_SUMMARY_LENGTH] code points with an ASCII `...` after
  /// the cut, which never splits a surrogate pair. A throwable with no message, or with nothing
  /// left of it, is summarized by its class name alone.
  public static String summarize(final Throwable failure) {
    final var name = failure.getClass().getSimpleName();
    var message = failure.getMessage();
    if (message == null) {
      return name;
    }
    final int body = message.indexOf(HTTP_BODY_MARKER);
    if (body != -1) {
      message = message.substring(0, body) + "[body omitted]";
    }
    message = WHITESPACE.matcher(message).replaceAll(" ");
    message = CONTROLS.matcher(UNESCAPED.matcher(message).replaceAll("")).replaceAll("").strip();
    if (message.isEmpty()) {
      return name;
    }
    if (message.codePointCount(0, message.length()) > THROWABLE_SUMMARY_LENGTH) {
      message = message.substring(0, message.offsetByCodePoints(0, THROWABLE_SUMMARY_LENGTH)) + "...";
    }
    return name + ": " + message;
  }

  /// Posts `msg` through `client`, less what the webhook's JSON cannot carry ([#UNESCAPED]: a
  /// vault's on-chain name or a program log may hold a backslash or a stray control character,
  /// and a page lost to a 400 is worse than one missing a character), and returns its posts, with
  /// none for a null list or a null element: the notify client is anyone's implementation, and a null out of it must not become
  /// a NullPointerException in place of what the caller was about to do. None either when the
  /// client threw, since a client that throws (ravina's does, with RejectedExecutionException,
  /// once its executor has shut down) loses the page and nothing else: the loss is logged at ERROR
  /// to `logger` under `event`, and the caller's own failure, if it has one, is neither replaced
  /// nor given the notify failure as suppressed.
  public static List<CompletableFuture<String>> post(final NotifyClient client,
                                                     final String msg,
                                                     final System.Logger logger,
                                                     final String event) {
    final List<CompletableFuture<String>> posts;
    try {
      posts = client.postMsg(UNESCAPED.matcher(msg).replaceAll(""));
    } catch (final RuntimeException notifyFailure) {
      logger.log(ERROR, String.format("Failed to post the page: %s", event), notifyFailure);
      return List.of();
    }
    return posts == null ? List.of() : posts.stream().filter(Objects::nonNull).toList();
  }

  /// Waits for `posts` (as [#post] returns them, none null) to complete, or for `timeout` to pass,
  /// whichever comes first: a best effort that changes nothing but the time of what follows it.
  /// Completed or no posts return at once. A post that failed within the bound or is still running
  /// at it is logged at ERROR to `logger` under `event` and left to itself; a rejection the webhook
  /// answered completed its post normally and is not seen here. An interrupt ends the wait and is
  /// kept for the caller.
  public static void awaitDelivery(final String event,
                                   final List<CompletableFuture<String>> posts,
                                   final long timeout,
                                   final TimeUnit unit,
                                   final System.Logger logger) {
    try {
      CompletableFuture.allOf(posts.toArray(new CompletableFuture<?>[0])).get(timeout, unit);
    } catch (final TimeoutException | ExecutionException ex) {
      logger.log(ERROR, String.format("The page may not have been delivered: %s", event), ex);
    } catch (final InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  private Pages() {
  }
}
