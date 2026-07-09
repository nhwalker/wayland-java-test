package io.github.nhwalker.unixsocket.internal;

import io.github.nhwalker.unixsocket.Fd;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link Fd} over a real file descriptor. The single atomic state field holds the fd value
 * while open, or a negative sentinel once closed or detached; close/detach race resolution is
 * a CAS (first caller wins).
 */
final class FfmFd implements Fd {

  private static final int CLOSED = -1;
  private static final int DETACHED = -2;

  private final AtomicInteger state;

  FfmFd(int fd) {
    this.state = new AtomicInteger(fd);
  }

  @Override
  public int value() {
    int current = state.get();
    if (current < 0) {
      throw new IllegalStateException(
          current == CLOSED ? "file descriptor is closed" : "file descriptor was detached");
    }
    return current;
  }

  @Override
  public boolean isOpen() {
    return state.get() >= 0;
  }

  @Override
  public int detach() {
    while (true) {
      int current = state.get();
      if (current < 0) {
        throw new IllegalStateException(
            current == CLOSED ? "file descriptor is closed" : "file descriptor was detached");
      }
      if (state.compareAndSet(current, DETACHED)) {
        return current;
      }
    }
  }

  @Override
  public void close() {
    while (true) {
      int current = state.get();
      if (current < 0) {
        return;
      }
      if (state.compareAndSet(current, CLOSED)) {
        Libc.closeQuietly(current);
        return;
      }
    }
  }

  @Override
  public MemorySegment map(MapMode mode, long offset, long size, Arena arena)
      throws IOException {
    if (offset < 0) {
      throw new IllegalArgumentException("offset must be non-negative: " + offset);
    }
    if (size <= 0) {
      throw new IllegalArgumentException("size must be positive: " + size);
    }
    int fd = value();
    int prot = switch (mode) {
      case READ_ONLY -> Libc.PROT_READ;
      case READ_WRITE, PRIVATE -> Libc.PROT_READ | Libc.PROT_WRITE;
    };
    int flags = mode == MapMode.PRIVATE ? Libc.MAP_PRIVATE : Libc.MAP_SHARED;
    try (Arena scratch = Arena.ofConfined()) {
      MemorySegment capture = scratch.allocate(Libc.CAPTURE_LAYOUT);
      MemorySegment result =
          Libc.mmap(capture, MemorySegment.NULL, size, prot, flags, fd, offset);
      long address = result.address();
      if (address == -1L) {
        throw Libc.ioException("mmap", Libc.errno(capture));
      }
      MemorySegment mapped = MemorySegment.ofAddress(address)
          .reinterpret(size, arena, segment -> Libc.munmapQuietly(address, size));
      return mode == MapMode.READ_ONLY ? mapped.asReadOnly() : mapped;
    }
  }

  @Override
  public int read(MemorySegment dst) throws IOException {
    if (dst.byteSize() == 0) {
      throw new IllegalArgumentException("dst must not be zero-length");
    }
    if (dst.isReadOnly()) {
      throw new IllegalArgumentException("dst must not be read-only");
    }
    int fd = value();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment capture = arena.allocate(Libc.CAPTURE_LAYOUT);
      MemorySegment scratch = dst.isNative() ? dst : arena.allocate(dst.byteSize());
      long n;
      while (true) {
        n = Libc.read(capture, fd, scratch, scratch.byteSize());
        if (n >= 0) {
          break;
        }
        int e = Libc.errno(capture);
        if (e != Libc.EINTR) {
          throw Libc.ioException("read", e);
        }
      }
      if (n == 0) {
        return -1;
      }
      if (scratch != dst) {
        MemorySegment.copy(scratch, 0, dst, 0, n);
      }
      return (int) n;
    }
  }

  @Override
  public void write(MemorySegment src) throws IOException {
    long length = src.byteSize();
    if (length == 0) {
      return;
    }
    int fd = value();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment capture = arena.allocate(Libc.CAPTURE_LAYOUT);
      MemorySegment scratch;
      if (src.isNative()) {
        scratch = src;
      } else {
        scratch = arena.allocate(length);
        scratch.copyFrom(src);
      }
      long written = 0;
      while (written < length) {
        long n = Libc.write(capture, fd, scratch.asSlice(written), length - written);
        if (n < 0) {
          int e = Libc.errno(capture);
          if (e == Libc.EINTR) {
            continue;
          }
          throw Libc.ioException("write", e);
        }
        written += n;
      }
    }
  }

  @Override
  public long size() throws IOException {
    int fd = value();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment capture = arena.allocate(Libc.CAPTURE_LAYOUT);
      MemorySegment stat = arena.allocate(Libc.STAT_SIZE, 8);
      if (Libc.fstat(capture, fd, stat) != 0) {
        throw Libc.ioException("fstat", Libc.errno(capture));
      }
      return stat.get(java.lang.foreign.ValueLayout.JAVA_LONG, Libc.ST_SIZE_OFFSET);
    }
  }

  @Override
  public void truncate(long size) throws IOException {
    if (size < 0) {
      throw new IllegalArgumentException("size must be non-negative: " + size);
    }
    int fd = value();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment capture = arena.allocate(Libc.CAPTURE_LAYOUT);
      while (true) {
        if (Libc.ftruncate(capture, fd, size) == 0) {
          return;
        }
        int e = Libc.errno(capture);
        if (e != Libc.EINTR) {
          throw Libc.ioException("ftruncate", e);
        }
      }
    }
  }
}
