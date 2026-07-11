package systems.glam.sdk;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class GlamMintMathTest {

  @Test
  void calculatesTheProtocolVectorsExactly() {
    assertEquals(
        GlamMintMath.PA_NAV_PRECISION,
        GlamMintMath.calcPaNav(
            BigInteger.valueOf(10_000_000_000L),
            9,
            BigInteger.valueOf(10_000_000_000L),
            9
        )
    );
    assertEquals(
        BigInteger.valueOf(2_000_000_000L),
        GlamMintMath.calcPaNav(
            BigInteger.valueOf(10_000_000_000L),
            9,
            BigInteger.valueOf(20_000_000_000L),
            9
        )
    );
    assertEquals(
        BigInteger.valueOf(500_000_000L),
        GlamMintMath.calcPaNav(
            BigInteger.valueOf(10_000_000_000L),
            9,
            BigInteger.valueOf(5_000_000_000L),
            9
        )
    );
    assertEquals(
        GlamMintMath.PA_NAV_PRECISION,
        GlamMintMath.calcPaNav(
            BigInteger.valueOf(10_000_000_000L),
            9,
            BigInteger.valueOf(10_000_000L),
            6
        )
    );
  }

  @Test
  void zeroSupplyUsesTheProtocolSeedBeforeDecimalScaling() {
    assertEquals(
        GlamMintMath.PA_NAV_PRECISION,
        GlamMintMath.calcPaNav(BigInteger.ZERO, 0, BigInteger.ONE, 9)
    );
  }

  @Test
  void signedDivisionTruncatesTowardZeroLikeRust() {
    assertEquals(
        BigInteger.valueOf(-3_333_333_333L),
        GlamMintMath.calcPaNav(BigInteger.valueOf(3), 0, BigInteger.valueOf(-10), 0)
    );
  }

  @Test
  void failsClosedOnProtocolWidthAndDecimalErrors() {
    assertThrows(
        IllegalArgumentException.class,
        () -> GlamMintMath.calcPaNav(BigInteger.ONE.shiftLeft(64), 9, BigInteger.ONE, 9)
    );
    assertThrows(
        IllegalArgumentException.class,
        () -> GlamMintMath.calcPaNav(BigInteger.ONE, 6, BigInteger.ONE, 9)
    );
    assertThrows(
        IllegalArgumentException.class,
        () -> GlamMintMath.calcPaNav(
            BigInteger.ONE,
            1,
            BigInteger.ONE.shiftLeft(127).subtract(BigInteger.ONE),
            0
        )
    );
  }
}
