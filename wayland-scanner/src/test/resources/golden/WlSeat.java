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
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A seat is a group of keyboards, pointer and touch devices. This
 * object is published as a global during start up, or when such a
 * device is hot plugged.  A seat typically has a pointer and
 * maintains a keyboard focus and a pointer focus.
 */
@WaylandGenerated(protocol = "wayland", interfaceName = "wl_seat", version = 11)
public final class WlSeat {

  public static final InterfaceDesc INTERFACE = InterfaceDesc.of("wl_seat", 11,
      List.of(
          MessageDesc.of("get_pointer", 1, false,
              ArgDesc.newIdArg("id", () -> WlPointer.INTERFACE)),
          MessageDesc.of("get_keyboard", 1, false,
              ArgDesc.newIdArg("id", () -> WlKeyboard.INTERFACE)),
          MessageDesc.of("get_touch", 1, false,
              ArgDesc.newIdArg("id", () -> WlTouch.INTERFACE)),
          MessageDesc.of("release", 5, true)),
      List.of(
          MessageDesc.of("capabilities", 1, false,
              ArgDesc.uintArg("capabilities")),
          MessageDesc.of("name", 2, false,
              ArgDesc.stringArg("name"))));

  public static final ProxyType<WlSeat> TYPE = ProxyType.of(INTERFACE, WlSeat::new);

  private final Proxy proxy;

  WlSeat(Proxy proxy) {
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

  /**
   * The ID provided will be initialized to the wl_pointer interface
   * for this seat.
   *
   * This request only takes effect if the seat has the pointer
   * capability, or has had the pointer capability in the past.
   * It is a protocol violation to issue this request on a seat that has
   * never had the pointer capability. The missing_capability error will
   * be sent in this case.
   */
  public WlPointer getPointer() {
    return WlPointer.TYPE.wrap(proxy.marshalConstructor(0, WlPointer.INTERFACE));
  }

  /**
   * The ID provided will be initialized to the wl_keyboard interface
   * for this seat.
   *
   * This request only takes effect if the seat has the keyboard
   * capability, or has had the keyboard capability in the past.
   * It is a protocol violation to issue this request on a seat that has
   * never had the keyboard capability. The missing_capability error will
   * be sent in this case.
   */
  public WlKeyboard getKeyboard() {
    return WlKeyboard.TYPE.wrap(proxy.marshalConstructor(1, WlKeyboard.INTERFACE));
  }

  /**
   * The ID provided will be initialized to the wl_touch interface
   * for this seat.
   *
   * This request only takes effect if the seat has the touch
   * capability, or has had the touch capability in the past.
   * It is a protocol violation to issue this request on a seat that has
   * never had the touch capability. The missing_capability error will
   * be sent in this case.
   */
  public WlTouch getTouch() {
    return WlTouch.TYPE.wrap(proxy.marshalConstructor(2, WlTouch.INTERFACE));
  }

  /**
   * Since protocol version 5.
   * Using this request a client can tell the server that it is not going to
   * use the seat object anymore.
   */
  public void release() {
    proxy.marshalDestructor(3);
  }

  public sealed interface Event permits Event.Capabilities, Event.Name {

    /**
     * This is sent on binding to the seat global or whenever a seat gains
     * or loses the pointer, keyboard or touch capabilities.
     * The argument is a capability enum containing the complete set of
     * capabilities this seat has.
     *
     * When the pointer capability is added, a client may create a
     * wl_pointer object using the wl_seat.get_pointer request. This object
     * will receive pointer events until the capability is removed in the
     * future.
     *
     * When the pointer capability is removed, a client should destroy the
     * wl_pointer objects associated with the seat where the capability was
     * removed, using the wl_pointer.release request. No further pointer
     * events will be received on these objects.
     *
     * In some compositors, if a seat regains the pointer capability and a
     * client has a previously obtained wl_pointer object of version 4 or
     * less, that object may start sending pointer events again. This
     * behavior is considered a misinterpretation of the intended behavior
     * and must not be relied upon by the client. wl_pointer objects of
     * version 5 or later must not send events if created before the most
     * recent event notifying the client of an added pointer capability.
     *
     * The above behavior also applies to wl_keyboard and wl_touch with the
     * keyboard and touch capabilities, respectively.
     */
    record Capabilities(int capabilities) implements Event {}

    /**
     * Since protocol version 2.
     * In a multi-seat configuration the seat name can be used by clients to
     * help identify which physical devices the seat represents.
     *
     * The seat name is a UTF-8 string with no convention defined for its
     * contents. Each name is unique among all wl_seat globals. The name is
     * only guaranteed to be unique for the current compositor instance.
     *
     * The same seat names are used for all clients. Thus, the name can be
     * shared across processes to refer to a specific wl_seat global.
     *
     * The name event is sent after binding to the seat global, and should be sent
     * before announcing capabilities. This event is only sent once per seat object,
     * and the name does not change over the lifetime of the wl_seat global.
     *
     * Compositors may re-use the same seat name if the wl_seat global is
     * destroyed and re-created later.
     */
    record Name(String name) implements Event {}
  }

  /** Installs the event handler for this object, replacing any previous one. */
  public void onEvent(Consumer<? super Event> handler) {
    proxy.setEventHandler((opcode, args) -> handler.accept(decode(opcode, args)));
  }

  private static Event decode(int opcode, Object[] args) {
    return switch (opcode) {
      case 0 -> new Event.Capabilities((Integer) args[0]);
      case 1 -> new Event.Name((String) args[0]);
      default -> throw new WaylandException("wl_seat: unknown event opcode " + opcode);
    };
  }

  /**
   * This is a bitmask of capabilities this seat has; if a member is
   * set, then it is present on the seat.
   */
  public enum Capability implements WireEnum {
    POINTER(1),
    KEYBOARD(2),
    TOUCH(4);

    private final int value;

    Capability(int value) {
      this.value = value;
    }

    @Override
    public int value() {
      return value;
    }

    public static EnumSet<Capability> setOf(int bits) {
      return WireEnums.setOf(Capability.class, bits);
    }
  }

  /** These errors can be emitted in response to wl_seat requests. */
  public enum Error implements WireEnum {
    MISSING_CAPABILITY(0);

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
