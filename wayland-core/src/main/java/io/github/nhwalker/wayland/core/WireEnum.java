package io.github.nhwalker.wayland.core;

/** Implemented by every generated protocol enum: the constant's wire value. */
public interface WireEnum {

  /** The value this constant has on the wire. */
  int value();
}
