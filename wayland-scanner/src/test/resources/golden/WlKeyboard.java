package io.github.nhwalker.wayland.client;

import io.github.nhwalker.unixsocket.Fd;
import io.github.nhwalker.wayland.core.ArgDesc;
import io.github.nhwalker.wayland.core.InterfaceDesc;
import io.github.nhwalker.wayland.core.MessageDesc;
import io.github.nhwalker.wayland.core.Proxy;
import io.github.nhwalker.wayland.core.ProxyType;
import io.github.nhwalker.wayland.core.WaylandException;
import io.github.nhwalker.wayland.core.WaylandGenerated;
import io.github.nhwalker.wayland.core.WireEnum;
import io.github.nhwalker.wayland.core.WireEnums;
import io.github.nhwalker.wayland.core.WlArray;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The wl_keyboard interface represents one or more keyboards
 * associated with a seat.
 *
 * Each wl_keyboard has the following logical state:
 *
 * - an active surface (possibly null),
 * - the keys currently logically down,
 * - the active modifiers,
 * - the active group.
 *
 * By default, the active surface is null, the keys currently logically down
 * are empty, the active modifiers and the active group are 0.
 */
@WaylandGenerated(protocol = "wayland", interfaceName = "wl_keyboard", version = 11)
public final class WlKeyboard {

  public static final InterfaceDesc INTERFACE = InterfaceDesc.of("wl_keyboard", 11,
      List.of(
          MessageDesc.of("release", 3, true)),
      List.of(
          MessageDesc.of("keymap", 1, false,
              ArgDesc.uintArg("format"),
              ArgDesc.fdArg("fd"),
              ArgDesc.uintArg("size")),
          MessageDesc.of("enter", 1, false,
              ArgDesc.uintArg("serial"),
              ArgDesc.objectArg("surface", () -> WlSurface.INTERFACE),
              ArgDesc.arrayArg("keys")),
          MessageDesc.of("leave", 1, false,
              ArgDesc.uintArg("serial"),
              ArgDesc.objectArg("surface", () -> WlSurface.INTERFACE)),
          MessageDesc.of("key", 1, false,
              ArgDesc.uintArg("serial"),
              ArgDesc.uintArg("time"),
              ArgDesc.uintArg("key"),
              ArgDesc.uintArg("state")),
          MessageDesc.of("modifiers", 1, false,
              ArgDesc.uintArg("serial"),
              ArgDesc.uintArg("mods_depressed"),
              ArgDesc.uintArg("mods_latched"),
              ArgDesc.uintArg("mods_locked"),
              ArgDesc.uintArg("group")),
          MessageDesc.of("repeat_info", 4, false,
              ArgDesc.intArg("rate"),
              ArgDesc.intArg("delay"))));

  public static final ProxyType<WlKeyboard> TYPE = ProxyType.of(INTERFACE, WlKeyboard::new);

  private final Proxy proxy;

  WlKeyboard(Proxy proxy) {
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

  /** Since protocol version 3. release the keyboard object */
  public void release() {
    proxy.marshalDestructor(0);
  }

  public sealed interface Event permits Event.Keymap, Event.Enter, Event.Leave, Event.Key,
      Event.Modifiers, Event.RepeatInfo {

    /**
     * This event provides a file descriptor to the client which can be
     * memory-mapped in read-only mode to provide a keyboard mapping
     * description.
     *
     * From version 7 onwards, the fd must be mapped with MAP_PRIVATE by
     * the recipient, as MAP_SHARED may fail.
     */
    record Keymap(int format, Fd fd, int size) implements Event {}

    /**
     * Notification that this seat's keyboard focus is on a certain
     * surface.
     *
     * The compositor must send the wl_keyboard.modifiers event after this
     * event.
     *
     * In the wl_keyboard logical state, this event sets the active surface to
     * the surface argument and the keys currently logically down to the keys
     * in the keys argument. The compositor must not send this event if the
     * wl_keyboard already had an active surface immediately before this event.
     *
     * Clients should not use the list of pressed keys to emulate key-press
     * events. The order of keys in the list is unspecified.
     */
    record Enter(int serial, Proxy surface, WlArray keys) implements Event {}

    /**
     * Notification that this seat's keyboard focus is no longer on
     * a certain surface.
     *
     * The leave notification is sent before the enter notification
     * for the new focus.
     *
     * In the wl_keyboard logical state, this event resets all values to their
     * defaults. The compositor must not send this event if the active surface
     * of the wl_keyboard was not equal to the surface argument immediately
     * before this event.
     */
    record Leave(int serial, Proxy surface) implements Event {}

    /**
     * A key was pressed or released.
     * The time argument is a timestamp with millisecond
     * granularity, with an undefined base.
     *
     * The key is a platform-specific key code that can be interpreted
     * by feeding it to the keyboard mapping (see the keymap event).
     *
     * If this event produces a change in modifiers, then the resulting
     * wl_keyboard.modifiers event must be sent after this event.
     *
     * In the wl_keyboard logical state, this event adds the key to the keys
     * currently logically down (if the state argument is pressed) or removes
     * the key from the keys currently logically down (if the state argument is
     * released). The compositor must not send this event if the wl_keyboard
     * did not have an active surface immediately before this event. The
     * compositor must not send this event if state is pressed (resp. released)
     * and the key was already logically down (resp. was not logically down)
     * immediately before this event.
     *
     * Since version 10, compositors may send key events with the "repeated"
     * key state when a wl_keyboard.repeat_info event with a rate argument of
     * 0 has been received. This allows the compositor to take over the
     * responsibility of key repetition.
     */
    record Key(int serial, int time, int key, int state) implements Event {}

    /**
     * Notifies clients that the modifier and/or group state has
     * changed, and it should update its local state.
     *
     * The compositor may send this event without a surface of the client
     * having keyboard focus, for example to tie modifier information to
     * pointer focus instead. If a modifier event with pressed modifiers is sent
     * without a prior enter event, the client can assume the modifier state is
     * valid until it receives the next wl_keyboard.modifiers event. In order to
     * reset the modifier state again, the compositor can send a
     * wl_keyboard.modifiers event with no pressed modifiers.
     *
     * In the wl_keyboard logical state, this event updates the modifiers and
     * group.
     */
    record Modifiers(int serial, int modsDepressed, int modsLatched, int modsLocked,
        int group) implements Event {}

    /**
     * Since protocol version 4.
     * Informs the client about the keyboard's repeat rate and delay.
     *
     * This event is sent as soon as the wl_keyboard object has been created,
     * and is guaranteed to be received by the client before any key press
     * event.
     *
     * Negative values for either rate or delay are illegal. A rate of zero
     * will disable any repeating (regardless of the value of delay).
     *
     * This event can be sent later on as well with a new value if necessary,
     * so clients should continue listening for the event past the creation
     * of wl_keyboard.
     */
    record RepeatInfo(int rate, int delay) implements Event {}
  }

  /** Installs the event handler for this object, replacing any previous one. */
  public void onEvent(Consumer<? super Event> handler) {
    proxy.setEventHandler((opcode, args) -> handler.accept(decode(opcode, args)));
  }

  private static Event decode(int opcode, Object[] args) {
    return switch (opcode) {
      case 0 -> new Event.Keymap((Integer) args[0], (Fd) args[1], (Integer) args[2]);
      case 1 -> new Event.Enter((Integer) args[0], (Proxy) args[1], (WlArray) args[2]);
      case 2 -> new Event.Leave((Integer) args[0], (Proxy) args[1]);
      case 3 -> new Event.Key((Integer) args[0], (Integer) args[1], (Integer) args[2],
          (Integer) args[3]);
      case 4 -> new Event.Modifiers((Integer) args[0], (Integer) args[1], (Integer) args[2],
          (Integer) args[3], (Integer) args[4]);
      case 5 -> new Event.RepeatInfo((Integer) args[0], (Integer) args[1]);
      default -> throw new WaylandException("wl_keyboard: unknown event opcode " + opcode);
    };
  }

  /**
   * This specifies the format of the keymap provided to the
   * client with the wl_keyboard.keymap event.
   */
  public enum KeymapFormat implements WireEnum {
    NO_KEYMAP(0),
    XKB_V1(1);

    private final int value;

    KeymapFormat(int value) {
      this.value = value;
    }

    @Override
    public int value() {
      return value;
    }

    public static Optional<KeymapFormat> lookup(int value) {
      return WireEnums.lookup(KeymapFormat.class, value);
    }
  }

  /**
   * Describes the physical state of a key that produced the key event.
   *
   * Since version 10, the key can be in a "repeated" pseudo-state which
   * means the same as "pressed", but is used to signal repetition in the
   * key event.
   *
   * The key may only enter the repeated state after entering the pressed
   * state and before entering the released state. This event may be
   * generated multiple times while the key is down.
   */
  public enum KeyState implements WireEnum {
    RELEASED(0),
    PRESSED(1),
    REPEATED(2);

    private final int value;

    KeyState(int value) {
      this.value = value;
    }

    @Override
    public int value() {
      return value;
    }

    public static Optional<KeyState> lookup(int value) {
      return WireEnums.lookup(KeyState.class, value);
    }
  }
}
