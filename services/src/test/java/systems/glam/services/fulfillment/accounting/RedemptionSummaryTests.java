package systems.glam.services.fulfillment.accounting;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.SolanaAccounts;
import systems.glam.sdk.idl.programs.glam.mint.gen.types.PendingRequest;
import systems.glam.sdk.idl.programs.glam.mint.gen.types.RequestQueue;
import systems.glam.sdk.idl.programs.glam.mint.gen.types.RequestType;
import systems.glam.services.mints.MintContext;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class RedemptionSummaryTests {

  private static PublicKey user(final int id) {
    final byte[] bytes = new byte[PublicKey.PUBLIC_KEY_LENGTH];
    bytes[0] = (byte) id;
    return PublicKey.createPubKey(bytes);
  }

  private static PendingRequest pending(final int userId,
                                        final long shares,
                                        final long createdAt,
                                        final long fulfilledAt,
                                        final RequestType requestType) {
    return new PendingRequest(
        user(userId), shares, 0L, createdAt, fulfilledAt, 0, requestType,
        new byte[PendingRequest.RESERVED_LEN]
    );
  }

  private static RequestQueue queue(final PendingRequest... requests) {
    return new RequestQueue(
        PublicKey.NONE, RequestQueue.DISCRIMINATOR,
        user(101), user(102),
        false, false,
        requests
    );
  }

  @Test
  void collectFiltersFulfilledAndNonRedemptions() {
    final var requests = RedemptionRequest.collectRedemptionRequests(queue(
        pending(1, 100, 1_000L, 0L, RequestType.Redemption),
        // already fulfilled: excluded
        pending(2, 200, 900L, 5_000L, RequestType.Redemption),
        // a subscription: excluded
        pending(3, 300, 800L, 0L, RequestType.Subscription),
        pending(4, 400, 500L, 0L, RequestType.Redemption)
    ));
    assertEquals(2, requests.size());
    // sorted by createdAt ascending: oldest first
    assertEquals(user(4), requests.getFirst().user());
    assertEquals(500L, requests.getFirst().createdAt());
    assertEquals(user(1), requests.getLast().user());
    assertEquals(new BigDecimal(400), requests.getFirst().shares());
    assertEquals(new BigDecimal(100), requests.getLast().shares());
  }

  @Test
  void sharesAndOrderingAreUnsigned() {
    // incoming -1 is u64 max, not a negative amount
    final var maxShares = RedemptionRequest.collectRedemptionRequests(queue(
        pending(1, -1L, 1_000L, 0L, RequestType.Redemption)
    ));
    assertEquals(new BigDecimal("18446744073709551615"), maxShares.getFirst().shares());

    // createdAt compares unsigned: a "negative" timestamp is the far future
    final var ordered = RedemptionRequest.collectRedemptionRequests(queue(
        pending(1, 1, -5L, 0L, RequestType.Redemption),
        pending(2, 2, 10L, 0L, RequestType.Redemption)
    ));
    assertEquals(user(2), ordered.getFirst().user());
    assertEquals(user(1), ordered.getLast().user());
  }

  @Test
  void sumOutstandingShares() {
    assertEquals(BigDecimal.ZERO, RedemptionRequest.sumOutstandingShares(List.of()));
    final var requests = List.of(
        RedemptionRequest.createRequest(user(1), 1L, new BigDecimal(25)),
        RedemptionRequest.createRequest(user(2), 2L, new BigDecimal(75))
    );
    assertEquals(new BigDecimal(100), RedemptionRequest.sumOutstandingShares(requests));
  }

  @Test
  void summaryPartitionsBySecondsWindow() {
    final long epochSeconds = 1_000L;
    final long slot = 50_000L;
    final long noticePeriod = 100L;
    final var summary = RedemptionSummary.createSummary(
        epochSeconds, slot,
        queue(
            // 1000 - 899 = 101 > 100: fulfillable
            pending(1, 10, 899L, 0L, RequestType.Redemption),
            // 1000 - 900 = 100: exactly the notice period is NOT fulfillable
            pending(2, 20, 900L, 0L, RequestType.Redemption),
            pending(3, 30, 990L, 0L, RequestType.Redemption)
        ),
        noticePeriod, true
    );

    assertEquals(epochSeconds, summary.epochSeconds());
    assertEquals(slot, summary.slot());
    assertEquals(3, summary.requests().size());
    assertEquals(new BigDecimal(60), summary.outstandingShares());

    assertEquals(List.of(user(1)), summary.fulfillable().stream().map(RedemptionRequest::user).toList());
    assertEquals(new BigDecimal(10), summary.fulfillableShares());
    assertEquals(2, summary.softFulfillable().size());
    assertEquals(new BigDecimal(50), summary.softFulfillableShares());
  }

  @Test
  void summaryUsesSlotsWhenWindowNotInSeconds() {
    final var summary = RedemptionSummary.createSummary(
        1_000L, 50_000L,
        queue(
            // 50_000 - 49_000 = 1_000 > 500 slots: fulfillable
            pending(1, 10, 49_000L, 0L, RequestType.Redemption),
            // 50_000 - 49_600 = 400 slots: still in notice
            pending(2, 20, 49_600L, 0L, RequestType.Redemption),
            // exactly the notice period is NOT fulfillable: strict >
            pending(3, 30, 49_500L, 0L, RequestType.Redemption)
        ),
        500L, false
    );
    assertEquals(List.of(user(1)), summary.fulfillable().stream().map(RedemptionRequest::user).toList());
    assertEquals(
        List.of(user(3), user(2)),
        summary.softFulfillable().stream().map(RedemptionRequest::user).toList()
    );
  }

  @Test
  void summaryParsesTheQueueFromAccountData() {
    // round-trips the generated RequestQueue serde: the mutation suites
    // exclude generated code, so this boundary is pinned here instead
    final var queue = queue(
        pending(1, 100, 899L, 0L, RequestType.Redemption),
        pending(2, 200, 990L, 0L, RequestType.Redemption),
        pending(3, 300, 800L, 5_000L, RequestType.Redemption)
    );
    final byte[] data = queue.write();
    final var parsed = systems.glam.sdk.idl.programs.glam.mint.gen.types.RequestQueue.read(user(50), data);
    assertEquals(queue.glamState(), parsed.glamState());
    assertEquals(queue.glamMint(), parsed.glamMint());
    assertEquals(3, parsed.data().length);
    assertEquals(queue.data()[0].user(), parsed.data()[0].user());
    assertEquals(queue.data()[0].incoming(), parsed.data()[0].incoming());
    assertEquals(queue.data()[0].createdAt(), parsed.data()[0].createdAt());
    assertEquals(RequestType.Redemption, parsed.data()[0].requestType());

    final var accountInfo = new software.sava.rpc.json.http.response.AccountInfo<>(
        user(50), new software.sava.rpc.json.http.response.Context(50_000L, null),
        false, 0, user(51), java.math.BigInteger.ZERO, 0, data
    );
    final var summary = RedemptionSummary.createSummary(1_000L, accountInfo, 100L, true);
    // the slot comes from the account context, the shares from the parsed queue
    assertEquals(50_000L, summary.slot());
    assertEquals(2, summary.requests().size());
    assertEquals(new BigDecimal(300), summary.outstandingShares());
    assertEquals(List.of(user(1)), summary.fulfillable().stream().map(RedemptionRequest::user).toList());
  }

  @Test
  void toJsonScalesSharesByMintDecimals() {
    final var request = RedemptionRequest.createRequest(user(7), 1_650_000_000L, new BigDecimal(1_234_500));
    final var mintContext = MintContext.createContext(
        SolanaAccounts.MAIN_NET, user(8), 6, SolanaAccounts.MAIN_NET.tokenProgram()
    );
    final var json = request.toJson(mintContext);
    assertTrue(json.contains("\"user\": \"" + user(7).toBase58() + '"'), json);
    assertTrue(json.contains("\"shares\": \"1.2345\""), json);
    assertTrue(json.contains("\"createdAt\": \"2022-04-15T05:20:00Z\""), json);
  }
}
