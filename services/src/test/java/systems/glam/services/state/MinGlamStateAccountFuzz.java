package systems.glam.services.state;

import software.sava.core.accounts.PublicKey;
import software.sava.rpc.json.http.response.AccountInfo;
import software.sava.rpc.json.http.response.Context;
import systems.glam.sdk.GlamAccounts;
import systems.glam.sdk.GlamEnv;

import java.math.BigInteger;
import java.util.function.Supplier;

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
/// target drives `createIfChanged` as well as `createRecord`. Each input is
/// driven three ways:
///
/// 1. parsed by `createRecord`, with the parsed surface touched;
/// 2. if it parsed, offered back to its own record at a newer slot — the
///    unchanged-bytes walk, where every section compares equal;
/// 3. always, offered as an update of a well-formed record (the mainnet
///    snapshot the unit tests use), so whichever sections the input changes
///    are reparsed by `createIfChanged`, and the record it returns is touched
///    like a parsed one.
///
/// Step 3 is the one that reaches the update path's own allocations. Without
/// it, `createIfChanged` only ever saw bytes `createRecord` had just accepted,
/// so every section compared unchanged — which is how defect (1) survived on
/// the update path until 2026-10-09: the four counts an update reparses were
/// read raw there, and a corrupt one sized an array directly.
///
/// Malformed-input contract: garbage in -> `RuntimeException` out. This repo
/// rejects malformed accounts rather than crashing (see
/// `KaminoCacheTests.truncatedAccountsAreSkippedNotCrashed`), so any
/// `RuntimeException` is tolerated but one: a raw `NegativeArraySizeException`
/// is not a rejection but an array sized by a count nobody checked — defect
/// (1)'s only symptom, on either path — so it propagates as a finding. Beyond
/// it, Jazzer flags hangs, memory exhaustion, and any non-`RuntimeException`
/// throwable — which is exactly how defect (2) above would present.
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

  /// The bytes of the well-formed record step 3 offers each input to. Only the
  /// bytes are held across inputs: the record is parsed again for every input,
  /// so nothing the code under test computes outlives the input it ran for.
  private static final byte[] WITNESS_DATA = MinGlamStateAccountTests.fixtureData();

  private static AccountInfo<byte[]> accountInfo(final byte[] data, final long slot) {
    return new AccountInfo<>(
        STATE_ACCOUNT_KEY, new Context(slot, null), false, 0,
        GlamAccounts.MAIN_NET.protocolProgram(), BigInteger.ZERO, 0, data
    );
  }

  /// One step under the malformed-input contract: a `RuntimeException` is a
  /// rejection and the step yields null, except the raw
  /// `NegativeArraySizeException` the class comment carves out, which
  /// propagates.
  private static MinGlamStateAccount tolerating(final Supplier<MinGlamStateAccount> step) {
    try {
      return step.get();
    } catch (final NegativeArraySizeException finding) {
      // an allocation sized by an unchecked count, not a rejection
      throw finding;
    } catch (final RuntimeException tolerated) {
      return null;
    }
  }

  /// A section that walked into a nonsense shape surfaces here rather than at
  /// first use.
  private static void touch(final MinGlamStateAccount record) {
    record.accountType();
    record.baseAssetMint();
    record.baseAssetDecimals();
    record.assets();
    record.delegates();
    record.externalPositions();
    record.protocolIntegrations();
  }

  public static void fuzzerTestOneInput(final byte[] data) {
    // 1. truncated or malformed state bytes are rejected — that is the contract
    final var record = tolerating(() -> MinGlamStateAccount.createRecord(GlamEnv.PRODUCTION, data, SLOT));
    if (record != null) {
      touch(record);
      // 2. the change-detection walk over unchanged bytes. delegateAclsOffset
      // and externalPositionsOffset re-traverse the same nested length
      // prefixes independently of the parse path, and that is where defect (2)
      // lived. An immutable base field appearing to change is a declared
      // error, and a malformed tail is rejected the same way as on the parse
      // path.
      tolerating(() -> record.createIfChanged(accountInfo(data, SLOT + 1)));
    }

    // 3. the input as an update of a good record: the sections it changes are
    // reparsed, from counts the input controls
    final var witness = MinGlamStateAccount.createRecord(GlamEnv.PRODUCTION, WITNESS_DATA.clone(), SLOT);
    final var updated = tolerating(() -> witness.createIfChanged(accountInfo(data, SLOT + 1)));
    if (updated != null) {
      touch(updated);
    }
  }
}
