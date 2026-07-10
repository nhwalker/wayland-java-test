package io.github.nhwalker.wayland.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FixedTest {

  @Test
  void doubleRoundTrip() {
    assertEquals(1.5, Fixed.of(1.5).doubleValue());
    assertEquals(-0.25, Fixed.of(-0.25).doubleValue());
    assertEquals(0.0, Fixed.ZERO.doubleValue());
  }

  @Test
  void doubleRoundsToNearest256th() {
    assertEquals(Fixed.of(1.0 / 256), Fixed.of(0.0040));
  }

  @Test
  void intConversionIsExact() {
    assertEquals(7 << 8, Fixed.of(7).raw());
    assertEquals(7, Fixed.of(7).intValue());
    assertEquals(-7, Fixed.of(-7).intValue());
  }

  @Test
  void intValueTruncatesTowardZero() {
    assertEquals(1, Fixed.of(1.75).intValue());
    assertEquals(-1, Fixed.of(-1.75).intValue());
  }

  @Test
  void outOfRangeRejected() {
    assertThrows(IllegalArgumentException.class, () -> Fixed.of(0x80_0000));
    assertThrows(IllegalArgumentException.class, () -> Fixed.of(-0x80_0001));
    assertThrows(IllegalArgumentException.class, () -> Fixed.of(1e10));
  }

  @Test
  void comparesByValue() {
    assertTrue(Fixed.of(1.25).compareTo(Fixed.of(1.5)) < 0);
    assertEquals(0, Fixed.of(2).compareTo(Fixed.of(2.0)));
  }

  @Test
  void friendlyToString() {
    assertEquals("1.5", Fixed.of(1.5).toString());
  }
}
