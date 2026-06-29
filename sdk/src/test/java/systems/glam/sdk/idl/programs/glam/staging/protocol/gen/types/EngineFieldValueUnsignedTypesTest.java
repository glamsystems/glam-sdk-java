package systems.glam.sdk.idl.programs.glam.staging.protocol.gen.types;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

final class EngineFieldValueUnsignedTypesTest {

  private static final long U32_MAX = 4_294_967_295L;

  @Test
  void u32VariantRoundTripsAsUnsignedLong() {
    final var value = new EngineFieldValue.U32(U32_MAX);
    final var data = new byte[value.l()];

    assertEquals(data.length, value.write(data, 0));
    final var decoded = assertInstanceOf(EngineFieldValue.U32.class, EngineFieldValue.read(data, 0));

    assertEquals(U32_MAX, decoded.val());
  }

  @Test
  void vecU32VariantRoundTripsAsUnsignedLongArray() {
    final var value = new EngineFieldValue.VecU32(new long[]{0L, 1L, U32_MAX});
    final var data = new byte[value.l()];

    assertEquals(data.length, value.write(data, 0));
    final var decoded = assertInstanceOf(EngineFieldValue.VecU32.class, EngineFieldValue.read(data, 0));

    assertArrayEquals(new long[]{0L, 1L, U32_MAX}, decoded.val());
  }
}
