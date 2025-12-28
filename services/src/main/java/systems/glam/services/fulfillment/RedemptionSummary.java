package systems.glam.services.fulfillment;

import software.sava.rpc.json.http.response.AccountInfo;
import systems.glam.sdk.idl.programs.glam.mint.gen.types.RequestQueue;

import java.math.BigDecimal;
import java.util.List;

record RedemptionSummary(long slot, List<RedemptionRequest> requests, BigDecimal outstandingShares) {

  static RedemptionSummary createSummary(final long slot, final RequestQueue requestQueue) {
    final var redemptionRequests = RedemptionRequest.collectRedemptionRequests(requestQueue);
    return new RedemptionSummary(slot, redemptionRequests, sumOutstandingShares(redemptionRequests));
  }

  static RedemptionSummary createSummary(final AccountInfo<byte[]> accountInfo) {
    return createSummary(accountInfo.context().slot(), RequestQueue.read(accountInfo));
  }

  static BigDecimal sumOutstandingShares(final List<RedemptionRequest> redemptionRequests) {
    return redemptionRequests.stream()
        .map(RedemptionRequest::shares)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }
}
