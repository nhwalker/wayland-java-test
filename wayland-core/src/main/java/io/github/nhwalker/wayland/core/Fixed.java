package io.github.nhwalker.wayland.core;

/**
 * A Wayland 24.8 signed fixed-point number: 24 integer bits, 8 fractional bits, carried on the
 * wire as one 32-bit word ({@code raw}).
 */
public record Fixed(int raw) implements Comparable<Fixed> {

  public static final Fixed ZERO = new Fixed(0);

  /** Rounds {@code value} to the nearest 1/256. */
  public static Fixed of(double value) {
    long raw = Math.round(value * 256.0);
    if (raw < Integer.MIN_VALUE || raw > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("value out of 24.8 fixed-point range: " + value);
    }
    return new Fixed((int) raw);
  }

  /** Exact conversion; {@code value} must fit in 24 signed bits. */
  public static Fixed of(int value) {
    if (value < -0x80_0000 || value > 0x7F_FFFF) {
      throw new IllegalArgumentException("value out of 24.8 fixed-point range: " + value);
    }
    return new Fixed(value << 8);
  }

  public double doubleValue() {
    return raw / 256.0;
  }

  /** The integer part, truncated toward zero. */
  public int intValue() {
    return raw / 256;
  }

  @Override
  public int compareTo(Fixed other) {
    return Integer.compare(raw, other.raw);
  }

  @Override
  public String toString() {
    return Double.toString(doubleValue());
  }
}
