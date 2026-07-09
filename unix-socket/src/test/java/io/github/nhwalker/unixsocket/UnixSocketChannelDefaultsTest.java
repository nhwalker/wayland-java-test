package io.github.nhwalker.unixsocket;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;

class UnixSocketChannelDefaultsTest {

  @Test
  void byteBufferSendPassesRemainingBytesAndAdvancesPosition() throws IOException {
    var channel = new FakeUnixSocketChannel();
    ByteBuffer buffer = ByteBuffer.wrap(new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9});
    buffer.position(2).limit(8);

    channel.send(buffer);

    assertArrayEquals(new byte[] {2, 3, 4, 5, 6, 7}, channel.sentBytes);
    assertEquals(8, buffer.position());
    assertSame(List.of(), channel.sentFds);
  }

  @Test
  void byteBufferSendForwardsFds() throws IOException {
    var channel = new FakeUnixSocketChannel();
    List<Fd> fds = List.of(new FakeFd());

    channel.send(ByteBuffer.wrap(new byte[] {42}), fds);

    assertSame(fds, channel.sentFds);
  }

  @Test
  void byteBufferReceiveFillsFromPositionAndAdvances() throws IOException {
    var channel = new FakeUnixSocketChannel();
    channel.bytesToReceive = 4;
    ByteBuffer buffer = ByteBuffer.allocate(10);
    buffer.position(3);

    ReceiveResult result = channel.receive(buffer);

    assertEquals(4, result.bytesReceived());
    assertFalse(result.endOfStream());
    assertEquals(7, buffer.position());
    assertArrayEquals(new byte[] {0, 0, 0, 1, 2, 3, 4, 0, 0, 0}, buffer.array());
  }

  @Test
  void byteBufferReceiveDoesNotAdvanceOnEndOfStream() throws IOException {
    var channel = new FakeUnixSocketChannel();
    channel.bytesToReceive = -1;
    ByteBuffer buffer = ByteBuffer.allocate(10);
    buffer.position(3);

    ReceiveResult result = channel.receive(buffer);

    assertEquals(-1, result.bytesReceived());
    assertTrue(result.endOfStream());
    assertEquals(3, buffer.position());
  }

  @Test
  void memorySegmentSendDefaultUsesEmptyFdList() throws IOException {
    var channel = new FakeUnixSocketChannel();

    channel.send(java.lang.foreign.MemorySegment.ofArray(new byte[] {7, 8}));

    assertArrayEquals(new byte[] {7, 8}, channel.sentBytes);
    assertSame(List.of(), channel.sentFds);
  }
}
