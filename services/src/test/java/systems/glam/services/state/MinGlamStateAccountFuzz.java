package systems.glam.services.state;

import software.sava.core.accounts.PublicKey;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.Context;
import systems.glam.sdk.GlamAccounts;
import systems.glam.sdk.GlamEnv;

import java.math.BigInteger;

/// Jazzer entry point for the glam-owned Glam state-account reader.
/// `MinGlamStateAccount.createRecord` walks a length-prefixed on-chain layout:
/// an assets vector, then integration ACLs, then delegate ACLs (each of which
/// nests integration-permission and protocol-permission blocks), then external
/// positions. Every one of those lengths is attacker-controlled, and the walk
/// is pure offset arithmetic — the shape the other four targets already cover
/// for their own parsers.
///
/// Two real defects were found here by hand on 2026-08-06, which is why this
/// target exists:
///
/// 1. the integration and delegate counts were read with a raw
///    `SerDeUtil.val(4, ...)` and used directly as an array size, so a corrupt
///    account surfaced a raw `NegativeArraySizeException` rather than being
///    rejected the way the `SerDeUtil` vector reads reject a bad length prefix;
/// 2. `externalPositionsOffset` advanced through a permission block one
///    constant-sized step at a time while its sibling `readDelegateAcls`
///    multiplied, so an unvalidated count drove billions of no-op additions —
///    finite, but slow enough that a mutant there reported `TIMED_OUT` instead
///    of being killed.
///
/// Both are the kind a mutator finds far faster than a reader does, and (2)
/// lives on the change-detection path rather than the parse path, so this
/// target drives `createIfChanged` as well as `createRecord`.
///
/// Malformed-input contract: garbage in -> `RuntimeException` out. This repo
/// rejects malformed accounts rather than crashing (see
/// `KaminoCacheTests.truncatedAccountsAreSkippedNotCrashed`), so any
/// `RuntimeException` is tolerated. Jazzer flags hangs, memory exhaustion, and
/// any non-`RuntimeException` throwable — which is exactly how defect (2)
/// above would present.
///
/// Seeded from the real mainnet state-account snapshot under
/// src/test/resources/fuzz/minGlamStateAccount.
///
/// Deliberately free of Jazzer imports so it compiles with the regular test
/// sources.
///
/// Run with `./gradlew :services:fuzzMinGlamStateAccount [-PmaxFuzzTime=<seconds>]`.
public final class MinGlamStateAccountFuzz {

  private static final PublicKey STATE_ACCOUNT_KEY =
      PublicKey.fromBase58Encoded("3H7XbyVaYusyzQCncfRSBx3zgvfmjGG7wrr3ARtXF1o7");

  /// Fixed non-zero origin: a zero slot makes every "slot mutated to 0"
  /// comparison equivalent by accident.
  private static final long SLOT = 337_845_331L;

  private static AccountInfo<byte[]> accountInfo(final byte[] data, final long slot) {
    return new AccountInfo<>(
        STATE_ACCOUNT_KEY, new Context(slot, null), false, 0,
        GlamAccounts.MAIN_NET.protocolProgram(), BigInteger.ZERO, 0, data
    );
  }

  public static void fuzzerTestOneInput(final byte[] data) {
    final MinGlamStateAccount record;
    try {
      record = MinGlamStateAccount.createRecord(GlamEnv.PRODUCTION, data, SLOT);
    } catch (final RuntimeException tolerated) {
      // truncated or malformed state bytes — rejection is the contract
      return;
    }

    // touch the parsed surface: a section that walked into a nonsense shape
    // surfaces here rather than at first use
    record.accountType();
    record.baseAssetMint();
    record.baseAssetDecimals();
    record.assets();
    record.delegates();
    record.externalPositions();
    record.protocolIntegrations();

    // drive the change-detection walk. delegateAclsOffset and
    // externalPositionsOffset re-traverse the same nested length prefixes
    // independently of the parse path, and that is where defect (2) lived.
    try {
      record.createIfChanged(accountInfo(data, SLOT + 1));
    } catch (final RuntimeException tolerated) {
      // an immutable base field appearing to change is a declared error, and a
      // malformed tail is rejected the same way as on the parse path
    }
  }
}
