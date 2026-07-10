package io.github.nhwalker.wayland.client;

import io.github.nhwalker.wayland.core.ArgDesc;
import io.github.nhwalker.wayland.core.InterfaceDesc;
import io.github.nhwalker.wayland.core.MessageDesc;
import io.github.nhwalker.wayland.core.Proxy;
import io.github.nhwalker.wayland.core.ProxyType;
import io.github.nhwalker.wayland.core.WaylandGenerated;
import java.util.List;

/** The compositor global: creates surfaces and regions. */
@WaylandGenerated(protocol = "wayland", interfaceName = "wl_compositor", version = 6)
public final class WlCompositor {

  public static final InterfaceDesc INTERFACE = InterfaceDesc.of("wl_compositor", 6,
      List.of(
          MessageDesc.of("create_surface", 1, false,
              ArgDesc.newIdArg("id", () -> WlSurface.INTERFACE)),
          MessageDesc.of("create_region", 1, false,
              ArgDesc.newIdArg("id", () -> WlRegion.INTERFACE))),
      List.of());

  public static final ProxyType<WlCompositor> TYPE =
      ProxyType.of(INTERFACE, WlCompositor::new);

  private final Proxy proxy;

  WlCompositor(Proxy proxy) {
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

  public WlSurface createSurface() {
    return WlSurface.TYPE.wrap(proxy.marshalConstructor(0, WlSurface.INTERFACE));
  }

  public WlRegion createRegion() {
    return WlRegion.TYPE.wrap(proxy.marshalConstructor(1, WlRegion.INTERFACE));
  }
}
