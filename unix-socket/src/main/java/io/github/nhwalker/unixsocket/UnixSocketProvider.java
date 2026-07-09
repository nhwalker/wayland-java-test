package io.github.nhwalker.unixsocket;

import java.io.IOException;
import java.net.UnixDomainSocketAddress;
import java.nio.file.Path;
import java.util.ServiceLoader;

/**
 * Factory for Unix domain socket channels and file-descriptor handles. Implementations are
 * discovered via {@link ServiceLoader}; an implementation registers itself as a provider of this
 * interface (via {@code META-INF/services}).
 *
 * <p>Every file descriptor created by a provider — sockets, pairs, and received descriptors —
 * has {@code FD_CLOEXEC} set.
 */
public interface UnixSocketProvider {

  /**
   * Returns the first installed provider, located via {@link ServiceLoader} on the thread
   * context class loader. The lookup is not cached; callers should retain the returned instance.
   *
   * @throws IllegalStateException if no implementation is installed
   */
  static UnixSocketProvider provider() {
    return ServiceLoader.load(UnixSocketProvider.class)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException(
            "No " + UnixSocketProvider.class.getName() + " implementation found"));
  }

  /**
   * Connects a blocking {@code SOCK_STREAM} socket to the given address.
   *
   * @throws IOException on connect failure ({@code ENOENT}, {@code ECONNREFUSED}, ...)
   */
  UnixSocketChannel connect(UnixDomainSocketAddress address) throws IOException;

  /** Convenience: connects to a filesystem path. See {@link #connect(UnixDomainSocketAddress)}. */
  default UnixSocketChannel connect(Path path) throws IOException {
    return connect(UnixDomainSocketAddress.of(path));
  }

  /**
   * Creates a connected channel pair via {@code socketpair(2)} — primarily for loopback testing
   * of code written against {@link UnixSocketChannel}.
   */
  Pair pair() throws IOException;

  /**
   * Wraps an already-connected {@code SOCK_STREAM} Unix domain socket as a channel, taking
   * ownership of {@code socket}: the handle is {@link Fd#detach() detached} and the returned
   * channel now owns the descriptor. This is how a Wayland client honors the
   * {@code WAYLAND_SOCKET} inherited-descriptor handshake.
   *
   * @throws IOException if the descriptor cannot be used as a channel
   */
  UnixSocketChannel adopt(Fd socket) throws IOException;

  /**
   * Wraps a raw file descriptor — for example one parsed from an environment variable, or
   * created by the caller's own FFM code ({@code memfd_create(2)}, ...) — as an owning
   * {@link Fd} handle, taking ownership.
   */
  Fd adoptFd(int rawFd);

  /** Two connected channels, as created by {@link #pair()}. Closing the pair closes both ends. */
  interface Pair extends AutoCloseable {

    /** One end of the pair. */
    UnixSocketChannel first();

    /** The other end of the pair. */
    UnixSocketChannel second();

    /** Closes both channels; the second is closed even if closing the first fails. */
    @Override
    default void close() throws IOException {
      try {
        first().close();
      } finally {
        second().close();
      }
    }
  }
}
