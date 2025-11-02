package systems.glam.sdk.idl.programs.glam.mint.gen.events;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.sava.core.encoding.Jex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

final class ParseEventTests {

  @Test
  void test() {
    final byte[] data = Jex.decode("e445a52e51cb9a1de859bb5231c87f84e7098f05000000000000000000000000");
    final var pricedProtocolRecord = assertInstanceOf(PricedProtocolRecord.class, GlamMintEvent.read(data, 8));
    Assertions.assertNotNull(pricedProtocolRecord);
    assertEquals(93260263, pricedProtocolRecord.baseAssetAmount().longValue());
  }
}
