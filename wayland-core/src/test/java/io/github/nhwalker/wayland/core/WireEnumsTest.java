package io.github.nhwalker.wayland.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class WireEnumsTest {

  private enum Flags implements WireEnum {
    A(0x1), B(0x2), C(0x8);

    private final int value;

    Flags(int value) {
      this.value = value;
    }

    @Override
    public int value() {
      return value;
    }
  }

  @Test
  void lookupFindsByWireValue() {
    assertEquals(Flags.B, WireEnums.lookup(Flags.class, 0x2).orElseThrow());
    assertTrue(WireEnums.lookup(Flags.class, 0x4).isEmpty());
  }

  @Test
  void setOfDecodesBitfieldIgnoringUnknownBits() {
    assertEquals(EnumSet.of(Flags.A, Flags.C), WireEnums.setOf(Flags.class, 0x1 | 0x8 | 0x100));
    assertEquals(EnumSet.noneOf(Flags.class), WireEnums.setOf(Flags.class, 0));
  }

  @Test
  void maskEncodesBitfield() {
    assertEquals(0x3, WireEnums.mask(List.of(Flags.A, Flags.B)));
    assertEquals(0, WireEnums.mask(List.of()));
  }
}
