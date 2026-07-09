package io.github.nhwalker.unixsocket.internal;

/** The CMSG_* macro arithmetic from {@code <sys/socket.h>}, for LP64 (8-byte cmsg alignment). */
final class Cmsg {

  private Cmsg() {}

  /** cmsghdr header size; also CMSG_DATA's offset within a cmsghdr. */
  static final long HEADER = 16;

  /** Control-buffer capacity for the kernel maximum of SCM_MAX_FD fds — truncation-proof. */
  static final long CONTROL_CAPACITY = space(Libc.SCM_MAX_FD * 4L);

  /** CMSG_ALIGN. */
  static long align(long length) {
    return (length + 7) & ~7L;
  }

  /** CMSG_SPACE: bytes a cmsg with {@code dataLength} bytes of payload occupies in a buffer. */
  static long space(long dataLength) {
    return align(dataLength) + HEADER;
  }

  /** CMSG_LEN: the cmsg_len field value for {@code dataLength} bytes of payload. */
  static long len(long dataLength) {
    return HEADER + dataLength;
  }
}
