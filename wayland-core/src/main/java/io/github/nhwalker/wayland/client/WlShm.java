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
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** The shared-memory global: creates pools from file descriptors. */
@WaylandGenerated(protocol = "wayland", interfaceName = "wl_shm", version = 2)
public final class WlShm {

  public static final InterfaceDesc INTERFACE = InterfaceDesc.of("wl_shm", 2,
      List.of(
          MessageDesc.of("create_pool", 1, false,
              ArgDesc.newIdArg("id", () -> WlShmPool.INTERFACE),
              ArgDesc.fdArg("fd"),
              ArgDesc.intArg("size")),
          MessageDesc.of("release", 2, true)),
      List.of(
          MessageDesc.of("format", 1, false,
              ArgDesc.uintArg("format"))));

  public static final ProxyType<WlShm> TYPE = ProxyType.of(INTERFACE, WlShm::new);

  private final Proxy proxy;

  WlShm(Proxy proxy) {
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
   * Creates a pool backed by {@code fd} (e.g. from
   * {@link io.github.nhwalker.unixsocket.UnixSocketProvider#sharedMemory(long)}). The caller
   * retains ownership of the handle; keep it open until
   * {@link io.github.nhwalker.wayland.core.WaylandConnection#flush()} has returned.
   */
  public WlShmPool createPool(Fd fd, int size) {
    return WlShmPool.TYPE.wrap(proxy.marshalConstructor(0, WlShmPool.INTERFACE, fd, size));
  }

  /** Since protocol version 2. */
  public void release() {
    proxy.marshalDestructor(1);
  }

  public sealed interface Event permits Event.Format {

    /**
     * A pixel format the compositor supports. Raw enum int for forward compatibility; decode
     * via {@link WlShm.Format#lookup}.
     */
    record Format(int format) implements Event {}
  }

  /** Installs the event handler for this object, replacing any previous one. */
  public void onEvent(Consumer<? super Event> handler) {
    proxy.setEventHandler((opcode, args) -> handler.accept(decode(opcode, args)));
  }

  private static Event decode(int opcode, Object[] args) {
    return switch (opcode) {
      case 0 -> new Event.Format((Integer) args[0]);
      default -> throw new WaylandException("wl_shm: unknown event opcode " + opcode);
    };
  }

  public enum Error implements WireEnum {
    INVALID_FORMAT(0),
    INVALID_STRIDE(1),
    INVALID_FD(2);

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
   * Buffer pixel formats. {@code ARGB8888}/{@code XRGB8888} are 0/1; all others are DRM fourcc
   * codes. A representative subset of wayland.xml's list — the generator emits the complete
   * set from the XML.
   */
  public enum Format implements WireEnum {
    ARGB8888(0),
    XRGB8888(1),
    C8(0x20203843),
    RGB565(0x36314752),
    RGB888(0x34324752),
    BGR888(0x34324742),
    XBGR8888(0x34324258),
    ABGR8888(0x34324241),
    RGBX8888(0x34325852),
    RGBA8888(0x34324152),
    BGRX8888(0x34325842),
    BGRA8888(0x34324142),
    XRGB2101010(0x30335258),
    ARGB2101010(0x30335241),
    YUYV(0x56595559),
    NV12(0x3231564e);

    private final int value;

    Format(int value) {
      this.value = value;
    }

    @Override
    public int value() {
      return value;
    }

    public static Optional<Format> lookup(int value) {
      return WireEnums.lookup(Format.class, value);
    }
  }
}
