package io.github.nhwalker.wayland.client;

import io.github.nhwalker.wayland.core.InterfaceDesc;
import io.github.nhwalker.wayland.core.MessageDesc;
import io.github.nhwalker.wayland.core.Proxy;
import io.github.nhwalker.wayland.core.ProxyType;
import io.github.nhwalker.wayland.core.WaylandException;
import io.github.nhwalker.wayland.core.WaylandGenerated;
import java.util.List;
import java.util.function.Consumer;

/** Pixel content that can be attached to a {@link WlSurface}. */
@WaylandGenerated(protocol = "wayland", interfaceName = "wl_buffer", version = 1)
public final class WlBuffer implements AutoCloseable {

  public static final InterfaceDesc INTERFACE = InterfaceDesc.of("wl_buffer", 1,
      List.of(
          MessageDesc.of("destroy", 1, true)),
      List.of(
          MessageDesc.of("release", 1, false)));

  public static final ProxyType<WlBuffer> TYPE = ProxyType.of(INTERFACE, WlBuffer::new);

  private final Proxy proxy;

  WlBuffer(Proxy proxy) {
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

  /** Destroys the buffer. The underlying storage (e.g. shm pool memory) is unaffected. */
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

  public sealed interface Event permits Event.Release {

    /** The compositor no longer reads the buffer; the client may reuse its storage. */
    record Release() implements Event {}
  }

  /** Installs the event handler for this object, replacing any previous one. */
  public void onEvent(Consumer<? super Event> handler) {
    proxy.setEventHandler((opcode, args) -> handler.accept(decode(opcode, args)));
  }

  private static Event decode(int opcode, Object[] args) {
    return switch (opcode) {
      case 0 -> new Event.Release();
      default -> throw new WaylandException("wl_buffer: unknown event opcode " + opcode);
    };
  }
}
