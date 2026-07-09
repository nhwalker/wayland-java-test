package io.github.nhwalker.unixsocket;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class FdDefaultsTest {

  @Test
  void byteBufferReadFillsFromPositionAndAdvances() throws IOException {
    var fd = new FakeFd();
    fd.toRead.add(new byte[] {1, 2, 3, 4});
    java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(10);
    buffer.position(3);

    int n = fd.read(buffer);

    assertEquals(4, n);
    assertEquals(7, buffer.position());
    assertArrayEquals(new byte[] {0, 0, 0, 1, 2, 3, 4, 0, 0, 0}, buffer.array());
  }

  @Test
  void byteBufferReadDoesNotAdvanceOnEndOfStream() throws IOException {
    var fd = new FakeFd();
    java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(10);
    buffer.position(3);

    assertEquals(-1, fd.read(buffer));
    assertEquals(3, buffer.position());
  }

  @Test
  void byteBufferWritePassesRemainingBytesAndAdvancesPosition() throws IOException {
    var fd = new FakeFd();
    java.nio.ByteBuffer buffer =
        java.nio.ByteBuffer.wrap(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9});
    buffer.position(2).limit(8);

    fd.write(buffer);

    assertArrayEquals(new byte[] {2, 3, 4, 5, 6, 7}, fd.written.toByteArray());
    assertEquals(8, buffer.position());
  }

  @Test
  void readAllBytesConcatenatesChunksUntilEndOfStream() throws IOException {
    var fd = new FakeFd();
    fd.toRead.add(new byte[] {1, 2, 3});
    fd.toRead.add(new byte[] {4});
    fd.toRead.add(new byte[] {5, 6});

    assertArrayEquals(new byte[] {1, 2, 3, 4, 5, 6}, fd.readAllBytes());
  }

  @Test
  void readAllBytesReturnsEmptyArrayOnImmediateEndOfStream() throws IOException {
    var fd = new FakeFd();

    assertArrayEquals(new byte[0], fd.readAllBytes());
  }

  @Test
  void operationsThrowAfterClose() {
    var fd = new FakeFd();
    fd.close();

    assertThrows(IllegalStateException.class, fd::value);
    assertThrows(IllegalStateException.class, fd::detach);
    assertThrows(IllegalStateException.class, fd::readAllBytes);
  }

  @Test
  void pipeCloseClosesBothEndsEvenIfReadEndFails() {
    var readEnd = new FakeFd() {
      @Override
      public void close() {
        super.close();
        throw new RuntimeException("read end close failed");
      }
    };
    var writeEnd = new FakeFd();
    var pipe = new UnixSocketProvider.Pipe() {
      @Override
      public Fd readEnd() {
        return readEnd;
      }

      @Override
      public Fd writeEnd() {
        return writeEnd;
      }
    };

    assertThrows(RuntimeException.class, pipe::close);

    assertFalse(readEnd.isOpen());
    assertFalse(writeEnd.isOpen());
  }
}
