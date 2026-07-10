package io.github.nhwalker.wayland.client;

import io.github.nhwalker.wayland.core.ArgDesc;
import io.github.nhwalker.wayland.core.InterfaceDesc;
import io.github.nhwalker.wayland.core.MessageDesc;
import io.github.nhwalker.wayland.core.Proxy;
import io.github.nhwalker.wayland.core.ProxyType;
import io.github.nhwalker.wayland.core.WaylandException;
import io.github.nhwalker.wayland.core.WaylandGenerated;
import io.github.nhwalker.wayland.core.WireEnum;
import io.github.nhwalker.wayland.core.WireEnums;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** An onscreen surface: content is supplied by attaching buffers and committing. */
@WaylandGenerated(protocol = "wayland", interfaceName = "wl_surface", version = 6)
public final class WlSurface implements AutoCloseable {

  public static final InterfaceDesc INTERFACE = InterfaceDesc.of("wl_surface", 6,
      List.of(
          MessageDesc.of("destroy", 1, true),
          MessageDesc.of("attach", 1, false,
              ArgDesc.objectArg("buffer", () -> WlBuffer.INTERFACE).asNullable(),
              ArgDesc.intArg("x"), ArgDesc.intArg("y")),
          MessageDesc.of("damage", 1, false,
              ArgDesc.intArg("x"), ArgDesc.intArg("y"),
              ArgDesc.intArg("width"), ArgDesc.intArg("height")),
          MessageDesc.of("frame", 1, false,
              ArgDesc.newIdArg("callback", () -> WlCallback.INTERFACE)),
          MessageDesc.of("set_opaque_region", 1, false,
              ArgDesc.objectArg("region", () -> WlRegion.INTERFACE).asNullable()),
          MessageDesc.of("set_input_region", 1, false,
              ArgDesc.objectArg("region", () -> WlRegion.INTERFACE).asNullable()),
          MessageDesc.of("commit", 1, false),
          MessageDesc.of("set_buffer_transform", 2, false,
              ArgDesc.intArg("transform")),
          MessageDesc.of("set_buffer_scale", 3, false,
              ArgDesc.intArg("scale")),
          MessageDesc.of("damage_buffer", 4, false,
              ArgDesc.intArg("x"), ArgDesc.intArg("y"),
              ArgDesc.intArg("width"), ArgDesc.intArg("height")),
          MessageDesc.of("offset", 5, false,
              ArgDesc.intArg("x"), ArgDesc.intArg("y"))),
      List.of(
          MessageDesc.of("enter", 1, false,
              ArgDesc.objectArg("output", () -> WlOutput.INTERFACE)),
          MessageDesc.of("leave", 1, false,
              ArgDesc.objectArg("output", () -> WlOutput.INTERFACE)),
          MessageDesc.of("preferred_buffer_scale", 6, false,
              ArgDesc.intArg("factor")),
          MessageDesc.of("preferred_buffer_transform", 6, false,
              ArgDesc.uintArg("transform"))));

  public static final ProxyType<WlSurface> TYPE = ProxyType.of(INTERFACE, WlSurface::new);

  private final Proxy proxy;

  WlSurface(Proxy proxy) {
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

  /** Attaches {@code buffer} as the pending content; {@code null} removes the content. */
  public void attach(WlBuffer buffer, int x, int y) {
    proxy.marshal(1, buffer == null ? null : buffer.proxy(), x, y);
  }

  public void damage(int x, int y, int width, int height) {
    proxy.marshal(2, x, y, width, height);
  }

  /** Requests a frame-done callback for the next commit. */
  public WlCallback frame() {
    return WlCallback.TYPE.wrap(proxy.marshalConstructor(3, WlCallback.INTERFACE));
  }

  /** {@code null} means the whole surface is non-opaque. */
  public void setOpaqueRegion(WlRegion region) {
    proxy.marshal(4, region == null ? null : region.proxy());
  }

  /** {@code null} means the whole surface accepts input. */
  public void setInputRegion(WlRegion region) {
    proxy.marshal(5, region == null ? null : region.proxy());
  }

  public void commit() {
    proxy.marshal(6);
  }

  /** Since protocol version 2. */
  public void setBufferTransform(WlOutput.Transform transform) {
    proxy.marshal(7, transform.value());
  }

  /** Since protocol version 3. */
  public void setBufferScale(int scale) {
    proxy.marshal(8, scale);
  }

  /** Since protocol version 4. */
  public void damageBuffer(int x, int y, int width, int height) {
    proxy.marshal(9, x, y, width, height);
  }

  /** Since protocol version 5. */
  public void offset(int x, int y) {
    proxy.marshal(10, x, y);
  }

  public sealed interface Event permits Event.Enter, Event.Leave, Event.PreferredBufferScale,
      Event.PreferredBufferTransform {

    /** The surface now overlaps {@code output} (expected interface: wl_output). */
    record Enter(Proxy output) implements Event {}

    /** The surface no longer overlaps {@code output} (expected interface: wl_output). */
    record Leave(Proxy output) implements Event {}

    /** Since protocol version 6. */
    record PreferredBufferScale(int factor) implements Event {}

    /** Since protocol version 6. Raw {@link WlOutput.Transform} int. */
    record PreferredBufferTransform(int transform) implements Event {}
  }

  /** Installs the event handler for this object, replacing any previous one. */
  public void onEvent(Consumer<? super Event> handler) {
    proxy.setEventHandler((opcode, args) -> handler.accept(decode(opcode, args)));
  }

  private static Event decode(int opcode, Object[] args) {
    return switch (opcode) {
      case 0 -> new Event.Enter((Proxy) args[0]);
      case 1 -> new Event.Leave((Proxy) args[0]);
      case 2 -> new Event.PreferredBufferScale((Integer) args[0]);
      case 3 -> new Event.PreferredBufferTransform((Integer) args[0]);
      default -> throw new WaylandException("wl_surface: unknown event opcode " + opcode);
    };
  }

  public enum Error implements WireEnum {
    INVALID_SCALE(0),
    INVALID_TRANSFORM(1),
    INVALID_SIZE(2),
    INVALID_OFFSET(3),
    DEFUNCT_ROLE_OBJECT(4);

    private final int value;

    Error(int value) {
      this.value = value;
    }

    @Override
    public int value() {
      return value;
    }

    public static Optional<Error> lookup(int value) {
      return WireEnums.lookup(Error.class, value);
    }
  }
}
