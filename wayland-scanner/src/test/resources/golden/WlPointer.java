package io.github.nhwalker.wayland.client;

import io.github.nhwalker.wayland.core.ArgDesc;
import io.github.nhwalker.wayland.core.Fixed;
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

/**
 * The wl_pointer interface represents one or more input devices,
 * such as mice, which control the pointer location and pointer_focus
 * of a seat.
 *
 * The wl_pointer interface generates motion, enter and leave
 * events for the surfaces that the pointer is located over,
 * and button and axis events for button presses, button releases
 * and scrolling.
 */
@WaylandGenerated(protocol = "wayland", interfaceName = "wl_pointer", version = 11)
public final class WlPointer {

  public static final InterfaceDesc INTERFACE = InterfaceDesc.of("wl_pointer", 11,
      List.of(
          MessageDesc.of("set_cursor", 1, false,
              ArgDesc.uintArg("serial"),
              ArgDesc.objectArg("surface", () -> WlSurface.INTERFACE).asNullable(),
              ArgDesc.intArg("hotspot_x"),
              ArgDesc.intArg("hotspot_y")),
          MessageDesc.of("release", 3, true)),
      List.of(
          MessageDesc.of("enter", 1, false,
              ArgDesc.uintArg("serial"),
              ArgDesc.objectArg("surface", () -> WlSurface.INTERFACE),
              ArgDesc.fixedArg("surface_x"),
              ArgDesc.fixedArg("surface_y")),
          MessageDesc.of("leave", 1, false,
              ArgDesc.uintArg("serial"),
              ArgDesc.objectArg("surface", () -> WlSurface.INTERFACE)),
          MessageDesc.of("motion", 1, false,
              ArgDesc.uintArg("time"),
              ArgDesc.fixedArg("surface_x"),
              ArgDesc.fixedArg("surface_y")),
          MessageDesc.of("button", 1, false,
              ArgDesc.uintArg("serial"),
              ArgDesc.uintArg("time"),
              ArgDesc.uintArg("button"),
              ArgDesc.uintArg("state")),
          MessageDesc.of("axis", 1, false,
              ArgDesc.uintArg("time"),
              ArgDesc.uintArg("axis"),
              ArgDesc.fixedArg("value")),
          MessageDesc.of("frame", 5, false),
          MessageDesc.of("axis_source", 5, false,
              ArgDesc.uintArg("axis_source")),
          MessageDesc.of("axis_stop", 5, false,
              ArgDesc.uintArg("time"),
              ArgDesc.uintArg("axis")),
          MessageDesc.of("axis_discrete", 5, false,
              ArgDesc.uintArg("axis"),
              ArgDesc.intArg("discrete")),
          MessageDesc.of("axis_value120", 8, false,
              ArgDesc.uintArg("axis"),
              ArgDesc.intArg("value120")),
          MessageDesc.of("axis_relative_direction", 9, false,
              ArgDesc.uintArg("axis"),
              ArgDesc.uintArg("direction")),
          MessageDesc.of("warp", 11, false,
              ArgDesc.fixedArg("surface_x"),
              ArgDesc.fixedArg("surface_y"))));

  public static final ProxyType<WlPointer> TYPE = ProxyType.of(INTERFACE, WlPointer::new);

  private final Proxy proxy;

  WlPointer(Proxy proxy) {
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
   * Set the pointer surface, i.e., the surface that contains the
   * pointer image (cursor). This request gives the surface the role
   * of a cursor. If the surface already has another role, it raises
   * a protocol error.
   *
   * The cursor actually changes only if the pointer
   * focus for this device is one of the requesting client's surfaces
   * or the surface parameter is the current pointer surface. If
   * there was a previous surface set with this request it is
   * replaced. If surface is NULL, the pointer image is hidden.
   *
   * The parameters hotspot_x and hotspot_y define the position of
   * the pointer surface relative to the pointer location. Its
   * top-left corner is always at (x, y) - (hotspot_x, hotspot_y),
   * where (x, y) are the coordinates of the pointer location, in
   * surface-local coordinates.
   *
   * On wl_surface.offset requests to the pointer surface, hotspot_x
   * and hotspot_y are decremented by the x and y parameters
   * passed to the request. The offset must be applied by
   * wl_surface.commit as usual.
   *
   * The hotspot can also be updated by passing the currently set
   * pointer surface to this request with new values for hotspot_x
   * and hotspot_y.
   *
   * The input region is ignored for wl_surfaces with the role of
   * a cursor. When the use as a cursor ends, the wl_surface is
   * unmapped.
   *
   * The serial parameter must match the latest wl_pointer.enter
   * serial number sent to the client. Otherwise the request will be
   * ignored.
   */
  public void setCursor(int serial, WlSurface surface, int hotspotX, int hotspotY) {
    proxy.marshal(0, serial, surface == null ? null : surface.proxy(), hotspotX, hotspotY);
  }

  /**
   * Since protocol version 3.
   * Using this request a client can tell the server that it is not going to
   * use the pointer object anymore.
   *
   * This request destroys the pointer proxy object, so clients must not call
   * wl_pointer_destroy() after using this request.
   */
  public void release() {
    proxy.marshalDestructor(1);
  }

  public sealed interface Event permits Event.Enter, Event.Leave, Event.Motion, Event.Button,
      Event.Axis, Event.Frame, Event.AxisSource, Event.AxisStop, Event.AxisDiscrete,
      Event.AxisValue120, Event.AxisRelativeDirection, Event.Warp {

    /**
     * Notification that this seat's pointer is focused on a certain
     * surface.
     *
     * When a seat's focus enters a surface, the pointer image
     * is undefined and a client should respond to this event by setting
     * an appropriate pointer image with the set_cursor request.
     */
    record Enter(int serial, Proxy surface, Fixed surfaceX, Fixed surfaceY) implements Event {}

    /**
     * Notification that this seat's pointer is no longer focused on
     * a certain surface.
     *
     * The leave notification is sent before the enter notification
     * for the new focus.
     */
    record Leave(int serial, Proxy surface) implements Event {}

    /**
     * Notification of pointer location change. The arguments
     * surface_x and surface_y are the location relative to the
     * focused surface.
     */
    record Motion(int time, Fixed surfaceX, Fixed surfaceY) implements Event {}

    /**
     * Mouse button click and release notifications.
     *
     * The location of the click is given by the last motion, warp or
     * enter event.
     * The time argument is a timestamp with millisecond
     * granularity, with an undefined base.
     *
     * The button is a button code as defined in the Linux kernel's
     * linux/input-event-codes.h header file, e.g. BTN_LEFT.
     *
     * Any 16-bit button code value is reserved for future additions to the
     * kernel's event code list. All other button codes above 0xFFFF are
     * currently undefined but may be used in future versions of this
     * protocol.
     */
    record Button(int serial, int time, int button, int state) implements Event {}

    /**
     * Scroll and other axis notifications.
     *
     * For scroll events (vertical and horizontal scroll axes), the
     * value parameter is the length of a vector along the specified
     * axis in a coordinate space identical to those of motion events,
     * representing a relative movement along the specified axis.
     *
     * For devices that support movements non-parallel to axes multiple
     * axis events will be emitted.
     *
     * When applicable, for example for touch pads, the server can
     * choose to emit scroll events where the motion vector is
     * equivalent to a motion event vector.
     *
     * When applicable, a client can transform its content relative to the
     * scroll distance.
     */
    record Axis(int time, int axis, Fixed value) implements Event {}

    /**
     * Since protocol version 5.
     * Indicates the end of a set of events that logically belong together.
     * A client is expected to accumulate the data in all events within the
     * frame before proceeding.
     *
     * All wl_pointer events before a wl_pointer.frame event belong
     * logically together. For example, in a diagonal scroll motion the
     * compositor will send an optional wl_pointer.axis_source event, two
     * wl_pointer.axis events (horizontal and vertical) and finally a
     * wl_pointer.frame event. The client may use this information to
     * calculate a diagonal vector for scrolling.
     *
     * When multiple wl_pointer.axis events occur within the same frame,
     * the motion vector is the combined motion of all events.
     * When a wl_pointer.axis and a wl_pointer.axis_stop event occur within
     * the same frame, this indicates that axis movement in one axis has
     * stopped but continues in the other axis.
     * When multiple wl_pointer.axis_stop events occur within the same
     * frame, this indicates that these axes stopped in the same instance.
     *
     * A wl_pointer.frame event is sent for every logical event group,
     * even if the group only contains a single wl_pointer event.
     * Specifically, a client may get a sequence: motion, frame, button,
     * frame, axis, frame, axis_stop, frame.
     *
     * The wl_pointer.enter and wl_pointer.leave events are logical events
     * generated by the compositor and not the hardware. These events are
     * also grouped by a wl_pointer.frame. When a pointer moves from one
     * surface to another, a compositor should group the
     * wl_pointer.leave event within the same wl_pointer.frame.
     * However, a client must not rely on wl_pointer.leave and
     * wl_pointer.enter being in the same wl_pointer.frame.
     * Compositor-specific policies may require the wl_pointer.leave and
     * wl_pointer.enter event being split across multiple wl_pointer.frame
     * groups.
     */
    record Frame() implements Event {}

    /**
     * Since protocol version 5.
     * Source information for scroll and other axes.
     *
     * This event does not occur on its own. It is sent before a
     * wl_pointer.frame event and carries the source information for
     * all events within that frame.
     *
     * The source specifies how this event was generated. If the source is
     * wl_pointer.axis_source.finger, a wl_pointer.axis_stop event will be
     * sent when the user lifts the finger off the device.
     *
     * If the source is wl_pointer.axis_source.wheel,
     * wl_pointer.axis_source.wheel_tilt or
     * wl_pointer.axis_source.continuous, a wl_pointer.axis_stop event may
     * or may not be sent. Whether a compositor sends an axis_stop event
     * for these sources is hardware-specific and implementation-dependent;
     * clients must not rely on receiving an axis_stop event for these
     * scroll sources and should treat scroll sequences from these scroll
     * sources as unterminated by default.
     *
     * This event is optional. If the source is unknown for a particular
     * axis event sequence, no event is sent.
     * Only one wl_pointer.axis_source event is permitted per frame.
     *
     * The order of wl_pointer.axis_discrete and wl_pointer.axis_source is
     * not guaranteed.
     */
    record AxisSource(int axisSource) implements Event {}

    /**
     * Since protocol version 5.
     * Stop notification for scroll and other axes.
     *
     * For some wl_pointer.axis_source types, a wl_pointer.axis_stop event
     * is sent to notify a client that the axis sequence has terminated.
     * This enables the client to implement kinetic scrolling.
     * See the wl_pointer.axis_source documentation for information on when
     * this event may be generated.
     *
     * Any wl_pointer.axis events with the same axis_source after this
     * event should be considered as the start of a new axis motion.
     *
     * The timestamp is to be interpreted identical to the timestamp in the
     * wl_pointer.axis event. The timestamp value may be the same as a
     * preceding wl_pointer.axis event.
     */
    record AxisStop(int time, int axis) implements Event {}

    /**
     * Since protocol version 5.
     * Discrete step information for scroll and other axes.
     *
     * This event carries the axis value of the wl_pointer.axis event in
     * discrete steps (e.g. mouse wheel clicks).
     *
     * This event is deprecated with wl_pointer version 8 - this event is not
     * sent to clients supporting version 8 or later.
     *
     * This event does not occur on its own, it is coupled with a
     * wl_pointer.axis event that represents this axis value on a
     * continuous scale. The protocol guarantees that each axis_discrete
     * event is always followed by exactly one axis event with the same
     * axis number within the same wl_pointer.frame. Note that the protocol
     * allows for other events to occur between the axis_discrete and
     * its coupled axis event, including other axis_discrete or axis
     * events. A wl_pointer.frame must not contain more than one axis_discrete
     * event per axis type.
     *
     * This event is optional; continuous scrolling devices
     * like two-finger scrolling on touchpads do not have discrete
     * steps and do not generate this event.
     *
     * The discrete value carries the directional information. e.g. a value
     * of -2 is two steps towards the negative direction of this axis.
     *
     * The axis number is identical to the axis number in the associated
     * axis event.
     *
     * The order of wl_pointer.axis_discrete and wl_pointer.axis_source is
     * not guaranteed.
     */
    record AxisDiscrete(int axis, int discrete) implements Event {}

    /**
     * Since protocol version 8.
     * Discrete high-resolution scroll information.
     *
     * This event carries high-resolution wheel scroll information,
     * with each multiple of 120 representing one logical scroll step
     * (a wheel detent). For example, an axis_value120 of 30 is one quarter of
     * a logical scroll step in the positive direction, a value120 of
     * -240 are two logical scroll steps in the negative direction within the
     * same hardware event.
     * Clients that rely on discrete scrolling should accumulate the
     * value120 to multiples of 120 before processing the event.
     *
     * The value120 must not be zero.
     *
     * This event replaces the wl_pointer.axis_discrete event in clients
     * supporting wl_pointer version 8 or later.
     *
     * Where a wl_pointer.axis_source event occurs in the same
     * wl_pointer.frame, the axis source applies to this event.
     *
     * The order of wl_pointer.axis_value120 and wl_pointer.axis_source is
     * not guaranteed.
     */
    record AxisValue120(int axis, int value120) implements Event {}

    /**
     * Since protocol version 9.
     * Relative directional information of the entity causing the axis
     * motion.
     *
     * For a wl_pointer.axis event, the wl_pointer.axis_relative_direction
     * event specifies the movement direction of the entity causing the
     * wl_pointer.axis event. For example:
     * - if a user's fingers on a touchpad move down and this
     * causes a wl_pointer.axis vertical_scroll down event, the physical
     * direction is 'identical'
     * - if a user's fingers on a touchpad move down and this causes a
     * wl_pointer.axis vertical_scroll up scroll up event ('natural
     * scrolling'), the physical direction is 'inverted'.
     *
     * A client may use this information to adjust scroll motion of
     * components. Specifically, enabling natural scrolling causes the
     * content to change direction compared to traditional scrolling.
     * Some widgets like volume control sliders should usually match the
     * physical direction regardless of whether natural scrolling is
     * active. This event enables clients to match the scroll direction of
     * a widget to the physical direction.
     *
     * This event does not occur on its own, it is coupled with a
     * wl_pointer.axis event that represents this axis value.
     * The protocol guarantees that each axis_relative_direction event is
     * always followed by exactly one axis event with the same
     * axis number within the same wl_pointer.frame. Note that the protocol
     * allows for other events to occur between the axis_relative_direction
     * and its coupled axis event.
     *
     * The axis number is identical to the axis number in the associated
     * axis event.
     *
     * The order of wl_pointer.axis_relative_direction,
     * wl_pointer.axis_discrete and wl_pointer.axis_source is not
     * guaranteed.
     */
    record AxisRelativeDirection(int axis, int direction) implements Event {}

    /**
     * Since protocol version 11.
     * Notification of pointer location change within a surface.
     *
     * This location change is not due to events on the input device,
     * but because either the surface under the pointer was moved and
     * thus the relative position of the pointer changed, or because
     * the compositor changed the pointer position in response to an
     * event like pointer confinement being exited.
     *
     * The arguments surface_x and surface_y are the location relative to
     * the focused surface.
     *
     * This event must not occur in the same wl_pointer.frame as a
     * wl_pointer.enter or wl_pointer.motion event.
     */
    record Warp(Fixed surfaceX, Fixed surfaceY) implements Event {}
  }

  /** Installs the event handler for this object, replacing any previous one. */
  public void onEvent(Consumer<? super Event> handler) {
    proxy.setEventHandler((opcode, args) -> handler.accept(decode(opcode, args)));
  }

  private static Event decode(int opcode, Object[] args) {
    return switch (opcode) {
      case 0 -> new Event.Enter((Integer) args[0], (Proxy) args[1], (Fixed) args[2],
          (Fixed) args[3]);
      case 1 -> new Event.Leave((Integer) args[0], (Proxy) args[1]);
      case 2 -> new Event.Motion((Integer) args[0], (Fixed) args[1], (Fixed) args[2]);
      case 3 -> new Event.Button((Integer) args[0], (Integer) args[1], (Integer) args[2],
          (Integer) args[3]);
      case 4 -> new Event.Axis((Integer) args[0], (Integer) args[1], (Fixed) args[2]);
      case 5 -> new Event.Frame();
      case 6 -> new Event.AxisSource((Integer) args[0]);
      case 7 -> new Event.AxisStop((Integer) args[0], (Integer) args[1]);
      case 8 -> new Event.AxisDiscrete((Integer) args[0], (Integer) args[1]);
      case 9 -> new Event.AxisValue120((Integer) args[0], (Integer) args[1]);
      case 10 -> new Event.AxisRelativeDirection((Integer) args[0], (Integer) args[1]);
      case 11 -> new Event.Warp((Fixed) args[0], (Fixed) args[1]);
      default -> throw new WaylandException("wl_pointer: unknown event opcode " + opcode);
    };
  }

  public enum Error implements WireEnum {
    ROLE(0);

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

  /**
   * Describes the physical state of a button that produced the button
   * event.
   */
  public enum ButtonState implements WireEnum {
    RELEASED(0),
    PRESSED(1);

    private final int value;

    ButtonState(int value) {
      this.value = value;
    }

    @Override
    public int value() {
      return value;
    }

    public static Optional<ButtonState> lookup(int value) {
      return WireEnums.lookup(ButtonState.class, value);
    }
  }

  /** Describes the axis types of scroll events. */
  public enum Axis implements WireEnum {
    VERTICAL_SCROLL(0),
    HORIZONTAL_SCROLL(1);

    private final int value;

    Axis(int value) {
      this.value = value;
    }

    @Override
    public int value() {
      return value;
    }

    public static Optional<Axis> lookup(int value) {
      return WireEnums.lookup(Axis.class, value);
    }
  }

  /**
   * Describes the source types for axis events. This indicates to the
   * client how an axis event was physically generated; a client may
   * adjust the user interface accordingly. For example, scroll events
   * from a "finger" source may be in a smooth coordinate space with
   * kinetic scrolling whereas a "wheel" source may be in discrete steps
   * of a number of lines.
   *
   * The "continuous" axis source is a device generating events in a
   * continuous coordinate space, but using something other than a
   * finger. One example for this source is button-based scrolling where
   * the vertical motion of a device is converted to scroll events while
   * a button is held down.
   *
   * The "wheel tilt" axis source indicates that the actual device is a
   * wheel but the scroll event is not caused by a rotation but a
   * (usually sideways) tilt of the wheel.
   */
  public enum AxisSource implements WireEnum {
    WHEEL(0),
    FINGER(1),
    CONTINUOUS(2),
    WHEEL_TILT(3);

    private final int value;

    AxisSource(int value) {
      this.value = value;
    }

    @Override
    public int value() {
      return value;
    }

    public static Optional<AxisSource> lookup(int value) {
      return WireEnums.lookup(AxisSource.class, value);
    }
  }

  /**
   * This specifies the direction of the physical motion that caused a
   * wl_pointer.axis event, relative to the wl_pointer.axis direction.
   */
  public enum AxisRelativeDirection implements WireEnum {
    IDENTICAL(0),
    INVERTED(1);

    private final int value;

    AxisRelativeDirection(int value) {
      this.value = value;
    }

    @Override
    public int value() {
      return value;
    }

    public static Optional<AxisRelativeDirection> lookup(int value) {
      return WireEnums.lookup(AxisRelativeDirection.class, value);
    }
  }
}
