package io.github.nhwalker.wayland.client;

import io.github.nhwalker.wayland.core.ArgDesc;
import io.github.nhwalker.wayland.core.InterfaceDesc;
import io.github.nhwalker.wayland.core.MessageDesc;
import io.github.nhwalker.wayland.core.Proxy;
import io.github.nhwalker.wayland.core.ProxyType;
import io.github.nhwalker.wayland.core.WaylandException;
import io.github.nhwalker.wayland.core.WaylandGenerated;
import java.util.List;
import java.util.function.Consumer;

/** The registry singleton: advertises and binds the compositor's globals. */
@WaylandGenerated(protocol = "wayland", interfaceName = "wl_registry", version = 1)
public final class WlRegistry {

  public static final InterfaceDesc INTERFACE = InterfaceDesc.of("wl_registry", 1,
      List.of(
          MessageDesc.of("bind", 1, false,
              ArgDesc.uintArg("name"),
              ArgDesc.newIdArg("id", null))),
      List.of(
          MessageDesc.of("global", 1, false,
              ArgDesc.uintArg("name"),
              ArgDesc.stringArg("interface"),
              ArgDesc.uintArg("version")),
          MessageDesc.of("global_remove", 1, false,
              ArgDesc.uintArg("name"))));

  public static final ProxyType<WlRegistry> TYPE = ProxyType.of(INTERFACE, WlRegistry::new);

  private final Proxy proxy;

  WlRegistry(Proxy proxy) {
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

  /** Binds global {@code name} as {@code type} at {@code version}, returning the typed proxy. */
  public <T> T bind(int name, ProxyType<T> type, int version) {
    return type.wrap(proxy.marshalConstructor(0, version, type.descriptor(), name));
  }

  public sealed interface Event permits Event.Global, Event.GlobalRemove {

    /** A global became available. */
    record Global(int name, String interfaceName, int version) implements Event {}

    /** A global was removed; requests to objects bound from it will be ignored. */
    record GlobalRemove(int name) implements Event {}
  }

  /** Installs the event handler for this object, replacing any previous one. */
  public void onEvent(Consumer<? super Event> handler) {
    proxy.setEventHandler((opcode, args) -> handler.accept(decode(opcode, args)));
  }

  private static Event decode(int opcode, Object[] args) {
    return switch (opcode) {
      case 0 -> new Event.Global((Integer) args[0], (String) args[1], (Integer) args[2]);
      case 1 -> new Event.GlobalRemove((Integer) args[0]);
      default -> throw new WaylandException("wl_registry: unknown event opcode " + opcode);
    };
  }
}
