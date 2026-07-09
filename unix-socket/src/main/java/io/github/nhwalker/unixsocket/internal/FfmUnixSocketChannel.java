package io.github.nhwalker.unixsocket.internal;

import io.github.nhwalker.unixsocket.Fd;
import io.github.nhwalker.unixsocket.ReceiveResult;
import io.github.nhwalker.unixsocket.UnixSocketChannel;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link UnixSocketChannel} over sendmsg(2)/recvmsg(2).
 *
 * <p>{@code close()} shuts the socket down before closing so operations blocked in other
 * threads return promptly (they then throw {@link AsynchronousCloseException}). Between the
 * shutdown and a blocked thread actually leaving its syscall, the closed descriptor number can
 * be recycled by another thread of the process — an inherent race this implementation accepts,
 * as libwayland does; avoiding it would require NIO-grade descriptor interlocking.
 */
final class FfmUnixSocketChannel implements UnixSocketChannel {

  private final int fd;
  private final AtomicBoolean open = new AtomicBoolean(true);

  FfmUnixSocketChannel(int fd) {
    this.fd = fd;
  }

  private record Result(int bytesReceived, List<Fd> fds) implements ReceiveResult {}

  @Override
  public boolean isOpen() {
    return open.get();
  }

  @Override
  public void close() {
    if (open.compareAndSet(true, false)) {
      Libc.shutdownQuietly(fd, Libc.SHUT_RDWR);
      Libc.closeQuietly(fd);
    }
  }

  @Override
  public void send(MemorySegment data, List<Fd> fds) throws IOException {
    if (fds.size() > Libc.SCM_MAX_FD) {
      throw new IllegalArgumentException(
          "at most " + Libc.SCM_MAX_FD + " fds per message, got " + fds.size());
    }
    long length = data.byteSize();
    if (!fds.isEmpty() && length == 0) {
      throw new IllegalArgumentException("SCM_RIGHTS requires at least one byte of data");
    }
    if (!open.get()) {
      throw new ClosedChannelException();
    }
    if (length == 0) {
      return;
    }
    // Snapshot raw fds first: a closed handle throws before any bytes go out.
    int[] rawFds = new int[fds.size()];
    for (int i = 0; i < rawFds.length; i++) {
      rawFds[i] = fds.get(i).value();
    }

    try (Arena arena = Arena.ofConfined()) {
      MemorySegment capture = arena.allocate(Libc.CAPTURE_LAYOUT);
      MemorySegment scratch;
      if (data.isNative()) {
        scratch = data;
      } else {
        scratch = arena.allocate(length);
        scratch.copyFrom(data);
      }
      MemorySegment iovec = arena.allocate(Libc.IOVEC_SIZE, 8);
      MemorySegment msghdr = arena.allocate(Libc.MSGHDR_SIZE, 8);
      msghdr.set(ValueLayout.ADDRESS, Libc.MSG_IOV, iovec);
      msghdr.set(ValueLayout.JAVA_LONG, Libc.MSG_IOVLEN, 1);

      MemorySegment control = MemorySegment.NULL;
      long controlLength = 0;
      if (rawFds.length > 0) {
        controlLength = Cmsg.space(rawFds.length * 4L);
        control = arena.allocate(controlLength, 8);
        control.set(ValueLayout.JAVA_LONG, 0, Cmsg.len(rawFds.length * 4L));
        control.set(ValueLayout.JAVA_INT, 8, Libc.SOL_SOCKET);
        control.set(ValueLayout.JAVA_INT, 12, Libc.SCM_RIGHTS);
        for (int i = 0; i < rawFds.length; i++) {
          control.set(ValueLayout.JAVA_INT, Cmsg.HEADER + 4L * i, rawFds[i]);
        }
      }

      boolean attachFds = rawFds.length > 0;
      long sent = 0;
      while (sent < length) {
        iovec.set(ValueLayout.ADDRESS, Libc.IOV_BASE, scratch.asSlice(sent));
        iovec.set(ValueLayout.JAVA_LONG, Libc.IOV_LEN, length - sent);
        msghdr.set(ValueLayout.ADDRESS, Libc.MSG_CONTROL,
            attachFds ? control : MemorySegment.NULL);
        msghdr.set(ValueLayout.JAVA_LONG, Libc.MSG_CONTROLLEN, attachFds ? controlLength : 0);
        long n = Libc.sendmsg(capture, fd, msghdr, Libc.MSG_NOSIGNAL);
        if (n < 0) {
          int e = Libc.errno(capture);
          if (e == Libc.EINTR) {
            continue; // nothing consumed; the SCM_RIGHTS payload stays attached for the retry
          }
          if (!open.get()) {
            throw new AsynchronousCloseException();
          }
          throw Libc.ioException("sendmsg", e);
        }
        sent += n;
        if (n > 0) {
          attachFds = false; // SCM_RIGHTS delivered exactly once, with the first fragment
        }
      }
    }
  }

  @Override
  public ReceiveResult receive(MemorySegment dst) throws IOException {
    if (dst.byteSize() == 0) {
      throw new IllegalArgumentException("dst must not be zero-length");
    }
    if (dst.isReadOnly()) {
      throw new IllegalArgumentException("dst must not be read-only");
    }
    if (!open.get()) {
      throw new ClosedChannelException();
    }
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment capture = arena.allocate(Libc.CAPTURE_LAYOUT);
      MemorySegment scratch = dst.isNative() ? dst : arena.allocate(dst.byteSize());
      MemorySegment control = arena.allocate(Cmsg.CONTROL_CAPACITY, 8);
      MemorySegment iovec = arena.allocate(Libc.IOVEC_SIZE, 8);
      iovec.set(ValueLayout.ADDRESS, Libc.IOV_BASE, scratch);
      iovec.set(ValueLayout.JAVA_LONG, Libc.IOV_LEN, scratch.byteSize());
      MemorySegment msghdr = arena.allocate(Libc.MSGHDR_SIZE, 8);
      msghdr.set(ValueLayout.ADDRESS, Libc.MSG_IOV, iovec);
      msghdr.set(ValueLayout.JAVA_LONG, Libc.MSG_IOVLEN, 1);
      msghdr.set(ValueLayout.ADDRESS, Libc.MSG_CONTROL, control);
      msghdr.set(ValueLayout.JAVA_LONG, Libc.MSG_CONTROLLEN, Cmsg.CONTROL_CAPACITY);

      long n;
      while (true) {
        n = Libc.recvmsg(capture, fd, msghdr, Libc.MSG_CMSG_CLOEXEC);
        if (n >= 0) {
          break;
        }
        int e = Libc.errno(capture);
        if (e == Libc.EINTR) {
          continue;
        }
        if (!open.get()) {
          throw new AsynchronousCloseException();
        }
        throw Libc.ioException("recvmsg", e);
      }
      if (n == 0) {
        if (!open.get()) {
          throw new AsynchronousCloseException(); // woken by our own close, not a real EOF
        }
        return new Result(-1, List.of());
      }

      // Wrap harvested fds as owning handles immediately so error paths can close them.
      List<Fd> received = new ArrayList<>();
      long controlUsed = msghdr.get(ValueLayout.JAVA_LONG, Libc.MSG_CONTROLLEN);
      long offset = 0;
      while (offset + Cmsg.HEADER <= controlUsed) {
        long cmsgLen = control.get(ValueLayout.JAVA_LONG, offset);
        int level = control.get(ValueLayout.JAVA_INT, offset + 8);
        int type = control.get(ValueLayout.JAVA_INT, offset + 12);
        if (cmsgLen < Cmsg.HEADER || offset + cmsgLen > controlUsed) {
          break; // malformed control data; stop parsing rather than read out of bounds
        }
        if (level == Libc.SOL_SOCKET && type == Libc.SCM_RIGHTS) {
          int count = (int) ((cmsgLen - Cmsg.HEADER) / 4);
          for (int i = 0; i < count; i++) {
            received.add(new FfmFd(control.get(ValueLayout.JAVA_INT, offset + Cmsg.HEADER + 4L * i)));
          }
        }
        offset += Cmsg.align(cmsgLen);
      }

      int flags = msghdr.get(ValueLayout.JAVA_INT, Libc.MSG_FLAGS);
      if ((flags & Libc.MSG_CTRUNC) != 0) {
        // Cannot happen with a CONTROL_CAPACITY buffer; if it somehow does, don't leak fds.
        received.forEach(Fd::close);
        throw new IOException("recvmsg: ancillary data truncated (MSG_CTRUNC)");
      }

      if (scratch != dst) {
        MemorySegment.copy(scratch, 0, dst, 0, n);
      }
      return new Result((int) n, List.copyOf(received));
    }
  }
}
