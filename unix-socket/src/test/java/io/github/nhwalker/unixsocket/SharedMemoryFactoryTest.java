package io.github.nhwalker.unixsocket;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class SharedMemoryFactoryTest {

  /** An Fd whose mapped memory is a plain byte array, so copies can be asserted. */
  private static class MappableFakeFd extends FakeFd {
    final byte[] store;

    MappableFakeFd(long size) {
      store = new byte[Math.toIntExact(size)];
    }

    @Override
    public MemorySegment map(MapMode mode, long offset, long size, Arena arena) {
      return MemorySegment.ofArray(store).asSlice(offset, size);
    }
  }

  /** Provider stub where only sharedMemory(long) works; overloads under test are defaults. */
  private static class TestProvider implements UnixSocketProvider {
    MappableFakeFd lastCreated;

    @Override
    public Fd sharedMemory(long size) {
      if (size <= 0) {
        throw new IllegalArgumentException("size must be positive: " + size);
      }
      lastCreated = new MappableFakeFd(size);
      return lastCreated;
    }

    @Override
    public UnixSocketChannel connect(UnixDomainSocketAddress address) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Pair pair() {
      throw new UnsupportedOperationException();
    }

    @Override
    public UnixSocketChannel adopt(Fd socket) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Fd adoptFd(int rawFd) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Pipe pipe() {
      throw new UnsupportedOperationException();
    }
  }

  @Test
  void byteArrayContentIsCopiedIn() throws IOException {
    var provider = new TestProvider();

    Fd fd = provider.sharedMemory(new byte[] {1, 2, 3, 4});

    assertTrue(fd.isOpen());
    assertArrayEquals(new byte[] {1, 2, 3, 4}, provider.lastCreated.store);
  }

  @Test
  void intArrayContentIsCopiedInNativeOrder() throws IOException {
    var provider = new TestProvider();
    int[] pixels = {0x11223344, 0x55667788};

    provider.sharedMemory(pixels);

    int[] roundTripped =
        MemorySegment.ofArray(provider.lastCreated.store)
            .toArray(ValueLayout.JAVA_INT_UNALIGNED);
    assertArrayEquals(pixels, roundTripped);
    assertEquals(8, provider.lastCreated.store.length);
  }

  @Test
  void byteBufferCopiesRemainingBytesAndAdvancesPosition() throws IOException {
    var provider = new TestProvider();
    ByteBuffer buffer = ByteBuffer.wrap(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9});
    buffer.position(2).limit(8);

    provider.sharedMemory(buffer);

    assertArrayEquals(new byte[] {2, 3, 4, 5, 6, 7}, provider.lastCreated.store);
    assertEquals(8, buffer.position());
  }

  @Test
  void memorySegmentContentIsCopiedIn() throws IOException {
    var provider = new TestProvider();

    provider.sharedMemory(MemorySegment.ofArray(new byte[] {9, 8, 7}));

    assertArrayEquals(new byte[] {9, 8, 7}, provider.lastCreated.store);
  }

  @Test
  void emptyContentIsRejected() {
    var provider = new TestProvider();

    assertThrows(IllegalArgumentException.class, () -> provider.sharedMemory(new byte[0]));
  }

  @Test
  void fdIsClosedWhenTheCopyFails() {
    var provider = new TestProvider() {
      @Override
      public Fd sharedMemory(long size) {
        lastCreated = new MappableFakeFd(size) {
          @Override
          public MemorySegment map(MapMode mode, long offset, long size, Arena arena) {
            throw new UnsupportedOperationException("map failed");
          }
        };
        return lastCreated;
      }
    };

    assertThrows(
        UnsupportedOperationException.class,
        () -> provider.sharedMemory(new byte[] {1, 2, 3}));

    assertFalse(provider.lastCreated.isOpen());
  }
}
