package systems.glam.services.execution;

import org.junit.jupiter.api.Test;
import software.sava.services.core.net.http.NotifyClient;
import systems.glam.services.tests.LogCapture;
import systems.glam.services.tests.Workers;

import java.io.UncheckedIOException;
import java.net.UnknownServiceException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.*;

/// The one paging policy, a rule per test: what a summary carries, what a guarded post answers, and
/// how an await of a page's delivery ends, each arm held without waiting.
final class PagesTests {

  private static final String LOGGER_NAME = PagesTests.class.getName();
  /// The logger the helpers are handed, which the captures below attach to by its name.
  private static final System.Logger LOGGER = System.getLogger(LOGGER_NAME);

  // ------------------------------------------------------------- summaries --

  /// A throwable without a message, or whose message leaves nothing once stripped, is its simple
  /// class name alone.
  @Test
  void aThrowableWithNoMessageLeftIsItsClassAlone() {
    assertEquals("IllegalStateException", Pages.summarize(new IllegalStateException()));
    assertEquals("IllegalStateException", Pages.summarize(new IllegalStateException(" \t\u0001\\ \n")));
  }

  /// Every run of whitespace, line breaks included, becomes one space, so a multi-line message (the
  /// account-limit refusal's JSON block) keeps every line, on one line, trimmed at both ends.
  @Test
  void aMessageIsCollapsedToOneLine() {
    assertEquals(
        "IllegalStateException: { \"event\": \"Instruction Exceeds Account Limit\", \"numAccounts\": 70 }",
        Pages.summarize(new IllegalStateException(
            "\n{\n \"event\": \"Instruction Exceeds Account Limit\",\n \"numAccounts\": 70\n}\n"
        ))
    );
    assertEquals("IllegalStateException: rpc down", Pages.summarize(new IllegalStateException("\n  rpc \t\r\n down \n")));
  }

  /// Every control character, C0 and C1, and every backslash is removed, since ravina's webhook
  /// escape rewrites only the quote and the newline into the webhook's JSON. Whitespace is
  /// collapsed first, so a tab inside a word becomes a space, not nothing.
  @Test
  void controlCharactersAndBackslashesAreRemoved() {
    assertEquals(
        "IllegalStateException: code 7 an escape, a nul and a C1 control backslash",
        Pages.summarize(new IllegalStateException(
            "code\t7 an escape,\u001B a nul\u0000 and a C1\u0085 control back\\slash"
        ))
    );
  }

  /// A failed request of sava's HTTP clients carries the provider's body in its message, behind the
  /// status that tells a key to fix from a rate to wait out: the status is kept, the body never,
  /// however short.
  @Test
  void anHttpFailureIsSummarizedWithoutItsBody() {
    final var failure = new UncheckedIOException(new UnknownServiceException(
        "HTTP request failed with [httpCode:401], [body={\"error\":\"invalid api key 5ecret\"}]"
    ));
    final var summary = Pages.summarize(failure);
    assertEquals(
        "UncheckedIOException: java.net.UnknownServiceException: HTTP request failed with [httpCode:401], [body omitted]",
        summary
    );
    assertFalse(summary.contains("5ecret"), summary);
  }

  /// At most 200 code points of the message are kept, the 200th among them, and a cut message ends
  /// in an ASCII ellipsis; a character outside the basic plane counts once and is never split.
  @Test
  void aMessageIsCutAtTwoHundredCodePoints() {
    final int bound = Pages.THROWABLE_SUMMARY_LENGTH;
    assertEquals(200, bound);
    final var atTheBound = "x".repeat(bound);
    assertEquals("IllegalStateException: " + atTheBound, Pages.summarize(new IllegalStateException(atTheBound)));
    assertEquals("IllegalStateException: " + atTheBound + "...", Pages.summarize(new IllegalStateException(atTheBound + "y")));
    final var grin = new String(Character.toChars(0x1F600));
    final var endingInAGrin = "x".repeat(bound - 1) + grin;
    assertEquals(
        "IllegalStateException: " + endingInAGrin + "...",
        Pages.summarize(new IllegalStateException(endingInAGrin + grin))
    );
  }

  // ----------------------------------------------------------------- posts --

  /// A post answers the client's posts, less any null the client answered among them; a null list
  /// is no post. Neither is a failure of anything.
  @Test
  void aPostAnswersTheClientsPostsLessAnyNull() {
    final var delivered = CompletableFuture.completedFuture("delivered");
    final var messages = new ArrayList<String>();
    final NotifyClient withANull = msg -> {
      messages.add(msg);
      return Arrays.asList(null, delivered);
    };
    try (final var log = LogCapture.attach(LOGGER_NAME)) {
      assertEquals(List.of(delivered), Pages.post(withANull, "the page", LOGGER, "the event"));
      assertEquals(List.of(), Pages.post(msg -> null, "the page", LOGGER, "the event"));
      assertTrue(log.messages().isEmpty(), () -> "a null answer is no failure, got " + log.messages());
    }
    assertEquals(List.of("the page"), messages);
  }

  /// What the webhook's JSON cannot carry unescaped leaves the page before it is posted: a
  /// backslash and a C0 control other than the newline, which ravina's escape rewrites.
  @Test
  void aPostCarriesNoBackslashOrStrayControlCharacter() {
    final var messages = new ArrayList<String>();
    final NotifyClient recording = msg -> {
      messages.add(msg);
      return List.of();
    };
    Pages.post(recording, "Alpha\\Beta\tended\r\nline two\u0001", LOGGER, "the event");
    assertEquals(List.of("AlphaBetaended\nline two"), messages);
  }

  /// A client that throws (ravina's does, with RejectedExecutionException, once its executor has
  /// shut down) loses the page and nothing else: no posts, and the loss logged at ERROR under the
  /// event.
  @Test
  void aClientThatThrowsLosesThePageAndIsLogged() {
    final NotifyClient client = msg -> {
      throw new RejectedExecutionException("the notify executor has shut down");
    };
    try (final var log = LogCapture.attach(LOGGER_NAME)) {
      assertEquals(List.of(), Pages.post(client, "the page", LOGGER, "the event"));
      log.assertLogged(Level.SEVERE, "Failed to post the page: the event");
    }
  }

  // ---------------------------------------------------------------- awaits --

  /// The await of a page is a best effort with four endings. Posts already complete, or none, end
  /// it at once, with nothing logged.
  @Test
  void completedPostsAreNotAwaited() throws InterruptedException {
    final var runner = new Thread(() -> {
      Pages.awaitDelivery("the event", List.of(CompletableFuture.completedFuture("delivered")), 30L, TimeUnit.SECONDS, LOGGER);
      Pages.awaitDelivery("the event", List.of(), 30L, TimeUnit.SECONDS, LOGGER);
    }, "completed posts runner");
    try (final var log = LogCapture.attach(LOGGER_NAME)) {
      runner.start();
      Workers.joinWithin(runner, "completed posts must not be awaited");
      assertTrue(log.messages().isEmpty(), () -> "nothing is logged for a delivered page, got " + log.messages());
    }
  }

  /// A post that failed within the bound is logged as possibly undelivered and changes nothing
  /// else; a webhook's own rejection completes its post normally, so only a failure to post at
  /// all arrives here.
  @Test
  void aFailedPostIsLoggedAsPossiblyUndelivered() {
    final var refused = CompletableFuture.<String>failedFuture(new IllegalStateException("the webhook refused the connection"));
    try (final var log = LogCapture.attach(LOGGER_NAME)) {
      Pages.awaitDelivery("the event", List.of(refused), 30L, TimeUnit.SECONDS, LOGGER);
      log.assertLogged(Level.SEVERE, "The page may not have been delivered: the event");
    }
  }

  /// A post still running at the bound is logged the same way and left to itself. A bound of
  /// zero puts the never-completing post at it already, so nothing here waits.
  @Test
  void aPostOutlastingTheBoundIsLoggedAsPossiblyUndelivered() {
    try (final var log = LogCapture.attach(LOGGER_NAME)) {
      Pages.awaitDelivery("the event", List.of(new CompletableFuture<>()), 0L, TimeUnit.SECONDS, LOGGER);
      log.assertLogged(Level.SEVERE, "The page may not have been delivered: the event");
    }
  }

  /// An interrupt ends the await, which is not a delivery failure, with the flag kept for the
  /// caller. A completed post returns before the interrupt is read, so the post here is pending.
  @Test
  void anInterruptEndsTheAwaitAndIsKeptForTheCaller() {
    try (final var log = LogCapture.attach(LOGGER_NAME)) {
      Thread.currentThread().interrupt();
      Pages.awaitDelivery("the event", List.of(new CompletableFuture<>()), 30L, TimeUnit.SECONDS, LOGGER);
      // read and cleared in one, so that no later test inherits the flag
      assertTrue(Thread.interrupted(), "the interrupt is the caller's to see");
      assertTrue(log.messages().isEmpty(), () -> "an interrupt is not a delivery failure, got " + log.messages());
    } finally {
      Thread.interrupted();
    }
  }

  /// The bound every exit of this module holds for a page: fifteen seconds, twice over at most
  /// (the processor's fatal page, then the loop's end page, on one thread).
  @Test
  void theDeliveryBoundIsFifteenSeconds() {
    assertEquals(15L, Pages.PAGE_DELIVERY_SECONDS);
  }
}
