package systems.glam.services.fulfillment;

import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.sdk.idl.programs.glam.mint.gen.types.RequestQueue;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Predicate;

record RedemptionSummary(long slot,
                         List<RedemptionRequest> requests, BigDecimal outstandingShares,
                         List<RedemptionRequest> fulfillable, BigDecimal fulfillableShares) {

  static RedemptionSummary createSummary(final long epochSeconds,
                                         final long slot,
                                         final RequestQueue requestQueue,
                                         final long redeemNoticePeriod,
                                         final boolean redeemWindowInSeconds) {
    final var redemptionRequests = RedemptionRequest.collectRedemptionRequests(requestQueue);
    final Predicate<RedemptionRequest> fulfillablePredicate = redeemWindowInSeconds
        ? request -> (epochSeconds - request.createdAt()) > redeemNoticePeriod
        : request -> (slot - request.createdAt()) > redeemNoticePeriod;

    final var fulfillable = redemptionRequests.stream().filter(fulfillablePredicate).toList();
    return new RedemptionSummary(
        slot,
        redemptionRequests, sumOutstandingShares(redemptionRequests),
        fulfillable,
        sumOutstandingShares(fulfillable)
    );
  }

  static RedemptionSummary createSummary(final long epochSeconds,
                                         final AccountInfo<byte[]> accountInfo,
                                         final long redeemNoticePeriod,
                                         final boolean redeemWindowInSeconds) {
    return createSummary(
        epochSeconds,
        accountInfo.context().slot() + 1,
        RequestQueue.read(accountInfo),
        redeemNoticePeriod, redeemWindowInSeconds
    );
  }

  static BigDecimal sumOutstandingShares(final List<RedemptionRequest> redemptionRequests) {
    return redemptionRequests.stream()
        .map(RedemptionRequest::shares)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
