package io.github.nhwalker.unixsocket.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.nhwalker.unixsocket.Fd;
import io.github.nhwalker.unixsocket.ReceiveResult;
import io.github.nhwalker.unixsocket.UnixSocketChannel;
import io.github.nhwalker.unixsocket.UnixSocketProvider;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Integration tests for the provider factories. */
@Timeout(10)
class FfmProviderTest {

  private final FfmUnixSocketProvider provider = new FfmUnixSocketProvider();

  @TempDir
  Path tempDir;

  @Test
  void connectToJdkEchoServer() throws Exception {
    Path socketPath = tempDir.resolve("s");
    assumeTrue(socketPath.toString().getBytes(StandardCharsets.UTF_8).length <= 107,
        "temp dir path too long for sun_path");
    byte[] payload = "ping".getBytes(StandardCharsets.UTF_8);
    AtomicReference<Throwable> echoError = new AtomicReference<>();
    try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
      server.bind(UnixDomainSocketAddress.of(socketPath));
      Thread echo = Thread.ofPlatform().daemon().start(() -> {
        try (SocketChannel client = server.accept()) {
          ByteBuffer buffer = ByteBuffer.allocate(64);
          client.read(buffer);
          buffer.flip();
          client.write(buffer);
        } catch (Throwable t) {
          echoError.set(t);
          // Tear the listener down so a client blocked in receive unblocks instead of hanging.
          try {
            server.close();
          } catch (IOException ignored) {
            // best-effort unblock
          }
        }
      });
      try (UnixSocketChannel channel = provider.connect(socketPath);
          Arena arena = Arena.ofConfined()) {
        channel.send(MemorySegment.ofArray(payload));
        MemorySegment dst = arena.allocate(16);
        ReceiveResult result = channel.receive(dst);
        assertEquals(payload.length, result.bytesReceived());
        assertArrayEquals(payload,
            dst.asSlice(0, payload.length).toArray(ValueLayout.JAVA_BYTE));
      }
      echo.join(5_000);
      assertFalse(echo.isAlive());
      assertNull(echoError.get());
    }
  }

  @Test
  void connectFailureNamesErrno() {
    IOException e = assertThrows(IOException.class,
        () -> provider.connect(tempDir.resolve("does-not-exist")));
    assertTrue(e.getMessage().contains("ENOENT"), e.getMessage());
  }

  @Test
  void connectRejectsOverlongPath() {
    Path longPath = Path.of("/tmp", "x".repeat(120));
    IOException e = assertThrows(IOException.class, () -> provider.connect(longPath));
    assertTrue(e.getMessage().contains("107"), e.getMessage());
  }

  @Test
  void adoptRejectsNonSocket() throws Exception {
    try (var pipe = provider.pipe()) {
      assertThrows(IOException.class, () -> provider.adopt(pipe.readEnd()));
      assertTrue(pipe.readEnd().isOpen(), "failed adopt must not take ownership");
    }
  }

  @Test
  void adoptDetachedSocketRoundTrip() throws Throwable {
    int[] sv = rawSocketpair();
    Fd first = provider.adoptFd(sv[0]);
    Fd second = provider.adoptFd(sv[1]);
    try (UnixSocketChannel a = provider.adopt(first);
        UnixSocketChannel b = provider.adopt(second);
        Arena arena = Arena.ofConfined()) {
      assertFalse(first.isOpen(), "adopt must detach the source handle");
      assertFalse(second.isOpen());

      a.send(MemorySegment.ofArray(new byte[] {7}));
      ReceiveResult result = b.receive(arena.allocate(4));
      assertEquals(1, result.bytesReceived());
    }
  }

  @Test
  void sharedMemoryRejectsNonPositiveSize() {
    assertThrows(IllegalArgumentException.class, () -> provider.sharedMemory(0));
    assertThrows(IllegalArgumentException.class, () -> provider.sharedMemory(-1));
  }

  @Test
  void serviceLoaderFindsFfmProvider() {
    boolean found = ServiceLoader.load(UnixSocketProvider.class).stream()
        .anyMatch(p -> p.type() == FfmUnixSocketProvider.class);
    assertTrue(found);
  }

  /** Test-local socketpair(2) downcall — the only way to get raw connected socket fds. */
  private static int[] rawSocketpair() throws Throwable {
    Linker linker = Linker.nativeLinker();
    MethodHandle socketpair = linker.downcallHandle(
        linker.defaultLookup().find("socketpair").orElseThrow(),
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment sv = arena.allocate(8, 4);
      int result = (int) socketpair.invokeExact(1 /* AF_UNIX */, 1 /* SOCK_STREAM */, 0, sv);
      assertEquals(0, result);
      return new int[] {sv.get(ValueLayout.JAVA_INT, 0), sv.get(ValueLayout.JAVA_INT, 4)};
    }
  }
}
