package io.github.nhwalker.unixsocket.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nhwalker.unixsocket.Fd;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Integration tests for FfmFd over real memfds and pipes. */
@Timeout(10)
class FfmFdTest {

  private final FfmUnixSocketProvider provider = new FfmUnixSocketProvider();

  @Test
  void pipeWriteThenReadAllBytes() throws Exception {
    byte[] data = "clipboard contents".getBytes(StandardCharsets.UTF_8);
    try (var pipe = provider.pipe()) {
      pipe.writeEnd().write(MemorySegment.ofArray(data));
      pipe.writeEnd().close();
      assertArrayEquals(data, pipe.readEnd().readAllBytes());
    }
  }

  @Test
  void writeToClosedReadEndFails() throws Exception {
    try (var pipe = provider.pipe()) {
      pipe.readEnd().close();
      MemorySegment chunk = MemorySegment.ofArray(new byte[4096]);
      IOException e = assertThrows(IOException.class, () -> {
        for (int i = 0; i < 1000; i++) {
          pipe.writeEnd().write(chunk);
        }
      });
      assertTrue(e.getMessage().contains("EPIPE"), e.getMessage());
    }
  }

  @Test
  void memfdTruncateAndSize() throws Exception {
    try (Fd shm = provider.sharedMemory(4096)) {
      assertEquals(4096, shm.size());
      shm.truncate(8192);
      assertEquals(8192, shm.size());
    }
  }

  @Test
  void privateMappingWritesAreInvisible() throws Exception {
    byte[] pattern = {1, 2, 3, 4};
    try (Fd shm = provider.sharedMemory(pattern); Arena arena = Arena.ofConfined()) {
      MemorySegment cow = shm.map(Fd.MapMode.PRIVATE, 0, pattern.length, arena);
      cow.set(ValueLayout.JAVA_BYTE, 0, (byte) 99);

      MemorySegment readOnly = shm.map(Fd.MapMode.READ_ONLY, 0, pattern.length, arena);
      assertTrue(readOnly.isReadOnly());
      assertArrayEquals(pattern, readOnly.toArray(ValueLayout.JAVA_BYTE));
    }
  }

  @Test
  void readWriteMappingVisibleAcrossMappings() throws Exception {
    try (Fd shm = provider.sharedMemory(4); Arena arena = Arena.ofConfined()) {
      MemorySegment writable = shm.map(Fd.MapMode.READ_WRITE, 0, 4, arena);
      writable.set(ValueLayout.JAVA_BYTE, 2, (byte) 42);

      MemorySegment readOnly = shm.map(Fd.MapMode.READ_ONLY, 0, 4, arena);
      assertEquals(42, readOnly.get(ValueLayout.JAVA_BYTE, 2));
    }
  }

  @Test
  void mappingSurvivesHandleClose() throws Exception {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment mapped;
      try (Fd shm = provider.sharedMemory(new byte[] {7, 8, 9})) {
        mapped = shm.map(Fd.MapMode.READ_ONLY, 0, 3, arena);
      }
      assertArrayEquals(new byte[] {7, 8, 9}, mapped.toArray(ValueLayout.JAVA_BYTE));
    }
  }

  @Test
  void fdLifecycle() throws Exception {
    Fd fd = provider.sharedMemory(8);
    assertTrue(fd.isOpen());
    fd.close();
    fd.close();
    assertFalse(fd.isOpen());
    assertThrows(IllegalStateException.class, fd::value);
    assertThrows(IllegalStateException.class, fd::detach);
  }

  @Test
  void detachReleasesOwnershipWithoutClosing() throws Exception {
    Fd original = provider.sharedMemory(8);
    int raw = original.detach();
    assertFalse(original.isOpen());
    original.close(); // no-op after detach — must not close the raw descriptor
    assertThrows(IllegalStateException.class, original::value);

    try (Fd readopted = provider.adoptFd(raw)) {
      assertEquals(8, readopted.size()); // descriptor still valid
    }
  }

  @Test
  void readReturnsMinusOneAtEndOfStream() throws Exception {
    try (var pipe = provider.pipe(); Arena arena = Arena.ofConfined()) {
      pipe.writeEnd().close();
      assertEquals(-1, pipe.readEnd().read(arena.allocate(8)));
    }
  }

  @Test
  void heapSegmentReadAndWrite() throws Exception {
    try (var pipe = provider.pipe()) {
      pipe.writeEnd().write(MemorySegment.ofArray(new byte[] {5, 6, 7}));
      byte[] buffer = new byte[8];
      int n = pipe.readEnd().read(MemorySegment.ofArray(buffer));
      assertEquals(3, n);
      assertArrayEquals(new byte[] {5, 6, 7, 0, 0, 0, 0, 0}, buffer);
    }
  }
}
