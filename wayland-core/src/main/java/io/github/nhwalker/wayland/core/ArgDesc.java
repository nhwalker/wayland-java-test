package io.github.nhwalker.wayland.core;

import java.util.function.Supplier;

/**
 * One argument of a request or event.
 *
 * <p>{@code interfaceRef} is a lazy {@link Supplier} because interface tables reference each
 * other, including cyclically (e.g. {@code xdg_toplevel.set_parent} references its own
 * interface) — direct static references would hit class-initialization ordering. Generated
 * tables pass method references like {@code () -> WlSurface.INTERFACE}. A {@link ArgType#NEW_ID}
 * arg with a {@code null} ref is the <em>dynamic</em> new_id ({@code wl_registry.bind}): the
 * wire layer encodes it as the {@code string interface + uint version + new_id} triple.
 *
 * <p>Because {@code Supplier} has reference equality, descriptor comparisons should use
 * {@code name}/{@code type}/{@code nullable}, not whole-record equality.
 */
public record ArgDesc(String name, ArgType type, boolean nullable,
    Supplier<InterfaceDesc> interfaceRef) {

  public static ArgDesc intArg(String name) {
    return new ArgDesc(name, ArgType.INT, false, null);
  }

  public static ArgDesc uintArg(String name) {
    return new ArgDesc(name, ArgType.UINT, false, null);
  }

  public static ArgDesc fixedArg(String name) {
    return new ArgDesc(name, ArgType.FIXED, false, null);
  }

  public static ArgDesc stringArg(String name) {
    return new ArgDesc(name, ArgType.STRING, false, null);
  }

  public static ArgDesc arrayArg(String name) {
    return new ArgDesc(name, ArgType.ARRAY, false, null);
  }

  public static ArgDesc fdArg(String name) {
    return new ArgDesc(name, ArgType.FD, false, null);
  }

  public static ArgDesc objectArg(String name, Supplier<InterfaceDesc> interfaceRef) {
    return new ArgDesc(name, ArgType.OBJECT, false, interfaceRef);
  }

  /** {@code interfaceRef == null} means the dynamic-new_id (registry.bind) shape. */
  public static ArgDesc newIdArg(String name, Supplier<InterfaceDesc> interfaceRef) {
    return new ArgDesc(name, ArgType.NEW_ID, false, interfaceRef);
  }

  /** A copy of this argument marked nullable ({@code allow-null="true"}). */
  public ArgDesc asNullable() {
    return new ArgDesc(name, type, true, interfaceRef);
  }
}
