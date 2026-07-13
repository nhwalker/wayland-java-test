package io.github.nhwalker.wayland.client;

import io.github.nhwalker.wayland.core.ArgDesc;
import io.github.nhwalker.wayland.core.Fixed;
import io.github.nhwalker.wayland.core.InterfaceDesc;
import io.github.nhwalker.wayland.core.MessageDesc;
import io.github.nhwalker.wayland.core.Proxy;
import io.github.nhwalker.wayland.core.ProxyType;
import io.github.nhwalker.wayland.core.WaylandException;
import io.github.nhwalker.wayland.core.WaylandGenerated;
import java.util.List;
import java.util.function.Consumer;

/**
 * The wl_touch interface represents a touchscreen
 * associated with a seat.
 *
 * Touch interactions can consist of one or more contacts.
 * For each contact, a series of events is generated, starting
 * with a down event, followed by zero or more motion events,
 * and ending with an up event. Events relating to the same
 * contact point can be identified by the ID of the sequence.
 */
@WaylandGenerated(protocol = "wayland", interfaceName = "wl_touch", version = 11)
public final class WlTouch {

  public static final InterfaceDesc INTERFACE = InterfaceDesc.of("wl_touch", 11,
      List.of(
          MessageDesc.of("release", 3, true)),
      List.of(
          MessageDesc.of("down", 1, false,
              ArgDesc.uintArg("serial"),
              ArgDesc.uintArg("time"),
              ArgDesc.objectArg("surface", () -> WlSurface.INTERFACE),
              ArgDesc.intArg("id"),
              ArgDesc.fixedArg("x"),
              ArgDesc.fixedArg("y")),
          MessageDesc.of("up", 1, false,
              ArgDesc.uintArg("serial"),
              ArgDesc.uintArg("time"),
              ArgDesc.intArg("id")),
          MessageDesc.of("motion", 1, false,
              ArgDesc.uintArg("time"),
              ArgDesc.intArg("id"),
              ArgDesc.fixedArg("x"),
              ArgDesc.fixedArg("y")),
          MessageDesc.of("frame", 1, false),
          MessageDesc.of("cancel", 1, false),
          MessageDesc.of("shape", 6, false,
              ArgDesc.intArg("id"),
              ArgDesc.fixedArg("major"),
              ArgDesc.fixedArg("minor")),
          MessageDesc.of("orientation", 6, false,
              ArgDesc.intArg("id"),
              ArgDesc.fixedArg("orientation"))));

  public static final ProxyType<WlTouch> TYPE = ProxyType.of(INTERFACE, WlTouch::new);

  private final Proxy proxy;

  WlTouch(Proxy proxy) {
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

  /** Since protocol version 3. release the touch object */
  public void release() {
    proxy.marshalDestructor(0);
  }

  public sealed interface Event permits Event.Down, Event.Up, Event.Motion, Event.Frame,
      Event.Cancel, Event.Shape, Event.Orientation {

    /**
     * A new touch point has appeared on the surface. This touch point is
     * assigned a unique ID. Future events from this touch point reference
     * this ID. The ID ceases to be valid after a touch up event and may be
     * reused in the future.
     */
    record Down(int serial, int time, Proxy surface, int id, Fixed x, Fixed y) implements Event {}

    /**
     * The touch point has disappeared. No further events will be sent for
     * this touch point and the touch point's ID is released and may be
     * reused in a future touch down event.
     */
    record Up(int serial, int time, int id) implements Event {}

    /** A touch point has changed coordinates. */
    record Motion(int time, int id, Fixed x, Fixed y) implements Event {}

    /**
     * Indicates the end of a set of events that logically belong together.
     * A client is expected to accumulate the data in all events within the
     * frame before proceeding.
     *
     * A wl_touch.frame terminates at least one event but otherwise no
     * guarantee is provided about the set of events within a frame. A client
     * must assume that any state not updated in a frame is unchanged from the
     * previously known state.
     */
    record Frame() implements Event {}

    /**
     * Sent if the compositor decides the touch stream is a global
     * gesture. No further events are sent to the clients from that
     * particular gesture. Touch cancellation applies to all touch points
     * currently active on this client's surface. The client is
     * responsible for finalizing the touch points, future touch points on
     * this surface may reuse the touch point ID.
     *
     * No frame event is required after the cancel event.
     */
    record Cancel() implements Event {}

    /**
     * Since protocol version 6.
     * Sent when a touchpoint has changed its shape.
     *
     * This event does not occur on its own. It is sent before a
     * wl_touch.frame event and carries the new shape information for
     * any previously reported, or new touch points of that frame.
     *
     * Other events describing the touch point such as wl_touch.down,
     * wl_touch.motion or wl_touch.orientation may be sent within the
     * same wl_touch.frame. A client should treat these events as a single
     * logical touch point update. The order of wl_touch.shape,
     * wl_touch.orientation and wl_touch.motion is not guaranteed.
     * A wl_touch.down event is guaranteed to occur before the first
     * wl_touch.shape event for this touch ID but both events may occur within
     * the same wl_touch.frame.
     *
     * A touchpoint shape is approximated by an ellipse through the major and
     * minor axis length. The major axis length describes the longer diameter
     * of the ellipse, while the minor axis length describes the shorter
     * diameter. Major and minor are orthogonal and both are specified in
     * surface-local coordinates. The center of the ellipse is always at the
     * touchpoint location as reported by wl_touch.down or wl_touch.motion.
     *
     * This event is only sent by the compositor if the touch device supports
     * shape reports. The client has to make reasonable assumptions about the
     * shape if it did not receive this event.
     */
    record Shape(int id, Fixed major, Fixed minor) implements Event {}

    /**
     * Since protocol version 6.
     * Sent when a touchpoint has changed its orientation.
     *
     * This event does not occur on its own. It is sent before a
     * wl_touch.frame event and carries the new shape information for
     * any previously reported, or new touch points of that frame.
     *
     * Other events describing the touch point such as wl_touch.down,
     * wl_touch.motion or wl_touch.shape may be sent within the
     * same wl_touch.frame. A client should treat these events as a single
     * logical touch point update. The order of wl_touch.shape,
     * wl_touch.orientation and wl_touch.motion is not guaranteed.
     * A wl_touch.down event is guaranteed to occur before the first
     * wl_touch.orientation event for this touch ID but both events may occur
     * within the same wl_touch.frame.
     *
     * The orientation describes the clockwise angle of a touchpoint's major
     * axis to the positive surface y-axis and is normalized to the -180 to
     * +180 degree range. The granularity of orientation depends on the touch
     * device, some devices only support binary rotation values between 0 and
     * 90 degrees.
     *
     * This event is only sent by the compositor if the touch device supports
     * orientation reports.
     */
    record Orientation(int id, Fixed orientation) implements Event {}
  }

  /** Installs the event handler for this object, replacing any previous one. */
  public void onEvent(Consumer<? super Event> handler) {
    proxy.setEventHandler((opcode, args) -> handler.accept(decode(opcode, args)));
  }

  private static Event decode(int opcode, Object[] args) {
    return switch (opcode) {
      case 0 -> new Event.Down((Integer) args[0], (Integer) args[1], (Proxy) args[2],
          (Integer) args[3], (Fixed) args[4], (Fixed) args[5]);
      case 1 -> new Event.Up((Integer) args[0], (Integer) args[1], (Integer) args[2]);
      case 2 -> new Event.Motion((Integer) args[0], (Integer) args[1], (Fixed) args[2],
          (Fixed) args[3]);
      case 3 -> new Event.Frame();
      case 4 -> new Event.Cancel();
      case 5 -> new Event.Shape((Integer) args[0], (Fixed) args[1], (Fixed) args[2]);
      case 6 -> new Event.Orientation((Integer) args[0], (Fixed) args[1]);
      default -> throw new WaylandException("wl_touch: unknown event opcode " + opcode);
    };
  }
}
