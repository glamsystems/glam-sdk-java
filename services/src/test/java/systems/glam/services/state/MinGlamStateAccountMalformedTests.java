package systems.glam.services.state;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;
import software.sava.core.encoding.ByteUtil;
import software.sava.idl.clients.core.gen.SerDeUtil;
import systems.glam.sdk.GlamEnv;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.DelegateAcl;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.IntegrationAcl;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.IntegrationPermissions;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.ProtocolPermissions;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.ProtocolPolicy;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateAccount;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
  /// parse still fails further in, on the data the count promised: the walk
  /// reads the bytes after the real ACLs as more of them and stops at the first
  /// nested prefix those bytes cannot satisfy). One past that is a
  /// contradiction and must be rejected by the prefix check. Pinning both sides
  /// is what fixes the comparison direction and the `remaining` arithmetic;
  /// asserting only the far-oversized case leaves either free.
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
        String.valueOf(atBoundFailure.getMessage()).startsWith("Length prefix " + remaining + " "),
        "a count equal to the bytes remaining must clear its own length-prefix guard, was: "
            + atBoundFailure.getMessage()
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

  /// The same landmine reaches the update path, which the parse-path guard does
  /// not cover: `createIfChanged` re-searches the assets vector whenever it
  /// moved, and stored the negative index just as `createRecord` used to. It is
  /// the worse of the two, because the poisoned record is *returned* rather than
  /// thrown — callers guard the call with `catch (RuntimeException)`, see
  /// nothing, and store it, so the throw surfaces later and somewhere else.
  ///
  /// Rewriting the base asset's entry inside the vector, and leaving the
  /// `BASE_ASSET_MINT` field alone, is what reaches it: the assets bytes now
  /// differ, so the reuse-the-witness shortcut is skipped, and the witness's own
  /// base asset is no longer among them.
  @Test
  void aBaseAssetMissingFromAChangedAssetsVectorIsRejectedOnUpdate() {
    final var witness = MinGlamStateAccount.createRecord(
        GlamEnv.PRODUCTION, MinGlamStateAccountTests.fixtureData(), SLOT);
    final var baseAsset = witness.baseAssetMint();

    final byte[] data = MinGlamStateAccountTests.fixtureData();
    final int numAssets = ByteUtil.getInt32LE(data, StateAccount.ASSETS_OFFSET);
    final int firstAsset = StateAccount.ASSETS_OFFSET + Integer.BYTES;
    final byte[] absent = new byte[PublicKey.PUBLIC_KEY_LENGTH];
    absent[0] = (byte) 0xFE;
    absent[PublicKey.PUBLIC_KEY_LENGTH - 1] = (byte) 0xFE;
    boolean replaced = false;
    for (int i = 0; i < numAssets; ++i) {
      final int offset = firstAsset + (i * PublicKey.PUBLIC_KEY_LENGTH);
      if (baseAsset.equals(PublicKey.readPubKey(data, offset))) {
        System.arraycopy(absent, 0, data, offset, absent.length);
        replaced = true;
        break;
      }
    }
    assertTrue(replaced, "the fixture's base asset should appear in its own assets vector");

    final var failure = assertThrows(
        IllegalStateException.class,
        () -> witness.createIfChanged(MinGlamStateAccountTests.accountInfo(SLOT + 1, data))
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

  @Test
  void aBaseAssetAtIndexZeroIsAcceptedOnUpdate() {
    final byte[] initial = MinGlamStateAccountTests.fixtureData();
    final var parsed = MinGlamStateAccount.createRecord(GlamEnv.PRODUCTION, initial, SLOT);
    final var firstAsset = parsed.assets()[0];
    firstAsset.write(initial, StateAccount.BASE_ASSET_MINT_OFFSET);
    final var witness = MinGlamStateAccount.createRecord(GlamEnv.PRODUCTION, initial, SLOT);
    assertEquals(0, witness.baseAssetIndex());

    final byte[] changed = initial.clone();
    final int firstOffset = StateAccount.ASSETS_OFFSET + Integer.BYTES;
    final int secondOffset = firstOffset + PublicKey.PUBLIC_KEY_LENGTH;
    final var rawFirst = PublicKey.readPubKey(changed, firstOffset);
    final var rawSecond = PublicKey.readPubKey(changed, secondOffset);
    assertFalse(rawFirst.equals(rawSecond), "the fixture needs two distinct assets to reorder");
    rawFirst.write(changed, secondOffset);
    rawSecond.write(changed, firstOffset);

    final var updated = witness.createIfChanged(MinGlamStateAccountTests.accountInfo(SLOT + 1, changed));
    assertNotNull(updated);
    assertEquals(0, updated.baseAssetIndex());
    assertEquals(firstAsset, updated.baseAssetMint());
    assertArrayEquals(witness.assets(), updated.assets());
  }

  /// The delegate and external-positions count prefixes, located with the generated full parser
  /// (`StateAccount.read`) rather than with the walk under test: each one is the previous prefix
  /// plus that section's serialized length.
  private static int delegatesCountOffset(final byte[] data) {
    final var stateAccount = StateAccount.read(data, 0);
    return integrationsCountOffset(data) + SerDeUtil.lenVector(4, stateAccount.integrationAcls());
  }

  static int externalPositionsCountOffset(final byte[] data) {
    final var stateAccount = StateAccount.read(data, 0);
    return delegatesCountOffset(data) + SerDeUtil.lenVector(4, stateAccount.delegateAcls());
  }

  /// One past the bytes the fixture holds after the prefix at `prefixOffset`: the smallest count
  /// the bound refuses, and already a count no account of this size could describe.
  private static int oneMoreThanRemaining(final int prefixOffset) {
    return MinGlamStateAccountTests.fixtureData().length - (prefixOffset + Integer.BYTES) + 1;
  }

  /// A corrupt count in an update must be refused at the count, before anything is allocated
  /// from it, and the same way the parse path refuses the very same bytes. The parse is the
  /// oracle: for one corrupted count it throws one specific exception, and an update of a good
  /// record must throw exactly that one. The message is what tells a refusal at the count from a
  /// later failure, because before the fix an oversized count failed an update with an
  /// IndexOutOfBoundsException too, just later: the walk reading past the end of the account,
  /// or `readArray` running out of keys after an allocation the count had sized.
  private static void assertRefusedOnUpdateAsAtParse(final int prefixOffset, final int count) {
    final byte[] corrupt = MinGlamStateAccountTests.fixtureData();
    ByteUtil.putInt32LE(corrupt, prefixOffset, count);

    final var atParse = assertThrows(
        IndexOutOfBoundsException.class,
        () -> MinGlamStateAccount.createRecord(GlamEnv.PRODUCTION, corrupt, SLOT)
    );
    assertTrue(
        String.valueOf(atParse.getMessage()).startsWith("Length prefix " + count + " "),
        "the parse should refuse the count itself, was: " + atParse.getMessage()
    );

    final var witness = MinGlamStateAccount.createRecord(
        GlamEnv.PRODUCTION, MinGlamStateAccountTests.fixtureData(), SLOT);
    final var onUpdate = assertThrows(
        IndexOutOfBoundsException.class,
        () -> witness.createIfChanged(MinGlamStateAccountTests.accountInfo(SLOT + 1, corrupt))
    );
    assertEquals(atParse.getClass(), onUpdate.getClass());
    assertEquals(
        atParse.getMessage(), onUpdate.getMessage(),
        "an update must be refused at the count, the way the parse refuses the same bytes"
    );
  }

  /// `1 << 27` keys of 32 bytes are exactly 2^32 bytes, so this negative count moves the int
  /// offset arithmetic to the same place the real count does: the walk after the vector is
  /// undisturbed and only the allocation sees the count. Before the fix that was a raw
  /// `NegativeArraySizeException` from `new PublicKey[numAssets]`.
  @Test
  void aNegativeAssetsCountIsRejectedOnUpdate() {
    final int numAssets = ByteUtil.getInt32LE(MinGlamStateAccountTests.fixtureData(), StateAccount.ASSETS_OFFSET);
    assertRefusedOnUpdateAsAtParse(StateAccount.ASSETS_OFFSET, numAssets - (1 << 27));
  }

  /// Before the fix the count went straight into offset arithmetic, and the update failed
  /// reading an integration count at offset 254,124 of an 8,200-byte account.
  @Test
  void anOversizedAssetsCountIsRejectedOnUpdate() {
    assertRefusedOnUpdateAsAtParse(
        StateAccount.ASSETS_OFFSET, oneMoreThanRemaining(StateAccount.ASSETS_OFFSET));
  }

  /// Before the fix the walk ran a negative count zero times and the update reached
  /// `new ProtocolIntegration[-1]`: the raw `NegativeArraySizeException` the parse path was
  /// fixed for on 2026-08-06, recurring on the update path.
  @Test
  void aNegativeIntegrationCountIsRejectedOnUpdate() {
    assertRefusedOnUpdateAsAtParse(integrationsCountOffset(MinGlamStateAccountTests.fixtureData()), -1);
  }

  /// Before the fix the walk iterated an oversized count until it read outside the account, so
  /// the update failed deep inside the walk instead of at the count.
  @Test
  void anOversizedIntegrationCountIsRejectedOnUpdate() {
    final int prefixOffset = integrationsCountOffset(MinGlamStateAccountTests.fixtureData());
    assertRefusedOnUpdateAsAtParse(prefixOffset, oneMoreThanRemaining(prefixOffset));
  }

  /// Before the fix: a raw `NegativeArraySizeException` from `new PublicKey[numDelegates]`.
  @Test
  void aNegativeDelegateCountIsRejectedOnUpdate() {
    assertRefusedOnUpdateAsAtParse(delegatesCountOffset(MinGlamStateAccountTests.fixtureData()), -1);
  }

  /// Before the fix: a failure inside the walk, which iterated the count past the account's end.
  @Test
  void anOversizedDelegateCountIsRejectedOnUpdate() {
    final int prefixOffset = delegatesCountOffset(MinGlamStateAccountTests.fixtureData());
    assertRefusedOnUpdateAsAtParse(prefixOffset, oneMoreThanRemaining(prefixOffset));
  }

  /// Before the fix: a raw `NegativeArraySizeException` from `new PublicKey[numExternalPositions]`.
  /// The `negative-external-positions-count` fuzz seed is these bytes.
  @Test
  void aNegativeExternalPositionsCountIsRejectedOnUpdate() {
    assertRefusedOnUpdateAsAtParse(externalPositionsCountOffset(MinGlamStateAccountTests.fixtureData()), -1);
  }

  /// Before the fix the update allocated an array the size of the count and failed only when
  /// `readArray` ran out of keys. With this count that array was 28 KB; with 0x72e2ac09, the
  /// count a misaligned walk reads out of key bytes, it would have been about 7.2 GiB.
  @Test
  void anOversizedExternalPositionsCountIsRejectedOnUpdate() {
    final int prefixOffset = externalPositionsCountOffset(MinGlamStateAccountTests.fixtureData());
    assertRefusedOnUpdateAsAtParse(prefixOffset, oneMoreThanRemaining(prefixOffset));
  }

  /// The first integration ACL's policy count, and its first policy's data length: the nested
  /// prefixes both walks iterate. Located with the generated parser, which also confirms the
  /// fixture still holds an ACL with a policy for these tests to corrupt.
  private static int policyCountOffset(final byte[] data) {
    final var stateAccount = StateAccount.read(data, 0);
    assertTrue(stateAccount.integrationAcls()[0].protocolPolicies().length > 0,
        "the fixture's first integration ACL must carry a policy");
    return integrationsCountOffset(data) + Integer.BYTES + IntegrationAcl.PROTOCOL_POLICIES_OFFSET;
  }

  private static int policyDataLengthOffset(final byte[] data) {
    return policyCountOffset(data) + Integer.BYTES + ProtocolPolicy.DATA_OFFSET;
  }

  /// The first delegate's integration-permissions count, and its first entry's protocol
  /// permissions count.
  private static int integrationPermissionsCountOffset(final byte[] data) {
    final var stateAccount = StateAccount.read(data, 0);
    assertTrue(stateAccount.delegateAcls()[0].integrationPermissions().length > 0,
        "the fixture's first delegate must carry an integration permission");
    return delegatesCountOffset(data) + Integer.BYTES + DelegateAcl.INTEGRATION_PERMISSIONS_OFFSET;
  }

  private static int protocolPermissionsCountOffset(final byte[] data) {
    return integrationPermissionsCountOffset(data) + Integer.BYTES
        + IntegrationPermissions.PROTOCOL_PERMISSIONS_OFFSET;
  }

  /// Before the fix both walks iterated the count over whatever bytes followed, failing deep
  /// inside the account or past its end rather than at the count.
  @Test
  void anOversizedPolicyCountIsRejected() {
    final int prefixOffset = policyCountOffset(MinGlamStateAccountTests.fixtureData());
    assertRefusedOnUpdateAsAtParse(prefixOffset, oneMoreThanRemaining(prefixOffset));
  }

  /// A policy is its two-byte flag plus a length-prefixed data vector, so this length cancels the
  /// rest of the stride and holds the walk in place: with a large policy count beside it, both
  /// walks spun for billions of iterations without moving. The length is refused instead.
  @Test
  void aNegativePolicyDataLengthIsRejected() {
    assertRefusedOnUpdateAsAtParse(
        policyDataLengthOffset(MinGlamStateAccountTests.fixtureData()),
        -(ProtocolPolicy.DATA_OFFSET + Integer.BYTES)
    );
  }

  @Test
  void anOversizedIntegrationPermissionsCountIsRejected() {
    final int prefixOffset = integrationPermissionsCountOffset(MinGlamStateAccountTests.fixtureData());
    assertRefusedOnUpdateAsAtParse(prefixOffset, oneMoreThanRemaining(prefixOffset));
  }

  /// Before the fix a negative count stepped both walks backwards by whole permission blocks.
  @Test
  void aNegativeProtocolPermissionsCountIsRejected() {
    assertRefusedOnUpdateAsAtParse(protocolPermissionsCountOffset(MinGlamStateAccountTests.fixtureData()), -1);
  }

  /// The permission block is the count times ten bytes, an int product: this count wraps it to
  /// exactly minus the rest of the entry's stride, which held both walks in place the way a
  /// negative policy length does. It is refused as a count no account could describe.
  @Test
  void aWrappingProtocolPermissionsCountIsRejected() {
    final long wrappedBlock = (1L << Integer.SIZE)
        - (IntegrationPermissions.PROTOCOL_PERMISSIONS_OFFSET + Integer.BYTES);
    assertEquals(0, wrappedBlock % ProtocolPermissions.BYTES);
    final int count = (int) (wrappedBlock / ProtocolPermissions.BYTES);
    assertEquals(
        -(IntegrationPermissions.PROTOCOL_PERMISSIONS_OFFSET + Integer.BYTES),
        count * ProtocolPermissions.BYTES
    );
    assertRefusedOnUpdateAsAtParse(protocolPermissionsCountOffset(MinGlamStateAccountTests.fixtureData()), count);
  }
}
