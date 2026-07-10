package io.github.nhwalker.wayland.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * The wire {@code array} primitive: an opaque byte blob whose interpretation is
 * message-specific. Value semantics — content equality, defensive copies in and out.
 */
public record WlArray(byte[] bytes) {

  public WlArray {
    bytes = bytes.clone();
  }

  public static WlArray of(byte[] bytes) {
    return new WlArray(bytes);
  }

  /** A defensive copy of the content. */
  @Override
  public byte[] bytes() {
    return bytes.clone();
  }

  /** The content as little-endian 32-bit integers — the common uint-array payload. */
  public int[] asIntArray() {
    int[] ints = new int[bytes.length / 4];
    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(ints);
    return ints;
  }

  public int byteSize() {
    return bytes.length;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof WlArray other && Arrays.equals(bytes, other.bytes);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(bytes);
  }

  @Override
  public String toString() {
    return "WlArray[" + bytes.length + " bytes]";
  }
}
