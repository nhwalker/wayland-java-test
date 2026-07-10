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

/**
 * A one-shot completion callback. The server destroys the object after firing {@code done};
 * the connection retires its id via {@code wl_display.delete_id} — no client action needed.
 */
@WaylandGenerated(protocol = "wayland", interfaceName = "wl_callback", version = 1)
public final class WlCallback {

  public static final InterfaceDesc INTERFACE = InterfaceDesc.of("wl_callback", 1,
      List.of(),
      List.of(
          MessageDesc.of("done", 1, false,
              ArgDesc.uintArg("callback_data"))));

  public static final ProxyType<WlCallback> TYPE = ProxyType.of(INTERFACE, WlCallback::new);

  private final Proxy proxy;

  WlCallback(Proxy proxy) {
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

  public sealed interface Event permits Event.Done {

    /** The callback fired. */
    record Done(int callbackData) implements Event {}
  }

  /** Installs the event handler for this object, replacing any previous one. */
  public void onEvent(Consumer<? super Event> handler) {
    proxy.setEventHandler((opcode, args) -> handler.accept(decode(opcode, args)));
  }

  private static Event decode(int opcode, Object[] args) {
    return switch (opcode) {
      case 0 -> new Event.Done((Integer) args[0]);
      default -> throw new WaylandException("wl_callback: unknown event opcode " + opcode);
    };
  }
}
