package io.github.nhwalker.wayland.scanner;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** One protocol definition inside a {@link WaylandProtocols} declaration. */
@Target({})
@Retention(RetentionPolicy.SOURCE)
public @interface WaylandProtocol {

  /**
   * The protocol source: {@code "builtin:NAME"} for a definition bundled with the scanner
   * (e.g. {@code "builtin:wayland"}), or a file name resolved against the
   * {@code -Awayland.protocolDir=DIR} compiler option.
   */
  String value();

  /**
   * Cross-protocol references, as {@code "PKG=SPEC"} entries: the interfaces of {@code SPEC}
   * (same {@code builtin:}/file forms as {@link #value()}) were already generated into
   * {@code PKG} and are imported where referenced.
   */
  String[] imports() default {};
}
