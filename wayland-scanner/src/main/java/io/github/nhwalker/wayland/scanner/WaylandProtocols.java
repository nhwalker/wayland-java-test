package io.github.nhwalker.wayland.scanner;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the Wayland protocols whose stubs should be generated into the annotated package at
 * compile time — the annotation-processor counterpart of the {@code wayland-scanner} CLI.
 *
 * <p>Place on a {@code package-info.java} and add wayland-scanner to the compiler's annotation
 * processor path:
 *
 * <pre>
 * &#64;WaylandProtocols({
 *     &#64;WaylandProtocol("builtin:wayland"),
 *     &#64;WaylandProtocol(value = "xdg-shell.xml",
 *         imports = "com.example.core=builtin:wayland")})
 * package com.example.protocols;
 * </pre>
 *
 * <p>All protocols listed in one annotation are generated into the same package and may
 * reference each other's interfaces freely.
 */
@Target(ElementType.PACKAGE)
@Retention(RetentionPolicy.SOURCE)
public @interface WaylandProtocols {

  WaylandProtocol[] value();
}
