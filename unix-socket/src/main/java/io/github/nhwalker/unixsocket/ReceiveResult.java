package io.github.nhwalker.unixsocket;

import java.util.List;

/**
 * The outcome of a single {@link UnixSocketChannel#receive(java.lang.foreign.MemorySegment)
 * receive}: the byte count plus any file descriptors that arrived as {@code SCM_RIGHTS}
 * ancillary data with those bytes.
 */
public interface ReceiveResult {

  /**
   * Returns the number of bytes copied into the caller's buffer, or {@code -1} if the peer has
   * shut down the stream (end-of-stream). Never {@code 0}: a blocking receive returns only once
   * data or end-of-stream is available. End-of-stream never carries file descriptors.
   */
  int bytesReceived();

  /**
   * Returns the file descriptors received with these bytes, in arrival order — often empty. The
   * list is immutable. Ownership of every handle transfers to the caller, who must close each
   * one. All received descriptors have {@code FD_CLOEXEC} set.
   */
  List<Fd> fds();

  /** Returns {@code true} iff the peer has shut down the stream ({@code bytesReceived() == -1}). */
  default boolean endOfStream() {
    return bytesReceived() == -1;
  }
}
