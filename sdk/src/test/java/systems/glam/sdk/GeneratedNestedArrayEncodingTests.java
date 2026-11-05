package systems.glam.sdk;

import org.junit.jupiter.api.Test;
import software.sava.idl.clients.core.gen.SerDeUtil;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.EngineField;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.EngineFieldName;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.EngineFieldValue;
import systems.glam.sdk.idl.programs.glam.protocol.gen.types.OracleConfigs;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

// OracleConfigs.max_ages_seconds is a Vec<[u16; 2]>. Until idl-src-gen e02f91c5 the generated record
// read it at two bytes per u16 but wrote and measured it at four, so a decoded value's l() overstated
// what read had consumed by four bytes per element, and every reader stepping by l(), the commons'
// readVector behind StateAccount.read's params and the update_mint_params arguments among them,
// misread or threw past an OracleConfigs entry. The oracle is Borsh: a u32 count, each [u16; 2] as
// four little-endian bytes, then the twelve padding bytes, and the next field right after.
final class GeneratedNestedArrayEncodingTests {

  @Test
  void anEngineFieldAfterOracleConfigsIsReadWhereItWasWritten() {
    final var configs = new OracleConfigs(new int[][]{{7, 9}, {300, 65535}}, new byte[OracleConfigs.PADDING_LEN]);
    final var fields = new EngineField[]{
        new EngineField(EngineFieldName.OracleConfigs, new EngineFieldValue.OracleConfigs(configs)),
        new EngineField(EngineFieldName.DefaultAccountStateFrozen, EngineFieldValue.Boolean.TRUE),
    };
    final var data = new byte[SerDeUtil.lenVector(4, fields)];
    assertEquals(data.length, SerDeUtil.writeVector(4, fields, data, 0));

    // count 2; name 16 (OracleConfigs), value 15, count 2, 07 00 09 00, 2c 01 ff ff, twelve zero bytes;
    // name 9 (DefaultAccountStateFrozen), value 0 (Boolean), 01
    final var expected = new byte[4 + 26 + 3];
    expected[0] = 2;
    expected[4] = 16;
    expected[5] = 15;
    expected[6] = 2;
    expected[10] = 7;
    expected[12] = 9;
    expected[14] = 0x2c;
    expected[15] = 1;
    expected[16] = (byte) 0xff;
    expected[17] = (byte) 0xff;
    expected[30] = 9;
    expected[32] = 1;
    assertArrayEquals(expected, data);

    final var decoded = SerDeUtil.readVector(4, EngineField.class, EngineField::read, data, 0);
    assertEquals(2, decoded.length);
    assertSame(EngineFieldName.OracleConfigs, decoded[0].name());
    assertArrayEquals(configs.maxAgesSeconds(), ((EngineFieldValue.OracleConfigs) decoded[0].value()).val().maxAgesSeconds());
    assertSame(EngineFieldName.DefaultAccountStateFrozen, decoded[1].name());
    assertEquals(EngineFieldValue.Boolean.TRUE, decoded[1].value());
    assertEquals(data.length, SerDeUtil.lenVector(4, decoded));
  }
}
