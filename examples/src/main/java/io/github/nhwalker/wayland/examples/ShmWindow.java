package io.github.nhwalker.wayland.examples;

import io.github.nhwalker.unixsocket.Fd;
import io.github.nhwalker.unixsocket.UnixSocketProvider;
import io.github.nhwalker.wayland.client.WlBuffer;
import io.github.nhwalker.wayland.client.WlCallback;
import io.github.nhwalker.wayland.client.WlCompositor;
import io.github.nhwalker.wayland.client.WlDisplay;
import io.github.nhwalker.wayland.client.WlRegistry;
import io.github.nhwalker.wayland.client.WlShm;
import io.github.nhwalker.wayland.client.WlShmPool;
import io.github.nhwalker.wayland.client.WlSurface;
import io.github.nhwalker.wayland.core.ProxyType;
import io.github.nhwalker.wayland.core.WaylandConnection;
import io.github.nhwalker.wayland.protocols.xdg.XdgSurface;
import io.github.nhwalker.wayland.protocols.xdg.XdgToplevel;
import io.github.nhwalker.wayland.protocols.xdg.XdgWmBase;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The end-to-end demo: an xdg-shell toplevel window whose pixels travel through this project's
 * whole stack — drawn into a memfd, shared via SCM_RIGHTS, composed by a real compositor.
 *
 * <p>Run {@link #main} under any Wayland session (or headless Weston) to see the window; the
 * CI integration test runs the same {@link #run} flow against {@code weston --backend=headless}.
 */
public final class ShmWindow {

  private ShmWindow() {}

  public record Result(int width, int height, int framesRendered) {}

  public static void main(String[] args) throws IOException {
    int frames = args.length > 0 ? Integer.parseInt(args[0]) : 300;
    try (WaylandConnection connection = WaylandConnection.open()) {
      Result result = run(connection, frames);
      System.out.println("rendered " + result.framesRendered() + " frames at "
          + result.width() + "x" + result.height());
    }
  }

  /**
   * Creates the window, renders up to {@code maxFrames} frames (stopping early if the
   * compositor asks the window to close), tears everything down, and verifies the connection
   * finished without a protocol error.
   */
  public static Result run(WaylandConnection connection, int maxFrames) throws IOException {
    WlDisplay display = WlDisplay.from(connection);
    WlRegistry registry = display.getRegistry();

    Map<String, WlRegistry.Event.Global> globals = new HashMap<>();
    registry.onEvent(event -> {
      if (event instanceof WlRegistry.Event.Global global) {
        globals.put(global.interfaceName(), global);
      }
    });
    roundtrip(connection, display);

    WlCompositor compositor =
        bind(registry, globals, "wl_compositor", WlCompositor.TYPE,
            WlCompositor.INTERFACE.version());
    WlShm shm = bind(registry, globals, "wl_shm", WlShm.TYPE, WlShm.INTERFACE.version());
    XdgWmBase wmBase =
        bind(registry, globals, "xdg_wm_base", XdgWmBase.TYPE, XdgWmBase.INTERFACE.version());
    wmBase.onEvent(event -> {
      if (event instanceof XdgWmBase.Event.Ping ping) {
        wmBase.pong(ping.serial());
      }
    });

    WlSurface surface = compositor.createSurface();
    XdgSurface xdgSurface = wmBase.getXdgSurface(surface);
    XdgToplevel toplevel = xdgSurface.getToplevel();
    toplevel.setTitle("wayland-java shm demo");
    toplevel.setAppId("io.github.nhwalker.wayland.examples.ShmWindow");

    AtomicBoolean configured = new AtomicBoolean();
    xdgSurface.onEvent(event -> {
      if (event instanceof XdgSurface.Event.Configure configure) {
        xdgSurface.ackConfigure(configure.serial());
        configured.set(true);
      }
    });
    AtomicBoolean closed = new AtomicBoolean();
    int[] size = {640, 480};
    toplevel.onEvent(event -> {
      switch (event) {
        case XdgToplevel.Event.Configure configure -> {
          if (configure.width() > 0 && configure.height() > 0) {
            size[0] = configure.width();
            size[1] = configure.height();
          }
        }
        case XdgToplevel.Event.Close ignored -> closed.set(true);
        default -> {}
      }
    });

    // xdg-shell forbids attaching a buffer before the first configure.
    surface.commit();
    connection.dispatchUntil(configured::get);

    int width = size[0];
    int height = size[1];
    int stride = width * 4;
    long poolSize = (long) stride * height;
    int frame = 0;
    try (Fd fd = UnixSocketProvider.provider().sharedMemory(poolSize);
        Arena arena = Arena.ofConfined()) {
      MemorySegment pixels = fd.map(Fd.MapMode.READ_WRITE, 0, poolSize, arena);
      WlShmPool pool = shm.createPool(fd, (int) poolSize);
      WlBuffer buffer = pool.createBuffer(0, width, height, stride, WlShm.Format.XRGB8888);

      // Demo simplification: one buffer, re-used after each frame callback (a production
      // client double-buffers or waits for wl_buffer.release).
      while (frame < maxFrames && !closed.get()) {
        draw(pixels, width, height, frame);
        WlCallback frameCallback = surface.frame();
        AtomicBoolean frameDone = new AtomicBoolean();
        frameCallback.onEvent(event -> frameDone.set(true));
        surface.attach(buffer, 0, 0);
        surface.damage(0, 0, width, height);
        surface.commit();
        connection.dispatchUntil(() -> frameDone.get() || closed.get());
        frame++;
      }

      buffer.destroy();
      pool.destroy();
    }

    toplevel.destroy();
    xdgSurface.destroy();
    surface.destroy();
    wmBase.destroy();
    roundtrip(connection, display);
    if (connection.error().isPresent()) {
      throw connection.error().get();
    }
    return new Result(width, height, frame);
  }

  private static <T> T bind(WlRegistry registry, Map<String, WlRegistry.Event.Global> globals,
      String name, ProxyType<T> type, int maxVersion) {
    WlRegistry.Event.Global global = globals.get(name);
    if (global == null) {
      throw new IllegalStateException("compositor does not advertise " + name);
    }
    return registry.bind(global.name(), type, Math.min(global.version(), maxVersion));
  }

  private static void roundtrip(WaylandConnection connection, WlDisplay display)
      throws IOException {
    WlCallback callback = display.sync();
    AtomicBoolean done = new AtomicBoolean();
    callback.onEvent(event -> done.set(true));
    connection.dispatchUntil(done::get);
  }

  /** A drifting gradient, so successive frames differ visibly. */
  private static void draw(MemorySegment pixels, int width, int height, int frame) {
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int red = (x * 255 / width + frame * 8) & 0xFF;
        int green = y * 255 / height;
        pixels.setAtIndex(ValueLayout.JAVA_INT, (long) y * width + x,
            0xFF00_0000 | red << 16 | green << 8 | 0x80);
      }
    }
  }
}
