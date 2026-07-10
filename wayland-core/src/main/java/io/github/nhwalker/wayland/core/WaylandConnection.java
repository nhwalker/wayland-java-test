package io.github.nhwalker.wayland.core;

import io.github.nhwalker.unixsocket.UnixSocketChannel;
import io.github.nhwalker.unixsocket.UnixSocketProvider;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * A client connection to a Wayland compositor. The connection is protocol-agnostic: it moves
 * bytes and file descriptors, tracks object ids, and dispatches decoded events to per-object
 * handlers — all typed protocol knowledge lives in generated stubs.
 *
 * <p>Dispatch is single-threaded: {@link #dispatch()}/{@link #dispatchPending()} deliver events
 * on the calling thread, and handlers must not assume any other synchronization.
 */
public interface WaylandConnection extends AutoCloseable {

  /**
   * Connects using the standard environment resolution: {@code WAYLAND_SOCKET} (an inherited,
   * already-connected fd number), else {@code WAYLAND_DISPLAY} (absolute, or relative to
   * {@code XDG_RUNTIME_DIR}), defaulting to {@code wayland-0}.
   */
  static WaylandConnection open() throws IOException {
    UnixSocketProvider provider = UnixSocketProvider.provider();
    String inheritedFd = System.getenv("WAYLAND_SOCKET");
    if (inheritedFd != null) {
      return adopt(provider.adopt(provider.adoptFd(Integer.parseInt(inheritedFd))));
    }
    String display = System.getenv("WAYLAND_DISPLAY");
    if (display == null) {
      display = "wayland-0";
    }
    Path path = Path.of(display);
    if (!path.isAbsolute()) {
      String runtimeDir = System.getenv("XDG_RUNTIME_DIR");
      if (runtimeDir == null) {
        throw new IOException("XDG_RUNTIME_DIR is not set and WAYLAND_DISPLAY is not absolute");
      }
      path = Path.of(runtimeDir, display);
    }
    return open(path);
  }

  /** Connects to an explicit socket path. */
  static WaylandConnection open(Path socket) throws IOException {
    return adopt(UnixSocketProvider.provider().connect(socket));
  }

  /** Wraps an already-connected compositor socket, taking ownership of the channel. */
  static WaylandConnection adopt(UnixSocketChannel channel) {
    return new WireConnection(channel);
  }

  /**
   * Bootstrap: binds object id 1 to the given descriptor and returns its proxy. The first call
   * wins; subsequent calls return the same proxy and throw {@link WaylandException} if handed a
   * different descriptor. The kernel names no interface — the caller (the generated display
   * stub) supplies the descriptor.
   */
  Proxy display(InterfaceDesc displayInterface);

  /** Writes all buffered requests to the socket. */
  void flush() throws IOException;

  /**
   * Blocks until events arrive, decodes and delivers them, and returns the count delivered.
   */
  int dispatch() throws IOException;

  /** Delivers already-read events without doing I/O; returns the count delivered. */
  int dispatchPending();

  /**
   * Flushes, then dispatches until {@code condition} holds — the building block generated-code
   * users combine with {@code display.sync()} to implement a roundtrip.
   */
  default void dispatchUntil(BooleanSupplier condition) throws IOException {
    flush();
    while (!condition.getAsBoolean()) {
      dispatch();
    }
  }

  /**
   * Marks the connection fatally errored ({@code wl_display.error}). Every later marshal or
   * dispatch call throws the resulting {@link WaylandProtocolException}. Called by the
   * generated display stub's control wiring — the kernel itself names no interface.
   */
  void fatalError(Proxy object, int code, String message);

  /** Retires a server-confirmed-dead object id for reuse ({@code wl_display.delete_id}). */
  void retireId(int id);

  /** The fatal protocol error, if one has been raised. */
  Optional<WaylandProtocolException> error();

  boolean isOpen();

  /** Closes the connection and its socket. Idempotent; never throws. */
  @Override
  void close();
}
