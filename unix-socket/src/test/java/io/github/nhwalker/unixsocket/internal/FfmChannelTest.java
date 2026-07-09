package io.github.nhwalker.unixsocket.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nhwalker.unixsocket.Fd;
import io.github.nhwalker.unixsocket.ReceiveResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.channels.ClosedChannelException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Integration tests over real socketpair(2) channels. */
@Timeout(10)
class FfmChannelTest {

  private final FfmUnixSocketProvider provider = new FfmUnixSocketProvider();

  @Test
  void pairByteRoundTrip() throws Exception {
    byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
    try (var pair = provider.pair(); Arena arena = Arena.ofConfined()) {
      pair.first().send(MemorySegment.ofArray(payload));
      MemorySegment dst = arena.allocate(16);
      ReceiveResult result = pair.second().receive(dst);
      assertEquals(payload.length, result.bytesReceived());
      assertTrue(result.fds().isEmpty());
      assertArrayEquals(payload,
          dst.asSlice(0, payload.length).toArray(ValueLayout.JAVA_BYTE));
    }
  }

  @Test
  void fdPassingRoundTrip() throws Exception {
    byte[] content = {10, 20, 30, 40, 50};
    try (var pair = provider.pair();
        Fd shm = provider.sharedMemory(content);
        Arena arena = Arena.ofConfined()) {
      pair.first().send(MemorySegment.ofArray(new byte[] {1}), List.of(shm));

      ReceiveResult result = pair.second().receive(arena.allocate(4));
      assertEquals(1, result.bytesReceived());
      assertEquals(1, result.fds().size());
      try (Fd received = result.fds().get(0)) {
        assertEquals(content.length, received.size());
        MemorySegment mapped = received.map(Fd.MapMode.READ_ONLY, 0, content.length, arena);
        assertArrayEquals(content, mapped.toArray(ValueLayout.JAVA_BYTE));
      }
    }
  }

  @Test
  void receivedFdIsCloexec() throws Exception {
    try (var pair = provider.pair();
        Fd shm = provider.sharedMemory(16);
        Arena arena = Arena.ofConfined()) {
      pair.first().send(MemorySegment.ofArray(new byte[] {1}), List.of(shm));
      ReceiveResult result = pair.second().receive(arena.allocate(4));
      try (Fd received = result.fds().get(0)) {
        String fdinfo =
            Files.readString(Path.of("/proc/self/fdinfo/" + received.value()));
        String flagsLine = fdinfo.lines()
            .filter(line -> line.startsWith("flags:"))
            .findFirst()
            .orElseThrow();
        int flags = Integer.parseInt(flagsLine.substring("flags:".length()).trim(), 8);
        assertTrue((flags & Libc.O_CLOEXEC) != 0, "expected O_CLOEXEC in " + flagsLine);
      }
    }
  }

  @Test
  void bigSendWriteFully() throws Exception {
    int size = 1 << 20; // well beyond default socket buffers
    byte[] payload = new byte[size];
    for (int i = 0; i < size; i++) {
      payload[i] = (byte) (i * 31);
    }
    try (var pair = provider.pair()) {
      byte[] got = new byte[size];
      Thread reader = Thread.ofPlatform().start(() -> {
        try (Arena arena = Arena.ofConfined()) {
          MemorySegment dst = arena.allocate(64 * 1024);
          int offset = 0;
          while (offset < size) {
            ReceiveResult result;
            try {
              result = pair.second().receive(dst);
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            }
            if (result.endOfStream()) {
              break;
            }
            MemorySegment.copy(dst, 0, MemorySegment.ofArray(got), offset,
                result.bytesReceived());
            offset += result.bytesReceived();
          }
        }
      });
      pair.first().send(MemorySegment.ofArray(payload));
      reader.join();
      assertArrayEquals(payload, got);
    }
  }

  @Test
  void eofOnPeerClose() throws Exception {
    try (var pair = provider.pair(); Arena arena = Arena.ofConfined()) {
      pair.second().close();
      ReceiveResult result = pair.first().receive(arena.allocate(8));
      assertEquals(-1, result.bytesReceived());
      assertTrue(result.endOfStream());
      assertTrue(result.fds().isEmpty());
    }
  }

  @Test
  void epipeOnSendToClosedPeer() throws Exception {
    try (var pair = provider.pair()) {
      pair.second().close();
      MemorySegment chunk = MemorySegment.ofArray(new byte[4096]);
      IOException e = assertThrows(IOException.class, () -> {
        for (int i = 0; i < 1000; i++) {
          pair.first().send(chunk);
        }
      });
      assertTrue(e.getMessage().contains("EPIPE"), e.getMessage());
    }
  }

  @Test
  void closeUnblocksBlockedReceive() throws Exception {
    var pair = provider.pair();
    AtomicReference<Throwable> thrown = new AtomicReference<>();
    CountDownLatch entered = new CountDownLatch(1);
    Thread blocked = Thread.ofPlatform().start(() -> {
      try (Arena arena = Arena.ofConfined()) {
        entered.countDown();
        pair.first().receive(arena.allocate(8));
      } catch (Throwable t) {
        thrown.set(t);
      }
    });
    entered.await();
    Thread.sleep(100); // let the thread actually block in recvmsg
    pair.first().close();
    blocked.join(5000);
    assertFalse(blocked.isAlive());
    assertInstanceOf(IOException.class, thrown.get());
    pair.second().close();
  }

  @Test
  void operationsAfterCloseThrowClosedChannelException() throws Exception {
    var pair = provider.pair();
    pair.close();
    assertThrows(ClosedChannelException.class,
        () -> pair.first().send(MemorySegment.ofArray(new byte[] {1})));
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment dst = arena.allocate(4);
      assertThrows(ClosedChannelException.class, () -> pair.first().receive(dst));
    }
  }

  @Test
  void closeIsIdempotent() throws Exception {
    var pair = provider.pair();
    pair.first().close();
    pair.first().close();
    assertFalse(pair.first().isOpen());
    pair.second().close();
  }

  @Test
  void sendRejectsFdsWithEmptyData() throws Exception {
    try (var pair = provider.pair(); Fd shm = provider.sharedMemory(8)) {
      assertThrows(IllegalArgumentException.class,
          () -> pair.first().send(MemorySegment.ofArray(new byte[0]), List.of(shm)));
    }
  }

  @Test
  void sendRejectsMoreThan253Fds() throws Exception {
    try (var pair = provider.pair(); Fd shm = provider.sharedMemory(8)) {
      List<Fd> fds = Collections.nCopies(254, shm);
      assertThrows(IllegalArgumentException.class,
          () -> pair.first().send(MemorySegment.ofArray(new byte[] {1}), fds));
    }
  }

  @Test
  void receiveRejectsBadDst() throws Exception {
    try (var pair = provider.pair(); Arena arena = Arena.ofConfined()) {
      assertThrows(IllegalArgumentException.class,
          () -> pair.first().receive(arena.allocate(4).asSlice(0, 0)));
      assertThrows(IllegalArgumentException.class,
          () -> pair.first().receive(arena.allocate(4).asReadOnly()));
    }
  }

  @Test
  void emptySendIsNoOp() throws Exception {
    try (var pair = provider.pair()) {
      pair.first().send(MemorySegment.ofArray(new byte[0]));
    }
  }
}
