package io.github.nhwalker.wayland.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type as generated from a Wayland protocol definition. {@code CLASS} retention so
 * tooling can identify stubs in jars without runtime cost.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface WaylandGenerated {

  /** The protocol name from the XML, e.g. {@code "wayland"}. */
  String protocol();

  /** The wire interface name, e.g. {@code "wl_registry"}. */
  String interfaceName();

  /** The interface version the stub was generated from. */
  int version();
}
