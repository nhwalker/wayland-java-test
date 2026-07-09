package io.github.nhwalker.unixsocket.internal;

import java.io.IOException;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;

/**
 * Downcall handles, struct offsets, and errno handling for the libc calls this implementation
 * needs. Constants and offsets are for Linux LP64 (x86_64 and aarch64 — verified identical on
 * both; note that {@code struct stat} differs between them except for {@code st_size} at
 * offset 48, which is the only field used).
 */
final class Libc {

  private Libc() {}

  // ---- constants (identical on linux x86_64 and aarch64) ----

  static final int AF_UNIX = 1;
  static final int SOCK_STREAM = 1;
  static final int SOCK_CLOEXEC = 0x80000;
  static final int SOL_SOCKET = 1;
  static final int SCM_RIGHTS = 1;
  static final int SO_TYPE = 3;
  static final int MSG_CMSG_CLOEXEC = 0x40000000;
  static final int MSG_NOSIGNAL = 0x4000;
  static final int MSG_CTRUNC = 0x8;
  static final int SHUT_RDWR = 2;
  static final int PROT_READ = 1;
  static final int PROT_WRITE = 2;
  static final int MAP_SHARED = 1;
  static final int MAP_PRIVATE = 2;
  static final int MFD_CLOEXEC = 1;
  static final int O_CLOEXEC = 0x80000;
  static final int F_SETFD = 2;
  static final int FD_CLOEXEC = 1;

  /** Kernel limit on SCM_RIGHTS fds per message (SCM_MAX_FD). */
  static final int SCM_MAX_FD = 253;

  static final int EINTR = 4;

  // ---- struct offsets/sizes (LP64) ----

  static final long SOCKADDR_UN_SIZE = 110;
  static final long SUN_PATH_OFFSET = 2;
  static final int SUN_PATH_MAX = 107;

  static final long MSGHDR_SIZE = 56;
  static final long MSG_NAME = 0;
  static final long MSG_NAMELEN = 8;
  static final long MSG_IOV = 16;
  static final long MSG_IOVLEN = 24;
  static final long MSG_CONTROL = 32;
  static final long MSG_CONTROLLEN = 40;
  static final long MSG_FLAGS = 48;

  static final long IOVEC_SIZE = 16;
  static final long IOV_BASE = 0;
  static final long IOV_LEN = 8;

  static final long STAT_SIZE = 144;
  static final long ST_SIZE_OFFSET = 48;

  // ---- linker plumbing ----

  private static final Linker LINKER = Linker.nativeLinker();
  private static final SymbolLookup LIBC = LINKER.defaultLookup();

  static final StructLayout CAPTURE_LAYOUT = Linker.Option.captureStateLayout();
  private static final VarHandle ERRNO =
      CAPTURE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("errno"));

  private static MemorySegment symbol(String name) {
    return LIBC.find(name).orElseThrow(() -> new UnsupportedOperationException(
        "libc symbol not found: " + name + " (glibc >= 2.33 required)"));
  }

  private static MethodHandle capturing(String name, FunctionDescriptor descriptor) {
    return LINKER.downcallHandle(symbol(name), descriptor, Linker.Option.captureCallState("errno"));
  }

  private static MethodHandle plain(String name, FunctionDescriptor descriptor) {
    return LINKER.downcallHandle(symbol(name), descriptor);
  }

  // Fallible calls capture errno (leading MemorySegment argument); calls whose failures are
  // ignored by design (close, shutdown, munmap, fcntl) do not.
  private static final MethodHandle SOCKET = capturing("socket",
      FunctionDescriptor.of(ValueLayout.JAVA_INT,
          ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
  private static final MethodHandle CONNECT = capturing("connect",
      FunctionDescriptor.of(ValueLayout.JAVA_INT,
          ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
  private static final MethodHandle SOCKETPAIR = capturing("socketpair",
      FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
          ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
  private static final MethodHandle SENDMSG = capturing("sendmsg",
      FunctionDescriptor.of(ValueLayout.JAVA_LONG,
          ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
  private static final MethodHandle RECVMSG = capturing("recvmsg",
      FunctionDescriptor.of(ValueLayout.JAVA_LONG,
          ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
  private static final MethodHandle READ = capturing("read",
      FunctionDescriptor.of(ValueLayout.JAVA_LONG,
          ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
  private static final MethodHandle WRITE = capturing("write",
      FunctionDescriptor.of(ValueLayout.JAVA_LONG,
          ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
  private static final MethodHandle MMAP = capturing("mmap",
      FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
          ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
          ValueLayout.JAVA_LONG));
  private static final MethodHandle FTRUNCATE = capturing("ftruncate",
      FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
  private static final MethodHandle FSTAT = capturing("fstat",
      FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
  private static final MethodHandle MEMFD_CREATE = capturing("memfd_create",
      FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
  private static final MethodHandle PIPE2 = capturing("pipe2",
      FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
  private static final MethodHandle GETSOCKOPT = capturing("getsockopt",
      FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
          ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

  private static final MethodHandle CLOSE = plain("close",
      FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
  private static final MethodHandle SHUTDOWN = plain("shutdown",
      FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
  private static final MethodHandle MUNMAP = plain("munmap",
      FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
  private static final MethodHandle FCNTL = plain("fcntl",
      FunctionDescriptor.of(ValueLayout.JAVA_INT,
          ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
  private static final MethodHandle STRERROR = plain("strerror",
      FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

  // ---- errno ----

  static int errno(MemorySegment capture) {
    return (int) ERRNO.get(capture, 0L);
  }

  static String errnoName(int e) {
    return switch (e) {
      case 2 -> "ENOENT";
      case 4 -> "EINTR";
      case 9 -> "EBADF";
      case 11 -> "EAGAIN";
      case 12 -> "ENOMEM";
      case 13 -> "EACCES";
      case 14 -> "EFAULT";
      case 19 -> "ENODEV";
      case 22 -> "EINVAL";
      case 23 -> "ENFILE";
      case 24 -> "EMFILE";
      case 28 -> "ENOSPC";
      case 32 -> "EPIPE";
      case 36 -> "ENAMETOOLONG";
      case 88 -> "ENOTSOCK";
      case 91 -> "EPROTOTYPE";
      case 98 -> "EADDRINUSE";
      case 104 -> "ECONNRESET";
      case 106 -> "EISCONN";
      case 107 -> "ENOTCONN";
      case 111 -> "ECONNREFUSED";
      default -> "errno " + e;
    };
  }

  static String errnoMessage(int e) {
    try {
      MemorySegment s = (MemorySegment) STRERROR.invokeExact(e);
      return s.reinterpret(4096).getString(0);
    } catch (Throwable t) {
      throw new AssertionError(t);
    }
  }

  static IOException ioException(String syscall, int e) {
    return new IOException(syscall + " failed: " + errnoName(e) + " (" + errnoMessage(e) + ")");
  }

  // ---- fallible calls (negative/non-zero return means failure; read errno via capture) ----

  static int socket(MemorySegment capture, int domain, int type, int protocol) {
    try {
      return (int) SOCKET.invokeExact(capture, domain, type, protocol);
    } catch (Throwable t) {
      throw new AssertionError(t);
    }
  }

  static int connect(MemorySegment capture, int fd, MemorySegment addr, int addrLen) {
    try {
      return (int) CONNECT.invokeExact(capture, fd, addr, addrLen);
    } catch (Throwable t) {
      throw new AssertionError(t);
    }
  }

  static int socketpair(MemorySegment capture, int domain, int type, int protocol,
      MemorySegment sv) {
    try {
      return (int) SOCKETPAIR.invokeExact(capture, domain, type, protocol, sv);
    } catch (Throwable t) {
      throw new AssertionError(t);
    }
  }

  static long sendmsg(MemorySegment capture, int fd, MemorySegment msg, int flags) {
    try {
      return (long) SENDMSG.invokeExact(capture, fd, msg, flags);
    } catch (Throwable t) {
      throw new AssertionError(t);
    }
  }

  static long recvmsg(MemorySegment capture, int fd, MemorySegment msg, int flags) {
    try {
      return (long) RECVMSG.invokeExact(capture, fd, msg, flags);
    } catch (Throwable t) {
      throw new AssertionError(t);
    }
  }

  static long read(MemorySegment capture, int fd, MemorySegment buf, long len) {
    try {
      return (long) READ.invokeExact(capture, fd, buf, len);
    } catch (Throwable t) {
      throw new AssertionError(t);
    }
  }

  static long write(MemorySegment capture, int fd, MemorySegment buf, long len) {
    try {
      return (long) WRITE.invokeExact(capture, fd, buf, len);
    } catch (Throwable t) {
      throw new AssertionError(t);
    }
  }

  static MemorySegment mmap(MemorySegment capture, MemorySegment addr, long len, int prot,
      int flags, int fd, long offset) {
    try {
      return (MemorySegment) MMAP.invokeExact(capture, addr, len, prot, flags, fd, offset);
    } catch (Throwable t) {
      throw new AssertionError(t);
    }
  }

  static int ftruncate(MemorySegment capture, int fd, long size) {
    try {
      return (int) FTRUNCATE.invokeExact(capture, fd, size);
    } catch (Throwable t) {
      throw new AssertionError(t);
    }
  }

  static int fstat(MemorySegment capture, int fd, MemorySegment statBuf) {
    try {
      return (int) FSTAT.invokeExact(capture, fd, statBuf);
    } catch (Throwable t) {
      throw new AssertionError(t);
    }
  }

  static int memfdCreate(MemorySegment capture, MemorySegment name, int flags) {
    try {
      return (int) MEMFD_CREATE.invokeExact(capture, name, flags);
    } catch (Throwable t) {
      throw new AssertionError(t);
    }
  }

  static int pipe2(MemorySegment capture, MemorySegment fds, int flags) {
    try {
      return (int) PIPE2.invokeExact(capture, fds, flags);
    } catch (Throwable t) {
      throw new AssertionError(t);
    }
  }

  static int getsockopt(MemorySegment capture, int fd, int level, int option,
      MemorySegment value, MemorySegment valueLen) {
    try {
      return (int) GETSOCKOPT.invokeExact(capture, fd, level, option, value, valueLen);
    } catch (Throwable t) {
      throw new AssertionError(t);
    }
  }

  // ---- calls whose failures are ignored by design ----

  static void closeQuietly(int fd) {
    try {
      int ignored = (int) CLOSE.invokeExact(fd);
    } catch (Throwable t) {
      throw new AssertionError(t);
    }
  }

  static void shutdownQuietly(int fd, int how) {
    try {
      int ignored = (int) SHUTDOWN.invokeExact(fd, how);
    } catch (Throwable t) {
      throw new AssertionError(t);
    }
  }

  static void munmapQuietly(long address, long size) {
    try {
      int ignored = (int) MUNMAP.invokeExact(MemorySegment.ofAddress(address), size);
    } catch (Throwable t) {
      throw new AssertionError(t);
    }
  }

  static void setCloexec(int fd) {
    try {
      int ignored = (int) FCNTL.invokeExact(fd, F_SETFD, FD_CLOEXEC);
    } catch (Throwable t) {
      throw new AssertionError(t);
    }
  }
}
