package systems.glam.sdk;

import java.math.BigInteger;
import java.util.Objects;

/** Exact financial arithmetic shared by GLAM Mint clients. */
public final class GlamMintMath {

  public static final BigInteger PA_NAV_PRECISION = BigInteger.valueOf(1_000_000_000L);

  private static final BigInteger UINT64_MAX = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
  private static final BigInteger I128_MIN = BigInteger.ONE.shiftLeft(127).negate();
  private static final BigInteger I128_MAX = BigInteger.ONE.shiftLeft(127).subtract(BigInteger.ONE);

  /**
   * Exact Java parity for the protocol's signed-integer {@code calc_pa_nav}.
   * Intermediate multiplication is checked at i128 width and division
   * truncates toward zero, matching Rust.
   */
  public static BigInteger calcPaNav(final BigInteger sharesSupply,
                                     final int sharesDecimals,
                                     final BigInteger aum,
                                     final int aumDecimals) {
    Objects.requireNonNull(sharesSupply, "sharesSupply");
    Objects.requireNonNull(aum, "aum");
    requireUnsigned64(sharesSupply, "sharesSupply");
    requireI128(aum, "aum");
    requireU8(sharesDecimals, "sharesDecimals");
    requireU8(aumDecimals, "aumDecimals");

    if (sharesSupply.signum() == 0) {
      return PA_NAV_PRECISION;
    }
    if (sharesDecimals < aumDecimals) {
      throw new IllegalArgumentException("sharesDecimals must be greater than or equal to aumDecimals");
    }

    final var decimalScale = BigInteger.TEN.pow(sharesDecimals - aumDecimals);
    requireI128(decimalScale, "decimal scale");
    final var scaledAum = checkedMultiply(aum, decimalScale, "scaled AUM");
    final var precisionAdjustedAum = checkedMultiply(
        scaledAum,
        PA_NAV_PRECISION,
        "precision-adjusted AUM"
    );
    final var nav = precisionAdjustedAum.divide(sharesSupply);
    requireI128(nav, "NAV");
    return nav;
  }

  private static BigInteger checkedMultiply(final BigInteger left,
                                            final BigInteger right,
                                            final String label) {
    final var result = left.multiply(right);
    requireI128(result, label);
    return result;
  }

  private static void requireUnsigned64(final BigInteger value, final String label) {
    if (value.signum() < 0 || value.compareTo(UINT64_MAX) > 0) {
      throw new IllegalArgumentException(label + " must fit in u64");
    }
  }

  static void requireI128(final BigInteger value, final String label) {
    if (value.compareTo(I128_MIN) < 0 || value.compareTo(I128_MAX) > 0) {
      throw new IllegalArgumentException(label + " must fit in i128");
    }
  }

  private static void requireU8(final int value, final String label) {
    if (value < 0 || value > 255) {
      throw new IllegalArgumentException(label + " must fit in u8");
    }
  }

  private GlamMintMath() {
  }
}
