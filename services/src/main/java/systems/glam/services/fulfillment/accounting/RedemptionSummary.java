package systems.glam.services.fulfillment.accounting;

import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.sdk.idl.programs.glam.mint.gen.types.RequestQueue;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Predicate;

public record RedemptionSummary(long epochSeconds, long slot,
                                List<RedemptionRequest> requests, BigDecimal outstandingShares,
                                List<RedemptionRequest> fulfillable, BigDecimal fulfillableShares,
                                List<RedemptionRequest> softFulfillable, BigDecimal softFulfillableShares) {

  public static RedemptionSummary createSummary(final long epochSeconds,
                                                final long slot,
                                                final RequestQueue requestQueue,
                                                final long redeemNoticePeriod,
                                                final boolean redeemWindowInSeconds) {
    final var redemptionRequests = RedemptionRequest.collectRedemptionRequests(requestQueue);
    final Predicate<RedemptionRequest> fulfillablePredicate = redeemWindowInSeconds
        ? request -> (epochSeconds - request.createdAt()) > redeemNoticePeriod
        : request -> (slot - request.createdAt()) > redeemNoticePeriod;

    final var fulfillable = redemptionRequests.stream().filter(fulfillablePredicate).toList();
    final var softFulfillable = redemptionRequests.stream().filter(fulfillablePredicate.negate()).toList();
    return new RedemptionSummary(
        epochSeconds, slot,
        redemptionRequests, sumOutstandingShares(redemptionRequests),
        fulfillable, sumOutstandingShares(fulfillable),
        softFulfillable, sumOutstandingShares(softFulfillable)
    );
  }

  public static RedemptionSummary createSummary(final long epochSeconds,
                                                final AccountInfo<byte[]> accountInfo,
                                                final long redeemNoticePeriod,
                                                final boolean redeemWindowInSeconds) {
    return createSummary(
        epochSeconds,
        accountInfo.context().slot(),
        RequestQueue.read(accountInfo),
        redeemNoticePeriod, redeemWindowInSeconds
    );
  }

  private static BigDecimal sumOutstandingShares(final List<RedemptionRequest> redemptionRequests) {
    return redemptionRequests.stream()
        .map(RedemptionRequest::shares)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
