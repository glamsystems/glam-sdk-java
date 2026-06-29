package systems.glam.sdk.idl.programs.glam.staging.registered_positions.gen.types;

import org.junit.jupiter.api.Test;
import software.sava.core.accounts.PublicKey;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class RegisteredPositionUnsignedTypesTest {

  private static final long U32_MAX = 4_294_967_295L;
  private static final int U16_MAX = 65_535;

  @Test
  void positionConfigRoundTripsUnsignedFreshness() {
    final var config = new PositionConfig(
        bytes(32, 1),
        RegisteredPositionType.Valued,
        RegisteredSourceType.Wormhole,
        new DenominationSpec(Denomination.Usd, PublicKey.NONE),
        PublicKey.NONE,
        NativeCustodyKind.SplToken,
        true,
        U32_MAX,
        new PublicKey[0],
        new PublicKey[0],
        new PublicKey[0]
    );

    final var decoded = roundTrip(config, new byte[config.l()], PositionConfig::read);

    assertArrayEquals(config.positionId(), decoded.positionId());
    assertEquals(U32_MAX, decoded.freshnessOverrideSecs());
  }

  @Test
  void wormholeInputRoundTripsUnsignedU16AndU32() {
    final var config = new WormholeObservationConfigInput(
        bytes(32, 2),
        U16_MAX,
        bytes(32, 3),
        255,
        254,
        U32_MAX
    );

    final var decoded = roundTrip(config, new byte[config.l()], WormholeObservationConfigInput::read);

    assertEquals(U16_MAX, decoded.emitterChain());
    assertEquals(255, decoded.payloadVersion());
    assertEquals(254, decoded.payloadType());
    assertEquals(U32_MAX, decoded.maxAgeSeconds());
  }

  @Test
  void wormholeAccountRoundTripsUnsignedU16AndU32() {
    final var config = new WormholeObservationConfig(
        PublicKey.NONE,
        WormholeObservationConfig.DISCRIMINATOR,
        PublicKey.NONE,
        bytes(32, 4),
        U16_MAX,
        bytes(32, 5),
        253,
        252,
        U32_MAX,
        true,
        12_345L,
        bytes(32, 6),
        251
    );

    final var decoded = roundTrip(config, new byte[config.l()], WormholeObservationConfig::read);

    assertEquals(U16_MAX, decoded.emitterChain());
    assertEquals(U32_MAX, decoded.maxAgeSeconds());
  }

  @Test
  void hyperliquidInputRoundTripsUnsignedPerpDexIndex() {
    final var config = new WormholeHyperliquidObservationConfigInput(
        bytes(32, 7),
        bytes(20, 8),
        bytes(20, 9),
        bytes(20, 10),
        U32_MAX,
        99L
    );

    final var decoded = roundTrip(config, new byte[config.l()], WormholeHyperliquidObservationConfigInput::read);

    assertEquals(U32_MAX, decoded.perpDexIndex());
    assertEquals(99L, decoded.usdcSpotToken());
  }

  @Test
  void hyperliquidAccountRoundTripsUnsignedPerpDexIndex() {
    final var config = new WormholeHyperliquidObservationConfig(
        PublicKey.NONE,
        WormholeHyperliquidObservationConfig.DISCRIMINATOR,
        PublicKey.NONE,
        bytes(32, 11),
        bytes(20, 12),
        bytes(20, 13),
        bytes(20, 14),
        U32_MAX,
        100L,
        250
    );

    final var decoded = roundTrip(config, new byte[config.l()], WormholeHyperliquidObservationConfig::read);

    assertEquals(U32_MAX, decoded.perpDexIndex());
    assertEquals(100L, decoded.usdcSpotToken());
  }

  private static <T extends software.sava.idl.clients.core.gen.SerDe> T roundTrip(
      final T value,
      final byte[] data,
      final Reader<T> reader
  ) {
    assertEquals(data.length, value.write(data, 0));
    return reader.read(data, 0);
  }

  private static byte[] bytes(final int length, final int seed) {
    final var bytes = new byte[length];
    for (int i = 0; i < length; ++i) {
      bytes[i] = (byte) (seed + i);
    }
    return bytes;
  }

  private interface Reader<T> {
    T read(byte[] data, int offset);
  }
}
