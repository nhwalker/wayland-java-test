package io.github.nhwalker.unixsocket;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.List;

/**
 * Records what reaches the abstract {@code send}/{@code receive} methods so the default-method
 * overloads can be tested. On receive, writes the byte pattern 1, 2, 3, ... into the destination.
 */
class FakeUnixSocketChannel implements UnixSocketChannel {

  byte[] sentBytes;
  List<Fd> sentFds;
  int bytesToReceive;
  List<Fd> fdsToReceive = List.of();
  private boolean open = true;

  @Override
  public void send(MemorySegment data, List<Fd> fds) {
    sentBytes = data.toArray(ValueLayout.JAVA_BYTE);
    sentFds = fds;
  }

  @Override
  public ReceiveResult receive(MemorySegment dst) {
    int n = bytesToReceive;
    if (n > 0) {
      n = (int) Math.min(n, dst.byteSize());
      for (int i = 0; i < n; i++) {
        dst.set(ValueLayout.JAVA_BYTE, i, (byte) (i + 1));
      }
    }
    int bytesReceived = n;
    return new ReceiveResult() {
      @Override
      public int bytesReceived() {
        return bytesReceived;
      }

      @Override
      public List<Fd> fds() {
        return fdsToReceive;
      }
    };
  }

  @Override
  public boolean isOpen() {
    return open;
  }

  @Override
  public void close() {
    open = false;
  }
}
