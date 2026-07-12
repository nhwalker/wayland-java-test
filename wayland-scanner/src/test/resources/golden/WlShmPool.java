package io.github.nhwalker.wayland.client;

import io.github.nhwalker.wayland.core.ArgDesc;
import io.github.nhwalker.wayland.core.InterfaceDesc;
import io.github.nhwalker.wayland.core.MessageDesc;
import io.github.nhwalker.wayland.core.Proxy;
import io.github.nhwalker.wayland.core.ProxyType;
import io.github.nhwalker.wayland.core.WaylandGenerated;
import java.util.List;

/** A shared-memory pool from which {@link WlBuffer}s are carved. */
@WaylandGenerated(protocol = "wayland", interfaceName = "wl_shm_pool", version = 1)
public final class WlShmPool implements AutoCloseable {

  public static final InterfaceDesc INTERFACE = InterfaceDesc.of("wl_shm_pool", 1,
      List.of(
          MessageDesc.of("create_buffer", 1, false,
              ArgDesc.newIdArg("id", () -> WlBuffer.INTERFACE),
              ArgDesc.intArg("offset"),
              ArgDesc.intArg("width"),
              ArgDesc.intArg("height"),
              ArgDesc.intArg("stride"),
              ArgDesc.uintArg("format")),
          MessageDesc.of("destroy", 1, true),
          MessageDesc.of("resize", 1, false,
              ArgDesc.intArg("size"))),
      List.of());

  public static final ProxyType<WlShmPool> TYPE = ProxyType.of(INTERFACE, WlShmPool::new);

  private final Proxy proxy;

  WlShmPool(Proxy proxy) {
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

  /** Creates a buffer over {@code [offset, offset + height * stride)} of the pool. */
  public WlBuffer createBuffer(int offset, int width, int height, int stride, WlShm.Format format) {
    return WlBuffer.TYPE.wrap(proxy.marshalConstructor(0, WlBuffer.INTERFACE, offset, width, height,
        stride, format.value()));
  }

  /** Destroys the pool; buffers created from it keep their storage alive. */
  public void destroy() {
    proxy.marshalDestructor(1);
  }

  /** {@link #destroy()}; no-op if already destroyed. */
  @Override
  public void close() {
    if (!proxy.isDestroyed()) {
      destroy();
    }
  }

  /** Grows the pool (never shrinks) after the backing fd was ftruncated larger. */
  public void resize(int size) {
    proxy.marshal(2, size);
  }
}
