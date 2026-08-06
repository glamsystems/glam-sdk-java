package systems.glam.services.state;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.encoding.ByteUtil;
import systems.glam.sdk.GlamEnv;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateAccount;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// `createRecord` parses untrusted on-chain bytes. This repo's contract for
/// malformed account data is "skipped, not crashed" — see
/// `KaminoCacheTests.truncatedAccountsAreSkippedNotCrashed` and the `accountData`
/// fuzz target, where the unbounded-decompression hang was found and fixed.
///
/// The vector reads go through `SerDeUtil`, which validates a length prefix
/// against the bytes remaining. The integration and delegate counts do not: they
/// are read with a raw `val(4, ...)` and used directly as an array size.
final class MinGlamStateAccountMalformedTests {

  private static final long SLOT = 337_845_331L;

  /// Offset of the integration-count prefix, which follows the assets vector.
  private static int integrationsCountOffset(final byte[] data) {
    final int numAssets = ByteUtil.getInt32LE(data, StateAccount.ASSETS_OFFSET);
    return StateAccount.ASSETS_OFFSET + Integer.BYTES + (numAssets * PublicKey.PUBLIC_KEY_LENGTH);
  }

  /// A length prefix that `SerDeUtil` guards: rejected against the byte count.
  @Test
  void aCorruptAssetsLengthIsBoundsChecked() {
    final byte[] data = MinGlamStateAccountTests.fixtureData();
    ByteUtil.putInt32LE(data, StateAccount.ASSETS_OFFSET, -1);
    assertThrows(
        IndexOutOfBoundsException.class,
        () -> MinGlamStateAccount.createRecord(GlamEnv.PRODUCTION, data, SLOT)
    );
  }

  @Test
  void aCorruptIntegrationCountIsBoundsChecked() {
    final byte[] data = MinGlamStateAccountTests.fixtureData();
    ByteUtil.putInt32LE(data, integrationsCountOffset(data), -1);
    assertThrows(
        IndexOutOfBoundsException.class,
        () -> MinGlamStateAccount.createRecord(GlamEnv.PRODUCTION, data, SLOT)
    );
  }

  @Test
  void anOversizedIntegrationCountIsBoundsChecked() {
    final byte[] data = MinGlamStateAccountTests.fixtureData();
    // a count far beyond what the account's own byte count could describe
    ByteUtil.putInt32LE(data, integrationsCountOffset(data), Integer.MAX_VALUE / 64);
    assertThrows(
        IndexOutOfBoundsException.class,
        () -> MinGlamStateAccount.createRecord(GlamEnv.PRODUCTION, data, SLOT)
    );
  }

  /// The guard's boundary: a count equal to the bytes remaining is not itself a
  /// contradiction, so the length-prefix rejection must not fire on it (the
  /// parse still fails further in, on the data the count promised). One past
  /// that is a contradiction and must be rejected by the prefix check. Pinning
  /// both sides is what fixes the comparison direction and the `remaining`
  /// arithmetic; asserting only the far-oversized case leaves either free.
  @Test
  void theCountBoundIsRemainingBytesExactly() {
    final int prefixOffset = integrationsCountOffset(MinGlamStateAccountTests.fixtureData());
    final byte[] fixture = MinGlamStateAccountTests.fixtureData();
    final int remaining = fixture.length - (prefixOffset + Integer.BYTES);

    final byte[] atBound = MinGlamStateAccountTests.fixtureData();
    ByteUtil.putInt32LE(atBound, prefixOffset, remaining);
    final var atBoundFailure = assertThrows(
        IndexOutOfBoundsException.class,
        () -> MinGlamStateAccount.createRecord(GlamEnv.PRODUCTION, atBound, SLOT)
    );
    assertFalse(
        String.valueOf(atBoundFailure.getMessage()).contains("Length prefix"),
        "a count equal to the bytes remaining must clear the length-prefix guard"
    );

    final byte[] pastBound = MinGlamStateAccountTests.fixtureData();
    ByteUtil.putInt32LE(pastBound, prefixOffset, remaining + 1);
    final var pastBoundFailure = assertThrows(
        IndexOutOfBoundsException.class,
        () -> MinGlamStateAccount.createRecord(GlamEnv.PRODUCTION, pastBound, SLOT)
    );
    assertTrue(
        String.valueOf(pastBoundFailure.getMessage()).contains("Length prefix " + (remaining + 1)),
        "one past the bytes remaining must be rejected by the length-prefix guard, was: "
            + pastBoundFailure.getMessage()
    );
  }

  /// Fuzz finding (minGlamStateAccount target, 2026-08-06, first campaign).
  /// `Arrays.binarySearch` returns `-(insertion point) - 1` for an absent key,
  /// and the record stores that index for `baseAssetMint()` to index `assets`
  /// with. A base asset missing from its own assets vector therefore parsed
  /// cleanly and produced a record that threw
  /// `ArrayIndexOutOfBoundsException: Index -3 out of bounds for length 5` on
  /// first use -- a landmine at a distance from its cause. It must be rejected
  /// at parse instead.
  @Test
  void aBaseAssetMissingFromTheAssetsVectorIsRejectedAtParse() {
    final byte[] data = MinGlamStateAccountTests.fixtureData();
    // flip the base-asset mint to a key that cannot be in the assets vector
    final byte[] absent = new byte[PublicKey.PUBLIC_KEY_LENGTH];
    absent[0] = (byte) 0xFE;
    absent[PublicKey.PUBLIC_KEY_LENGTH - 1] = (byte) 0xFE;
    System.arraycopy(absent, 0, data, StateAccount.BASE_ASSET_MINT_OFFSET, absent.length);

    final var failure = assertThrows(
        IllegalStateException.class,
        () -> MinGlamStateAccount.createRecord(GlamEnv.PRODUCTION, data, SLOT)
    );
    assertTrue(
        String.valueOf(failure.getMessage()).contains("not among the state account's"),
        "expected the base-asset membership rejection, was: " + failure.getMessage()
    );
  }

  /// The membership guard rejects only *absent* keys. `assets` is sorted, so a
  /// base asset that happens to sort first sits at index 0 — a legitimate and
  /// ordinary account that must be accepted. Only this case separates the
  /// absent-key bound from one that also rejects the first asset.
  @Test
  void aBaseAssetAtIndexZeroIsAccepted() {
    final var parsed = MinGlamStateAccount.createRecord(
        GlamEnv.PRODUCTION, MinGlamStateAccountTests.fixtureData(), SLOT);
    final var firstAsset = parsed.assets()[0];

    final byte[] data = MinGlamStateAccountTests.fixtureData();
    firstAsset.write(data, StateAccount.BASE_ASSET_MINT_OFFSET);

    final var record = MinGlamStateAccount.createRecord(GlamEnv.PRODUCTION, data, SLOT);
    assertEquals(firstAsset, record.baseAssetMint(),
        "the lowest-sorting asset is a valid base asset at index 0");
  }
}
