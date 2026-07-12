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

/** A compositor output (monitor). */
@WaylandGenerated(protocol = "wayland", interfaceName = "wl_output", version = 4)
public final class WlOutput {

  public static final InterfaceDesc INTERFACE = InterfaceDesc.of("wl_output", 4,
      List.of(
          MessageDesc.of("release", 3, true)),
      List.of(
          MessageDesc.of("geometry", 1, false,
              ArgDesc.intArg("x"),
              ArgDesc.intArg("y"),
              ArgDesc.intArg("physical_width"),
              ArgDesc.intArg("physical_height"),
              ArgDesc.intArg("subpixel"),
              ArgDesc.stringArg("make"),
              ArgDesc.stringArg("model"),
              ArgDesc.intArg("transform")),
          MessageDesc.of("mode", 1, false,
              ArgDesc.uintArg("flags"),
              ArgDesc.intArg("width"),
              ArgDesc.intArg("height"),
              ArgDesc.intArg("refresh")),
          MessageDesc.of("done", 2, false),
          MessageDesc.of("scale", 2, false,
              ArgDesc.intArg("factor")),
          MessageDesc.of("name", 4, false,
              ArgDesc.stringArg("name")),
          MessageDesc.of("description", 4, false,
              ArgDesc.stringArg("description"))));

  public static final ProxyType<WlOutput> TYPE = ProxyType.of(INTERFACE, WlOutput::new);

  private final Proxy proxy;

  WlOutput(Proxy proxy) {
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

  /** Since protocol version 3. */
  public void release() {
    proxy.marshalDestructor(0);
  }

  public sealed interface Event permits Event.Geometry, Event.Mode, Event.Done, Event.Scale,
      Event.Name, Event.Description {

    /** Raw enum ints for {@code subpixel}/{@code transform}; decode via the enum lookups. */
    record Geometry(int x, int y, int physicalWidth, int physicalHeight, int subpixel, String make,
        String model, int transform) implements Event {}

    /** {@code flags} is the bitfield decoded by {@link WlOutput.Mode#setOf(int)}. */
    record Mode(int flags, int width, int height, int refresh) implements Event {}

    /** Since protocol version 2. */
    record Done() implements Event {}

    /** Since protocol version 2. */
    record Scale(int factor) implements Event {}

    /** Since protocol version 4. */
    record Name(String name) implements Event {}

    /** Since protocol version 4. */
    record Description(String description) implements Event {}
  }

  /** Installs the event handler for this object, replacing any previous one. */
  public void onEvent(Consumer<? super Event> handler) {
    proxy.setEventHandler((opcode, args) -> handler.accept(decode(opcode, args)));
  }

  private static Event decode(int opcode, Object[] args) {
    return switch (opcode) {
      case 0 -> new Event.Geometry((Integer) args[0], (Integer) args[1], (Integer) args[2],
          (Integer) args[3], (Integer) args[4], (String) args[5], (String) args[6],
          (Integer) args[7]);
      case 1 -> new Event.Mode((Integer) args[0], (Integer) args[1], (Integer) args[2],
          (Integer) args[3]);
      case 2 -> new Event.Done();
      case 3 -> new Event.Scale((Integer) args[0]);
      case 4 -> new Event.Name((String) args[0]);
      case 5 -> new Event.Description((String) args[0]);
      default -> throw new WaylandException("wl_output: unknown event opcode " + opcode);
    };
  }

  public enum Subpixel implements WireEnum {
    UNKNOWN(0),
    NONE(1),
    HORIZONTAL_RGB(2),
    HORIZONTAL_BGR(3),
    VERTICAL_RGB(4),
    VERTICAL_BGR(5);

    private final int value;

    Subpixel(int value) {
      this.value = value;
    }

    @Override
    public int value() {
      return value;
    }

    public static Optional<Subpixel> lookup(int value) {
      return WireEnums.lookup(Subpixel.class, value);
    }
  }

  /** Entry names starting with a digit in the XML are prefixed with '_' (generator rule). */
  public enum Transform implements WireEnum {
    NORMAL(0),
    _90(1),
    _180(2),
    _270(3),
    FLIPPED(4),
    FLIPPED_90(5),
    FLIPPED_180(6),
    FLIPPED_270(7);

    private final int value;

    Transform(int value) {
      this.value = value;
    }

    @Override
    public int value() {
      return value;
    }

    public static Optional<Transform> lookup(int value) {
      return WireEnums.lookup(Transform.class, value);
    }
  }

  /** Bitfield enum: decode with {@link #setOf}, encode with {@link WireEnums#mask}. */
  public enum Mode implements WireEnum {
    CURRENT(0x1),
    PREFERRED(0x2);

    private final int value;

    Mode(int value) {
      this.value = value;
    }

    @Override
    public int value() {
      return value;
    }

    public static EnumSet<Mode> setOf(int bits) {
      return WireEnums.setOf(Mode.class, bits);
    }
  }
}
