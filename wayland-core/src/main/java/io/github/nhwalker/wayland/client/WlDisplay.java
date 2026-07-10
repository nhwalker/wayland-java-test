package io.github.nhwalker.wayland.client;

import io.github.nhwalker.wayland.core.ArgDesc;
import io.github.nhwalker.wayland.core.InterfaceDesc;
import io.github.nhwalker.wayland.core.MessageDesc;
import io.github.nhwalker.wayland.core.Proxy;
import io.github.nhwalker.wayland.core.WaylandConnection;
import io.github.nhwalker.wayland.core.WaylandException;
import io.github.nhwalker.wayland.core.WaylandGenerated;
import io.github.nhwalker.wayland.core.WireEnum;
import io.github.nhwalker.wayland.core.WireEnums;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The connection's root object (id 1). The kernel names no interface — this generated stub
 * supplies the descriptor via {@link #from} and installs control wiring that routes
 * {@code error}/{@code delete_id} to the connection's generic
 * {@link WaylandConnection#fatalError}/{@link WaylandConnection#retireId} hooks. wl_display is
 * the one interface the generator special-cases (by its literal name) to emit this wiring.
 */
@WaylandGenerated(protocol = "wayland", interfaceName = "wl_display", version = 1)
public final class WlDisplay {

  public static final InterfaceDesc INTERFACE = InterfaceDesc.of("wl_display", 1,
      List.of(
          MessageDesc.of("sync", 1, false,
              ArgDesc.newIdArg("callback", () -> WlCallback.INTERFACE)),
          MessageDesc.of("get_registry", 1, false,
              ArgDesc.newIdArg("registry", () -> WlRegistry.INTERFACE))),
      List.of(
          MessageDesc.of("error", 1, false,
              ArgDesc.objectArg("object_id", null),
              ArgDesc.uintArg("code"),
              ArgDesc.stringArg("message")),
          MessageDesc.of("delete_id", 1, false,
              ArgDesc.uintArg("id"))));

  // No ProxyType: wl_display is never bound or created by another object; use from(...).

  /** Connects using the standard environment resolution and wraps the display. */
  public static WlDisplay connect() throws IOException {
    return from(WaylandConnection.open());
  }

  /** Connects to an explicit socket path and wraps the display. */
  public static WlDisplay connect(Path socket) throws IOException {
    return from(WaylandConnection.open(socket));
  }

  /** Wraps object id 1 of {@code connection} and installs the control wiring. */
  public static WlDisplay from(WaylandConnection connection) {
    return new WlDisplay(connection.display(INTERFACE));
  }

  private final Proxy proxy;
  private volatile Consumer<? super Event> handler = event -> {};

  private WlDisplay(Proxy proxy) {
    this.proxy = proxy;
    proxy.setEventHandler(this::control);
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

  public WaylandConnection connection() {
    return proxy.connection();
  }

  private void control(int opcode, Object[] args) {
    Event event = decode(opcode, args);
    switch (event) {
      case Event.Error e -> proxy.connection().fatalError(e.object(), e.code(), e.message());
      case Event.DeleteId d -> proxy.connection().retireId(d.id());
    }
    handler.accept(event);
  }

  /** Requests a sync callback: its {@code done} fires once all prior requests are processed. */
  public WlCallback sync() {
    return WlCallback.TYPE.wrap(proxy.marshalConstructor(0, WlCallback.INTERFACE));
  }

  public WlRegistry getRegistry() {
    return WlRegistry.TYPE.wrap(proxy.marshalConstructor(1, WlRegistry.INTERFACE));
  }

  /** Replaces the user-visible handler; the control wiring is never displaced. */
  public void onEvent(Consumer<? super Event> handler) {
    this.handler = handler;
  }

  public sealed interface Event permits Event.Error, Event.DeleteId {

    /** Fatal server error; {@code object} may be null if the id is no longer live. */
    record Error(Proxy object, int code, String message) implements Event {}

    /** The server confirmed an object id is dead and reusable. */
    record DeleteId(int id) implements Event {}
  }

  private static Event decode(int opcode, Object[] args) {
    return switch (opcode) {
      case 0 -> new Event.Error((Proxy) args[0], (Integer) args[1], (String) args[2]);
      case 1 -> new Event.DeleteId((Integer) args[0]);
      default -> throw new WaylandException("wl_display: unknown event opcode " + opcode);
    };
  }

  public enum Error implements WireEnum {
    INVALID_OBJECT(0),
    INVALID_METHOD(1),
    NO_MEMORY(2),
    IMPLEMENTATION(3);

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
