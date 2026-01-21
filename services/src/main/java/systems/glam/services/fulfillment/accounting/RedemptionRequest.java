package systems.glam.services.fulfillment.accounting;

import software.sava.core.accounts.PublicKey;
import systems.glam.sdk.idl.programs.glam.mint.gen.types.RequestQueue;
import systems.glam.sdk.idl.programs.glam.mint.gen.types.RequestType;
import systems.glam.services.mints.MintContext;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

public interface RedemptionRequest extends Comparable<RedemptionRequest> {

  static RedemptionRequest createRequest(final PublicKey user,
                                         final long createdAt,
                                         final BigDecimal shares) {
    return new RedemptionRequestRecord(user, createdAt, shares);
  }

  static List<RedemptionRequest> collectRedemptionRequests(final RequestQueue requestQueue) {
    return Arrays.stream(requestQueue.data()).<RedemptionRequest>mapMulti((pendingRequest, downstream) -> {
      if (pendingRequest.fulfilledAt() == 0 && pendingRequest.requestType() == RequestType.Redemption) {
        final var incoming = pendingRequest.incoming();
        final var redemptionRequest = createRequest(
            pendingRequest.user(),
            pendingRequest.createdAt(),
            new BigDecimal(Long.toUnsignedString(incoming))
        );
        downstream.accept(redemptionRequest);
      }
    }).sorted().toList();
  }

  PublicKey user();

  BigDecimal shares();

  long createdAt();

  String toJson(final MintContext sharesMintContext);
}
