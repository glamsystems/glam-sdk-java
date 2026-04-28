package systems.glam.sdk.idl.programs.glam.staging.mint.gen;

import software.sava.idl.clients.core.gen.ProgramError;

public sealed interface GlamMintError extends ProgramError permits
    GlamMintError.InvalidAuthority,
    GlamMintError.UnauthorizedSigner,
    GlamMintError.ActionPaused,
    GlamMintError.InvalidAsset,
    GlamMintError.MaxCapExceeded,
    GlamMintError.InvalidAmount,
    GlamMintError.NewRequestNotAllowed,
    GlamMintError.RequestNotClaimable,
    GlamMintError.RequestNotCancellable,
    GlamMintError.RequestNotFound,
    GlamMintError.RequestQueueNotEmpty,
    GlamMintError.InvalidRequestQueueData,
    GlamMintError.RequestQueueFull,
    GlamMintError.ProtocolFeesNotCrystallized,
    GlamMintError.AmountBelowMinimum,
    GlamMintError.ManagerFeesNotCrystallized,
    GlamMintError.AmountAboveMaximum,
    GlamMintError.InsufficientEscrowBalance,
    GlamMintError.TokenAclManagesFreezeThaw,
    GlamMintError.InvalidMintState {

  static GlamMintError getInstance(final int errorCode) {
    return switch (errorCode) {
      case 6000 -> InvalidAuthority.INSTANCE;
      case 6001 -> UnauthorizedSigner.INSTANCE;
      case 6002 -> ActionPaused.INSTANCE;
      case 6003 -> InvalidAsset.INSTANCE;
      case 6004 -> MaxCapExceeded.INSTANCE;
      case 6005 -> InvalidAmount.INSTANCE;
      case 6006 -> NewRequestNotAllowed.INSTANCE;
      case 6007 -> RequestNotClaimable.INSTANCE;
      case 6008 -> RequestNotCancellable.INSTANCE;
      case 6009 -> RequestNotFound.INSTANCE;
      case 6010 -> RequestQueueNotEmpty.INSTANCE;
      case 6011 -> InvalidRequestQueueData.INSTANCE;
      case 6012 -> RequestQueueFull.INSTANCE;
      case 6013 -> ProtocolFeesNotCrystallized.INSTANCE;
      case 6014 -> AmountBelowMinimum.INSTANCE;
      case 6015 -> ManagerFeesNotCrystallized.INSTANCE;
      case 6016 -> AmountAboveMaximum.INSTANCE;
      case 6017 -> InsufficientEscrowBalance.INSTANCE;
      case 6018 -> TokenAclManagesFreezeThaw.INSTANCE;
      case 6019 -> InvalidMintState.INSTANCE;
      default -> null;
    };
  }

  record InvalidAuthority(int code, String msg) implements GlamMintError {

    public static final InvalidAuthority INSTANCE = new InvalidAuthority(
        6000, "Invalid authority"
    );
  }

  record UnauthorizedSigner(int code, String msg) implements GlamMintError {

    public static final UnauthorizedSigner INSTANCE = new UnauthorizedSigner(
        6001, "Signer is not authorized"
    );
  }

  record ActionPaused(int code, String msg) implements GlamMintError {

    public static final ActionPaused INSTANCE = new ActionPaused(
        6002, "Requested action is paused"
    );
  }

  record InvalidAsset(int code, String msg) implements GlamMintError {

    public static final InvalidAsset INSTANCE = new InvalidAsset(
        6003, "Asset not allowed to subscribe"
    );
  }

  record MaxCapExceeded(int code, String msg) implements GlamMintError {

    public static final MaxCapExceeded INSTANCE = new MaxCapExceeded(
        6004, "Max cap exceeded"
    );
  }

  record InvalidAmount(int code, String msg) implements GlamMintError {

    public static final InvalidAmount INSTANCE = new InvalidAmount(
        6005, "Invalid amount for subscription or redemption"
    );
  }

  record NewRequestNotAllowed(int code, String msg) implements GlamMintError {

    public static final NewRequestNotAllowed INSTANCE = new NewRequestNotAllowed(
        6006, "New request is not allowed"
    );
  }

  record RequestNotClaimable(int code, String msg) implements GlamMintError {

    public static final RequestNotClaimable INSTANCE = new RequestNotClaimable(
        6007, "Request is not claimable"
    );
  }

  record RequestNotCancellable(int code, String msg) implements GlamMintError {

    public static final RequestNotCancellable INSTANCE = new RequestNotCancellable(
        6008, "Request is not cancellable"
    );
  }

  record RequestNotFound(int code, String msg) implements GlamMintError {

    public static final RequestNotFound INSTANCE = new RequestNotFound(
        6009, "Request not found"
    );
  }

  record RequestQueueNotEmpty(int code, String msg) implements GlamMintError {

    public static final RequestQueueNotEmpty INSTANCE = new RequestQueueNotEmpty(
        6010, "Request queue not empty"
    );
  }

  record InvalidRequestQueueData(int code, String msg) implements GlamMintError {

    public static final InvalidRequestQueueData INSTANCE = new InvalidRequestQueueData(
        6011, "Invalid request queue data"
    );
  }

  record RequestQueueFull(int code, String msg) implements GlamMintError {

    public static final RequestQueueFull INSTANCE = new RequestQueueFull(
        6012, "Request queue full"
    );
  }

  record ProtocolFeesNotCrystallized(int code, String msg) implements GlamMintError {

    public static final ProtocolFeesNotCrystallized INSTANCE = new ProtocolFeesNotCrystallized(
        6013, "Protocol fees should be crystallized before updating"
    );
  }

  record AmountBelowMinimum(int code, String msg) implements GlamMintError {

    public static final AmountBelowMinimum INSTANCE = new AmountBelowMinimum(
        6014, "Amount is below the minimum required"
    );
  }

  record ManagerFeesNotCrystallized(int code, String msg) implements GlamMintError {

    public static final ManagerFeesNotCrystallized INSTANCE = new ManagerFeesNotCrystallized(
        6015, "Manager fees should be crystallized before updating"
    );
  }

  record AmountAboveMaximum(int code, String msg) implements GlamMintError {

    public static final AmountAboveMaximum INSTANCE = new AmountAboveMaximum(
        6016, "Amount exceeds maximum threshold"
    );
  }

  record InsufficientEscrowBalance(int code, String msg) implements GlamMintError {

    public static final InsufficientEscrowBalance INSTANCE = new InsufficientEscrowBalance(
        6017, "Insufficient escrow balance for fee burn"
    );
  }

  record TokenAclManagesFreezeThaw(int code, String msg) implements GlamMintError {

    public static final TokenAclManagesFreezeThaw INSTANCE = new TokenAclManagesFreezeThaw(
        6018, "Token ACL is enabled; freeze/thaw is managed by the Token ACL program"
    );
  }

  record InvalidMintState(int code, String msg) implements GlamMintError {

    public static final InvalidMintState INSTANCE = new InvalidMintState(
        6019, "Invalid mint state"
    );
  }
}
