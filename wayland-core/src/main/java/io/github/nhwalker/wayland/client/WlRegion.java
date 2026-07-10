package io.github.nhwalker.wayland.client;

import io.github.nhwalker.wayland.core.ArgDesc;
import io.github.nhwalker.wayland.core.InterfaceDesc;
import io.github.nhwalker.wayland.core.MessageDesc;
import io.github.nhwalker.wayland.core.Proxy;
import io.github.nhwalker.wayland.core.ProxyType;
import io.github.nhwalker.wayland.core.WaylandGenerated;
import java.util.List;

/** A rectangle set, used for surface input and opaque regions. */
@WaylandGenerated(protocol = "wayland", interfaceName = "wl_region", version = 1)
public final class WlRegion implements AutoCloseable {

  public static final InterfaceDesc INTERFACE = InterfaceDesc.of("wl_region", 1,
      List.of(
          MessageDesc.of("destroy", 1, true),
          MessageDesc.of("add", 1, false,
              ArgDesc.intArg("x"), ArgDesc.intArg("y"),
              ArgDesc.intArg("width"), ArgDesc.intArg("height")),
          MessageDesc.of("subtract", 1, false,
              ArgDesc.intArg("x"), ArgDesc.intArg("y"),
              ArgDesc.intArg("width"), ArgDesc.intArg("height"))),
      List.of());

  public static final ProxyType<WlRegion> TYPE = ProxyType.of(INTERFACE, WlRegion::new);

  private final Proxy proxy;

  WlRegion(Proxy proxy) {
    this.proxy = proxy;
  }

  public Proxy proxy() {
    return proxy;
  }

  public int id() {
    return proxy.id();
  }

  public int version() {
    return proxy.version();
  }

  public boolean isDestroyed() {
    return proxy.isDestroyed();
  }

  public void destroy() {
    proxy.marshalDestructor(0);
  }

  /** {@link #destroy()}; no-op if already destroyed. */
  @Override
  public void close() {
    if (!proxy.isDestroyed()) {
      destroy();
    }
  }

  public void add(int x, int y, int width, int height) {
    proxy.marshal(1, x, y, width, height);
  }

  public void subtract(int x, int y, int width, int height) {
    proxy.marshal(2, x, y, width, height);
  }
}
