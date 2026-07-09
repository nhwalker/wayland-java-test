package io.github.nhwalker.unixsocket;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.util.List;

/**
 * A connected, blocking, stream-mode ({@code SOCK_STREAM}) Unix domain socket supporting
 * {@code sendmsg(2)}/{@code recvmsg(2)} with {@code SCM_RIGHTS} file-descriptor passing.
 *
 * <p>This is deliberately not a {@link java.nio.channels.ReadableByteChannel} or
 * {@link java.nio.channels.WritableByteChannel}: a caller using those generic contracts would
 * silently leak any file descriptors arriving alongside the bytes, and would be entitled to
 * short-write semantics this channel does not have. The distinct {@code send}/{@code receive}
 * names reflect that these are {@code sendmsg}/{@code recvmsg}, not NIO read/write.
 *
 * <p><b>Thread safety:</b> the channel is full-duplex — one thread may send while another
 * receives. Concurrent sends (or concurrent receives) require external synchronization.
 * Blocking calls are not responsive to {@link Thread#interrupt()}; however, {@link #close()}
 * from another thread causes operations blocked on this channel to fail promptly with an
 * {@link IOException}.
 */
public interface UnixSocketChannel extends Channel {

  /**
   * Sends all {@code data.byteSize()} bytes as one logical operation, attaching {@code fds} as
   * {@code SCM_RIGHTS} ancillary data. Write-fully semantics: if the kernel accepts only part of
   * the data, the call blocks and retries until every byte is queued; the file descriptors are
   * attached to the first fragment exactly once.
   *
   * <p><b>Ownership:</b> the caller retains its {@link Fd} handles — the kernel gives the
   * receiver independent duplicates. Close your handle whenever you are done with it, regardless
   * of the send.
   *
   * @param data the bytes to send; may be heap- or native-backed
   * @param fds file descriptors to pass, or an empty list
   * @throws IllegalArgumentException if {@code fds} is non-empty and {@code data} is empty
   *     ({@code SCM_RIGHTS} requires at least one byte of data), or if {@code fds} exceeds the
   *     kernel per-message limit (253 on Linux)
   * @throws java.nio.channels.ClosedChannelException if the channel is closed
   * @throws IOException on socket errors ({@code EPIPE}, {@code ECONNRESET}, ...)
   */
  void send(MemorySegment data, List<Fd> fds) throws IOException;

  /** Sends bytes with no ancillary data. See {@link #send(MemorySegment, List)}. */
  default void send(MemorySegment data) throws IOException {
    send(data, List.of());
  }

  /**
   * {@link ByteBuffer} convenience for {@link #send(MemorySegment, List)}: sends the buffer's
   * remaining bytes (position to limit) and advances the position to the limit on success. The
   * buffer must not be a read-only heap buffer.
   */
  default void send(ByteBuffer data, List<Fd> fds) throws IOException {
    send(MemorySegment.ofBuffer(data), fds);
    data.position(data.limit());
  }

  /** {@link ByteBuffer} convenience with no ancillary data. See {@link #send(ByteBuffer, List)}. */
  default void send(ByteBuffer data) throws IOException {
    send(data, List.of());
  }

  /**
   * Blocks until at least one byte (or end-of-stream) is available, reads up to
   * {@code dst.byteSize()} bytes into {@code dst} starting at offset 0, and harvests any file
   * descriptors that arrived with them.
   *
   * <p>Received descriptors always have {@code FD_CLOEXEC} set ({@code MSG_CMSG_CLOEXEC}).
   * Implementations size the control buffer for the kernel maximum (253 file descriptors on
   * Linux), so ancillary data is never truncated and no per-call limit is needed.
   *
   * @param dst the writable destination; must not be zero-length
   * @return the byte count and any received file descriptors; never {@code null}
   * @throws IllegalArgumentException if {@code dst} is zero-length or read-only
   * @throws java.nio.channels.ClosedChannelException if the channel is closed
   * @throws IOException on socket errors
   */
  ReceiveResult receive(MemorySegment dst) throws IOException;

  /**
   * {@link ByteBuffer} convenience for {@link #receive(MemorySegment)}: reads into the buffer's
   * remaining space (position to limit) and advances the position by
   * {@link ReceiveResult#bytesReceived()} when positive. The buffer must not be read-only and
   * must have remaining space.
   */
  default ReceiveResult receive(ByteBuffer dst) throws IOException {
    ReceiveResult result = receive(MemorySegment.ofBuffer(dst));
    int n = result.bytesReceived();
    if (n > 0) {
      dst.position(dst.position() + n);
    }
    return result;
  }

  /**
   * Closes the channel and its underlying socket. Idempotent. Send or receive calls blocked on
   * this channel in other threads fail promptly with an {@link IOException}.
   */
  @Override
  void close() throws IOException;
}
