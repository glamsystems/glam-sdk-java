package systems.glam.sdk;

import software.sava.core.accounts.PublicKey;
import software.sava.core.accounts.meta.AccountMeta;
import software.sava.core.tx.Instruction;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

import static software.sava.core.encoding.ByteUtil.putInt128LE;

/**
 * Package-private bridge for guarded Mint handlers newer than the checked-in
 * generated IDLs. Public callers use {@link GlamAccountClient}, which derives
 * the ordinary instruction first; this codec validates its complete shape and
 * changes only the discriminator plus reference-NAV argument.
 */
final class GlamMintReferenceNavCodec {

  private static final int DISCRIMINATOR_LENGTH = 8;
  private static final byte[] FULFILL_DISCRIMINATOR = {
      (byte) 143, 2, 52, (byte) 206, (byte) 174, (byte) 164, (byte) 247, 72
  };
  private static final byte[] FULFILL_WITH_REF_NAV_DISCRIMINATOR = {
      (byte) 214, 80, 104, 122, 65, (byte) 144, 19, (byte) 209
  };
  private static final boolean[] FULFILL_WRITABLE = {
      true, true, true, false, true, true, true, false, true, true,
      false, false, false, false, false
  };
  private static final byte[] SUBSCRIBE_DISCRIMINATOR = {
      (byte) 254, 28, (byte) 191, (byte) 138, (byte) 156, (byte) 179, (byte) 183, 53
  };
  private static final byte[] SUBSCRIBE_WITH_REF_NAV_DISCRIMINATOR = {
      39, (byte) 136, 23, 75, 82, (byte) 217, (byte) 248, (byte) 141
  };
  private static final boolean[] SUBSCRIBE_WRITABLE = {
      true, false, true, false, false, true, true, true, false, true, true, true,
      false, false, false, false, false, false
  };

  static Instruction fulfillWithRefNav(final PublicKey expectedMintProgram,
                                       final Instruction fulfill,
                                       final BigInteger referenceNav) {
    validateSource(
        expectedMintProgram,
        fulfill,
        "fulfill",
        FULFILL_DISCRIMINATOR,
        FULFILL_WRITABLE
    );
    final var argumentLength = fulfill.len() - DISCRIMINATOR_LENGTH;
    if (argumentLength != 1 && argumentLength != 5) {
      throw new IllegalArgumentException("GLAM Mint fulfill instruction has an invalid argument length");
    }
    final var limitTag = fulfill.data()[fulfill.offset() + DISCRIMINATOR_LENGTH];
    if ((limitTag == 0 && argumentLength != 1) || (limitTag == 1 && argumentLength != 5)) {
      throw new IllegalArgumentException("GLAM Mint fulfill instruction has an invalid Option<u32> limit");
    }
    if (limitTag != 0 && limitTag != 1) {
      throw new IllegalArgumentException("GLAM Mint fulfill instruction has an invalid Option<u32> tag");
    }
    return withReferenceNav(
        fulfill,
        FULFILL_WITH_REF_NAV_DISCRIMINATOR,
        argumentLength,
        referenceNav
    );
  }

  static Instruction subscribeWithRefNav(final PublicKey expectedMintProgram,
                                         final Instruction subscribe,
                                         final BigInteger referenceNav) {
    validateSource(
        expectedMintProgram,
        subscribe,
        "subscribe",
        SUBSCRIBE_DISCRIMINATOR,
        SUBSCRIBE_WRITABLE
    );
    final var argumentLength = subscribe.len() - DISCRIMINATOR_LENGTH;
    if (argumentLength != Long.BYTES) {
      throw new IllegalArgumentException("GLAM Mint subscribe instruction has an invalid u64 amount");
    }
    return withReferenceNav(
        subscribe,
        SUBSCRIBE_WITH_REF_NAV_DISCRIMINATOR,
        argumentLength,
        referenceNav
    );
  }

  static void requirePrimaryMintId(final int mintId) {
    if (mintId != 0) {
      throw new IllegalArgumentException("guarded GLAM Mint handlers require primary mint id 0");
    }
  }

  static void requireU32Limit(final OptionalLong limit) {
    if (limit != null
        && limit.isPresent()
        && (limit.getAsLong() < 0 || limit.getAsLong() > 4_294_967_295L)) {
      throw new IllegalArgumentException("GLAM Mint fulfill limit must fit in u32");
    }
  }

  private static void validateSource(final PublicKey expectedMintProgram,
                                     final Instruction source,
                                     final String name,
                                     final byte[] discriminator,
                                     final boolean[] writable) {
    Objects.requireNonNull(expectedMintProgram, "expectedMintProgram");
    Objects.requireNonNull(source, name);
    if (!source.programId().invoked()
        || !expectedMintProgram.equals(source.programId().publicKey())) {
      throw new IllegalArgumentException("instruction is not invoked by the expected GLAM Mint program");
    }
    if (source.offset() < 0
        || source.len() < DISCRIMINATOR_LENGTH
        || source.offset() > source.data().length - source.len()) {
      throw new IllegalArgumentException("GLAM Mint " + name + " instruction data is truncated");
    }
    requireDiscriminator(source.data(), source.offset(), discriminator, name);
    validateAccounts(source.accounts(), name, writable);
  }

  private static void validateAccounts(final List<AccountMeta> accounts,
                                       final String name,
                                       final boolean[] writable) {
    if (accounts.size() != writable.length) {
      throw new IllegalArgumentException("GLAM Mint " + name + " instruction has an invalid account count");
    }
    for (var index = 0; index < writable.length; index++) {
      final var account = accounts.get(index);
      final var signer = index == 5;
      if (account.write() != writable[index]
          || account.signer() != signer
          || account.invoked()
          || account.feePayer()) {
        throw new IllegalArgumentException(
            "GLAM Mint " + name + " instruction has invalid account flags at index " + index
        );
      }
    }
  }

  private static Instruction withReferenceNav(final Instruction source,
                                              final byte[] discriminator,
                                              final int argumentLength,
                                              final BigInteger referenceNav) {
    Objects.requireNonNull(referenceNav, "referenceNav");
    GlamMintMath.requireI128(referenceNav, "referenceNav");
    final var encoded = new byte[DISCRIMINATOR_LENGTH + argumentLength + 1 + 16];
    System.arraycopy(discriminator, 0, encoded, 0, DISCRIMINATOR_LENGTH);
    System.arraycopy(
        source.data(),
        source.offset() + DISCRIMINATOR_LENGTH,
        encoded,
        DISCRIMINATOR_LENGTH,
        argumentLength
    );
    final var navTagOffset = DISCRIMINATOR_LENGTH + argumentLength;
    encoded[navTagOffset] = 1;
    putInt128LE(encoded, navTagOffset + 1, referenceNav);
    return Instruction.createInstruction(source.programId(), source.accounts(), encoded);
  }

  private static void requireDiscriminator(final byte[] data,
                                           final int offset,
                                           final byte[] expected,
                                           final String name) {
    for (var index = 0; index < expected.length; index++) {
      if (data[offset + index] != expected[index]) {
        throw new IllegalArgumentException("instruction is not GLAM Mint " + name);
      }
    }
  }

  private GlamMintReferenceNavCodec() {
  }
}
