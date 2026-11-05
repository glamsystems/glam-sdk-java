package systems.glam.sdk;

import org.junit.jupiter.api.Test;
import software.sava.idl.clients.core.gen.SerDe;
import systems.glam.sdk.idl.programs.glam.mint.gen.types.MintModel;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.StateModel;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// A generated record measures an Option in its own l() and leaves the bytes of an optional string
// to the commons the Solana BOM pins (SerDeUtil.writeOptionalVector), so the two must agree on
// the one value whose rule moved with idl-src-gen 36d2bd25 and idl-clients 25.19.14: an empty
// string or vector is present, an empty array for a sized fixed array refused, and only null is
// absent. The regeneration without the BOM bump compiles, as does the bump without the
// regeneration, and the halves then disagree on the wire alone: l() sizes a buffer write does not
// fill, or write overruns the one l() sized. The four records below are every exported one that
// writes an Option over a string, bytes or vector through a commons helper (writeOptionalVector;
// no exported record calls writeOptionalArray, lenOptionalVector or SerDeUtil.getBytes). The
// oracle is Borsh, not the generated reader: None is one zero byte, and Some("") its tag and a
// zero u32 length, so a StateModel with every field absent is 12 bytes and one with an empty uri
// 16, a MintModel 14 and, with an empty symbol and uri, 22.
final class GeneratedOptionEncodingTests {

  private static final OptionalLong NONE = OptionalLong.empty();
  private static final byte[] SOME_EMPTY = {1, 0, 0, 0, 0};

  // Writes the record into a buffer l() sized and holds write to that size: a disagreement between
  // l() and the commons fails here.
  private static byte[] encode(final SerDe model) {
    final var data = new byte[model.l()];
    assertEquals(data.length, model.write(data, 0), "l() and write disagree on an empty value");
    return data;
  }

  // The whole layout: every field absent is a zero byte each, and Some("") sits at the given offsets.
  private static byte[] layout(final int length, final int... someEmptyOffsets) {
    final var expected = new byte[length];
    for (final int offset : someEmptyOffsets) {
      System.arraycopy(SOME_EMPTY, 0, expected, offset, SOME_EMPTY.length);
    }
    return expected;
  }

  @Test
  void stateModelSendsAnEmptyUriAsPresent() {
    final var model = StateModel.createRecord(null, null, "", null, null, null, null, null, null, NONE, null, null);
    final var data = encode(model);
    assertArrayEquals(layout(16, 2), data);
    final var decoded = StateModel.read(data, 0);
    assertEquals("", decoded.uri());
    assertArrayEquals(data, encode(decoded));
  }

  @Test
  void stagingStateModelSendsAnEmptyUriAsPresent() {
    final var model = systems.glam.sdk.idl.programs.glam.staging.protocol.gen.types.StateModel.createRecord(
        null, null, "", null, null, null, null, null, null, NONE, null, null);
    final var data = encode(model);
    assertArrayEquals(layout(16, 2), data);
    final var decoded = systems.glam.sdk.idl.programs.glam.staging.protocol.gen.types.StateModel.read(data, 0);
    assertEquals("", decoded.uri());
    assertArrayEquals(data, encode(decoded));
  }

  @Test
  void mintModelSendsAnEmptySymbolAndUriAsPresent() {
    final var model = MintModel.createRecord("", null, "", NONE, null, null, null, null, NONE, NONE, NONE, NONE, null, null);
    final var data = encode(model);
    assertArrayEquals(layout(22, 0, 6), data);
    final var decoded = MintModel.read(data, 0);
    assertEquals("", decoded.symbol());
    assertEquals("", decoded.uri());
    assertArrayEquals(data, encode(decoded));
  }

  @Test
  void stagingMintModelSendsAnEmptySymbolAndUriAsPresent() {
    final var model = systems.glam.sdk.idl.programs.glam.staging.mint.gen.types.MintModel.createRecord(
        "", null, "", NONE, null, null, null, null, NONE, NONE, NONE, NONE, null, null);
    final var data = encode(model);
    assertArrayEquals(layout(22, 0, 6), data);
    final var decoded = systems.glam.sdk.idl.programs.glam.staging.mint.gen.types.MintModel.read(data, 0);
    assertEquals("", decoded.symbol());
    assertEquals("", decoded.uri());
    assertArrayEquals(data, encode(decoded));
  }

  @Test
  void nullStaysAbsent() {
    final var state = StateModel.createRecord(null, null, null, null, null, null, null, null, null, NONE, null, null);
    final var stateData = encode(state);
    assertArrayEquals(layout(12), stateData);
    assertNull(StateModel.read(stateData, 0).uri());
    final var mint = MintModel.createRecord(null, null, null, NONE, null, null, null, null, NONE, NONE, NONE, NONE, null, null);
    final var mintData = encode(mint);
    assertArrayEquals(layout(14), mintData);
    final var decoded = MintModel.read(mintData, 0);
    assertNull(decoded.symbol());
    assertNull(decoded.uri());
  }
}
