package io.github.nhwalker.unixsocket;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
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
   * created by the caller's own FFM code — as an owning {@link Fd} handle, taking ownership.
   */
  Fd adoptFd(int rawFd);

  /**
   * Creates an anonymous shared-memory descriptor of the given size ({@code memfd_create(2)} or
   * equivalent, sized with {@code ftruncate(2)}), ready to {@link Fd#map map} and to pass to a
   * peer — how a Wayland client backs a {@code wl_shm} pool.
   *
   * @param size the initial size in bytes; must be positive
   * @throws IllegalArgumentException if {@code size} is not positive
   * @throws IOException on creation failure ({@code ENOMEM}, {@code EMFILE}, ...)
   */
  Fd sharedMemory(long size) throws IOException;

  /**
   * Creates a shared-memory descriptor pre-filled with a copy of {@code content}: sized to
   * {@code content.byteSize()}, mapped, copied, and unmapped in one call — the common
   * "send these bytes via a file descriptor" case without the map/copy boilerplate.
   *
   * <p>The copy is eager: later changes to {@code content} do not affect the descriptor, and
   * errors surface here rather than at send time. On failure the descriptor is closed before
   * the exception propagates, so nothing leaks.
   *
   * @param content the bytes to copy in; must not be empty
   * @throws IllegalArgumentException if {@code content} is empty
   * @throws IOException on creation or mapping failure
   */
  default Fd sharedMemory(MemorySegment content) throws IOException {
    Fd fd = sharedMemory(content.byteSize());
    try (Arena arena = Arena.ofConfined()) {
      fd.map(Fd.MapMode.READ_WRITE, 0, content.byteSize(), arena).copyFrom(content);
      return fd;
    } catch (Throwable t) {
      fd.close();
      throw t;
    }
  }

  /** Heap-array convenience for {@link #sharedMemory(MemorySegment)}. */
  default Fd sharedMemory(byte[] content) throws IOException {
    return sharedMemory(MemorySegment.ofArray(content));
  }

  /**
   * Packed-pixel convenience for {@link #sharedMemory(MemorySegment)}: copies
   * {@code content.length * 4} bytes in native byte order — on little-endian platforms this
   * makes an ARGB pixel array (for example a {@code BufferedImage.TYPE_INT_ARGB_PRE} data
   * buffer) byte-for-byte compatible with {@code wl_shm} {@code ARGB8888}.
   */
  default Fd sharedMemory(int[] content) throws IOException {
    return sharedMemory(MemorySegment.ofArray(content));
  }

  /**
   * {@link ByteBuffer} convenience for {@link #sharedMemory(MemorySegment)}: copies the
   * buffer's remaining bytes (position to limit) and advances the position to the limit on
   * success. The buffer must not be a read-only heap buffer.
   */
  default Fd sharedMemory(ByteBuffer content) throws IOException {
    Fd fd = sharedMemory(MemorySegment.ofBuffer(content));
    content.position(content.limit());
    return fd;
  }

  /**
   * Creates a unidirectional pipe via {@code pipe2(2)} — how clipboard and drag-and-drop
   * transfers work: pass one end to the peer, stream on the other with {@link Fd#read} /
   * {@link Fd#write}.
   */
  Pipe pipe() throws IOException;

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

  /** The two ends of a pipe, as created by {@link #pipe()}. Closing the pipe closes both. */
  interface Pipe extends AutoCloseable {

    /** The end data is read from. */
    Fd readEnd();

    /** The end data is written to. */
    Fd writeEnd();

    /** Closes both ends; the write end is closed even if closing the read end fails. */
    @Override
    default void close() {
      try {
        readEnd().close();
      } finally {
        writeEnd().close();
      }
    }
  }
}
