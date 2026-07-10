package io.github.nhwalker.wayland.core;

import java.util.function.Function;

/**
 * A generated interface's typed handle: its descriptor plus the constructor that wraps an
 * untyped {@link Proxy} in the generated class. Every generated class exposes a
 * {@code public static final ProxyType<WlX> TYPE} — the contract that makes generic operations
 * like {@code wl_registry.bind} type-safe.
 */
public record ProxyType<T>(InterfaceDesc descriptor, Function<Proxy, T> wrapper) {

  public static <T> ProxyType<T> of(InterfaceDesc descriptor, Function<Proxy, T> wrapper) {
    return new ProxyType<>(descriptor, wrapper);
  }

  public T wrap(Proxy proxy) {
    return wrapper.apply(proxy);
  }
}
