package io.github.nhwalker.unixsocket;

import java.io.ByteArrayOutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Scriptable {@link Fd} for testing default methods: each queued chunk is served by one
 * {@code read} call (empty queue means end-of-stream), and writes are recorded.
 */
class FakeFd implements Fd {

  final Deque<byte[]> toRead = new ArrayDeque<>();
  final ByteArrayOutputStream written = new ByteArrayOutputStream();
  private boolean open = true;

  private void checkOpen() {
    if (!open) {
      throw new IllegalStateException("closed");
    }
  }

  @Override
  public int value() {
    checkOpen();
    return 99;
  }

  @Override
  public boolean isOpen() {
    return open;
  }

  @Override
  public int detach() {
    checkOpen();
    open = false;
    return 99;
  }

  @Override
  public void close() {
    open = false;
  }

  @Override
  public MemorySegment map(MapMode mode, long offset, long size, Arena arena) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int read(MemorySegment dst) {
    checkOpen();
    byte[] chunk = toRead.poll();
    if (chunk == null) {
      return -1;
    }
    int n = (int) Math.min(chunk.length, dst.byteSize());
    MemorySegment.copy(chunk, 0, dst, ValueLayout.JAVA_BYTE, 0, n);
    return n;
  }

  @Override
  public void write(MemorySegment src) {
    checkOpen();
    written.writeBytes(src.toArray(ValueLayout.JAVA_BYTE));
  }

  @Override
  public long size() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void truncate(long size) {
    throw new UnsupportedOperationException();
  }
}
