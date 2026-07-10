package io.github.nhwalker.wayland.core;

import java.util.EnumSet;
import java.util.Optional;

/** Lookup and bitfield helpers for generated {@link WireEnum} types. */
public final class WireEnums {

  private WireEnums() {}

  /** The constant of {@code type} with the given wire value, or empty if unknown. */
  public static <E extends Enum<E> & WireEnum> Optional<E> lookup(Class<E> type, int value) {
    for (E constant : type.getEnumConstants()) {
      if (constant.value() == value) {
        return Optional.of(constant);
      }
    }
    return Optional.empty();
  }

  /** Decodes a bitfield: every constant whose bits are present. Unknown bits are ignored. */
  public static <E extends Enum<E> & WireEnum> EnumSet<E> setOf(Class<E> type, int bits) {
    EnumSet<E> set = EnumSet.noneOf(type);
    for (E constant : type.getEnumConstants()) {
      if (constant.value() != 0 && (bits & constant.value()) == constant.value()) {
        set.add(constant);
      }
    }
    return set;
  }

  /** Encodes a bitfield: the OR of all values. */
  public static int mask(Iterable<? extends WireEnum> values) {
    int bits = 0;
    for (WireEnum value : values) {
      bits |= value.value();
    }
    return bits;
  }
}
