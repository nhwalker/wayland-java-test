package io.github.nhwalker.wayland.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class WlArrayTest {

  @Test
  void contentEquality() {
    assertEquals(WlArray.of(new byte[] {1, 2}), WlArray.of(new byte[] {1, 2}));
    assertEquals(WlArray.of(new byte[] {1, 2}).hashCode(),
        WlArray.of(new byte[] {1, 2}).hashCode());
    assertNotEquals(WlArray.of(new byte[] {1, 2}), WlArray.of(new byte[] {2, 1}));
  }

  @Test
  void defensiveCopies() {
    byte[] source = {1, 2, 3};
    WlArray array = WlArray.of(source);
    source[0] = 99;
    assertArrayEquals(new byte[] {1, 2, 3}, array.bytes());

    array.bytes()[0] = 99;
    assertArrayEquals(new byte[] {1, 2, 3}, array.bytes());
  }

  @Test
  void littleEndianIntView() {
    WlArray array = WlArray.of(new byte[] {0x01, 0x00, 0x00, 0x00, (byte) 0xFF, 0x00, 0x00, 0x00});
    assertArrayEquals(new int[] {1, 255}, array.asIntArray());
    assertEquals(8, array.byteSize());
  }
}
