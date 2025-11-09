package systems.glam.sdk.idl.programs.glam.mint.gen.events;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.sava.core.encoding.Jex;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

final class ParseEventTests {

  @Test
  void testEmitCpi() {
    final byte[] data = Jex.decode("e445a52e51cb9a1de859bb5231c87f84e7098f05000000000000000000000000");
    final var pricedProtocolRecord = assertInstanceOf(PricedProtocolRecord.class, GlamMintEvent.readCPI(data));
    Assertions.assertNotNull(pricedProtocolRecord);
    assertEquals(93260263, pricedProtocolRecord.baseAssetAmount().longValue());
  }

  @Test
  void testEmit() {
    final byte[] data = Base64.getDecoder().decode("6Fm7UjHIf4QJKN8XAAAAAAAAAAAAAAAA");
    final var pricedProtocolRecord = assertInstanceOf(PricedProtocolRecord.class, GlamMintEvent.read(data));
    Assertions.assertNotNull(pricedProtocolRecord);
    assertEquals(400500745, pricedProtocolRecord.baseAssetAmount().longValue());
  }
}
