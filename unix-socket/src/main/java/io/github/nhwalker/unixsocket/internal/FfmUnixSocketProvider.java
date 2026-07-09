package io.github.nhwalker.unixsocket.internal;

import io.github.nhwalker.unixsocket.Fd;
import io.github.nhwalker.unixsocket.UnixSocketChannel;
import io.github.nhwalker.unixsocket.UnixSocketProvider;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.net.UnixDomainSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * The FFM-based {@link UnixSocketProvider}, registered via {@code META-INF/services}. Internal:
 * obtain instances through {@link UnixSocketProvider#provider()}.
 *
 * <p>Descriptors adopted via {@link #adopt} and {@link #adoptFd} get {@code FD_CLOEXEC} set,
 * upholding the package-wide CLOEXEC guarantee (inherited fds like {@code WAYLAND_SOCKET}
 * deliberately arrive without it).
 */
public final class FfmUnixSocketProvider implements UnixSocketProvider {

  /** Public no-arg constructor for {@link java.util.ServiceLoader}. */
  public FfmUnixSocketProvider() {}

  @Override
  public UnixSocketChannel connect(UnixDomainSocketAddress address) throws IOException {
    byte[] path = address.getPath().toString().getBytes(StandardCharsets.UTF_8);
    if (path.length == 0) {
      throw new IOException("cannot connect to an unnamed socket address");
    }
    if (path.length > Libc.SUN_PATH_MAX) {
      throw new IOException(
          "socket path too long (" + path.length + " > " + Libc.SUN_PATH_MAX + " bytes): "
              + address);
    }
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment capture = arena.allocate(Libc.CAPTURE_LAYOUT);
      int fd = Libc.socket(capture, Libc.AF_UNIX, Libc.SOCK_STREAM | Libc.SOCK_CLOEXEC, 0);
      if (fd < 0) {
        throw Libc.ioException("socket", Libc.errno(capture));
      }
      MemorySegment addr = arena.allocate(Libc.SOCKADDR_UN_SIZE, 8);
      addr.set(ValueLayout.JAVA_SHORT, 0, (short) Libc.AF_UNIX);
      MemorySegment.copy(path, 0, addr, ValueLayout.JAVA_BYTE, Libc.SUN_PATH_OFFSET,
          path.length);
      if (Libc.connect(capture, fd, addr, (int) Libc.SOCKADDR_UN_SIZE) != 0) {
        int e = Libc.errno(capture);
        Libc.closeQuietly(fd);
        throw Libc.ioException("connect", e);
      }
      return new FfmUnixSocketChannel(fd);
    }
  }

  @Override
  public Pair pair() throws IOException {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment capture = arena.allocate(Libc.CAPTURE_LAYOUT);
      MemorySegment sv = arena.allocate(8, 4);
      if (Libc.socketpair(capture, Libc.AF_UNIX, Libc.SOCK_STREAM | Libc.SOCK_CLOEXEC, 0, sv)
          != 0) {
        throw Libc.ioException("socketpair", Libc.errno(capture));
      }
      return new ChannelPair(
          new FfmUnixSocketChannel(sv.get(ValueLayout.JAVA_INT, 0)),
          new FfmUnixSocketChannel(sv.get(ValueLayout.JAVA_INT, 4)));
    }
  }

  @Override
  public UnixSocketChannel adopt(Fd socket) throws IOException {
    // Verify before taking ownership: on failure the caller's handle is untouched.
    int raw = socket.value();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment capture = arena.allocate(Libc.CAPTURE_LAYOUT);
      MemorySegment type = arena.allocate(4, 4);
      MemorySegment typeLen = arena.allocate(4, 4);
      typeLen.set(ValueLayout.JAVA_INT, 0, 4);
      if (Libc.getsockopt(capture, raw, Libc.SOL_SOCKET, Libc.SO_TYPE, type, typeLen) != 0) {
        throw Libc.ioException("getsockopt(SO_TYPE)", Libc.errno(capture));
      }
      if (type.get(ValueLayout.JAVA_INT, 0) != Libc.SOCK_STREAM) {
        throw new IOException("file descriptor is not a SOCK_STREAM socket");
      }
    }
    int owned = socket.detach();
    Libc.setCloexec(owned);
    return new FfmUnixSocketChannel(owned);
  }

  @Override
  public Fd adoptFd(int rawFd) {
    if (rawFd < 0) {
      throw new IllegalArgumentException("negative file descriptor: " + rawFd);
    }
    Libc.setCloexec(rawFd);
    return new FfmFd(rawFd);
  }

  @Override
  public Fd sharedMemory(long size) throws IOException {
    if (size <= 0) {
      throw new IllegalArgumentException("size must be positive: " + size);
    }
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment capture = arena.allocate(Libc.CAPTURE_LAYOUT);
      MemorySegment name = arena.allocateFrom("java-unix-socket");
      int fd = Libc.memfdCreate(capture, name, Libc.MFD_CLOEXEC);
      if (fd < 0) {
        throw Libc.ioException("memfd_create", Libc.errno(capture));
      }
      while (true) {
        if (Libc.ftruncate(capture, fd, size) == 0) {
          return new FfmFd(fd);
        }
        int e = Libc.errno(capture);
        if (e != Libc.EINTR) {
          Libc.closeQuietly(fd);
          throw Libc.ioException("ftruncate", e);
        }
      }
    }
  }

  @Override
  public Pipe pipe() throws IOException {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment capture = arena.allocate(Libc.CAPTURE_LAYOUT);
      MemorySegment fds = arena.allocate(8, 4);
      if (Libc.pipe2(capture, fds, Libc.O_CLOEXEC) != 0) {
        throw Libc.ioException("pipe2", Libc.errno(capture));
      }
      return new FdPipe(
          new FfmFd(fds.get(ValueLayout.JAVA_INT, 0)),
          new FfmFd(fds.get(ValueLayout.JAVA_INT, 4)));
    }
  }

  private record ChannelPair(UnixSocketChannel first, UnixSocketChannel second)
      implements Pair {}

  private record FdPipe(Fd readEnd, Fd writeEnd) implements Pipe {}
}
