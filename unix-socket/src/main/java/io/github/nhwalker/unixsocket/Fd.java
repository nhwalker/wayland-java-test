package io.github.nhwalker.unixsocket;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

/**
 * An owning handle to a POSIX file descriptor, with the I/O operations the JDK does not expose
 * for raw descriptors: memory-mapping, blocking stream reads/writes, and sizing.
 *
 * <p>Each handle has exactly one owner at a time. Handles returned by
 * {@link ReceiveResult#fds()} are owned by the caller and must be {@link #close() closed} (or
 * {@link #detach() detached}) or the underlying descriptor leaks. Sending a handle over a
 * {@link UnixSocketChannel} does <em>not</em> transfer or invalidate it: the kernel installs an
 * independent duplicate of the descriptor in the receiving process, and the sender retains
 * ownership of its handle.
 *
 * <p>Not every operation suits every descriptor — the protocol context that delivered a
 * descriptor tells you what it is. Mapping suits shared-memory descriptors (keymaps, shm pools);
 * {@code read}/{@code write} suit pipe ends; an operation the descriptor cannot support fails
 * with {@link IOException} exactly as the underlying syscall does.
 *
 * <p>{@code close()} and {@code detach()} are thread-safe with respect to each other — the first
 * call wins and later calls are no-ops (for {@code close()}) or fail (for {@code detach()}).
 * Every other method throws {@link IllegalStateException} once the handle has been closed or
 * detached.
 */
public interface Fd extends AutoCloseable {

  /**
   * Returns the raw integer file descriptor, for example to pass to the caller's own FFM
   * downcalls. The handle keeps ownership; the returned value must not be closed by the caller
   * and is only valid until this handle is closed or detached.
   *
   * @throws IllegalStateException if this handle has been closed or detached
   */
  int value();

  /** Returns {@code true} until {@link #close()} or {@link #detach()} has been called. */
  boolean isOpen();

  /**
   * Releases ownership of the descriptor <em>without</em> closing it: returns the raw file
   * descriptor and marks this handle inert. Use this when handing lifetime control to code that
   * is not {@code Fd}-aware, so the descriptor cannot be closed twice.
   *
   * @return the raw file descriptor, now owned by the caller
   * @throws IllegalStateException if this handle has already been closed or detached
   */
  int detach();

  /**
   * Closes the underlying file descriptor. Idempotent, and never throws: a {@code close(2)}
   * failure on a valid descriptor is unactionable, and a throwing close would poison
   * try-with-resources blocks. Implementations may log such failures.
   */
  @Override
  void close();

  /**
   * Memory-maps a region of the descriptor via {@code mmap(2)}, mirroring
   * {@link java.nio.channels.FileChannel#map(java.nio.channels.FileChannel.MapMode, long, long,
   * Arena) FileChannel.map}: the returned segment is valid until {@code arena} is closed, at
   * which point the region is unmapped.
   *
   * <p>The mapping's lifetime is the arena's, independent of this handle — closing the handle
   * does not unmap (POSIX: {@code close(2)} does not affect existing mappings).
   *
   * @param mode the protection and visibility of the mapping
   * @param offset the file offset at which the mapping starts; must be a non-negative multiple
   *     of the system page size (in practice, {@code 0})
   * @param size the number of bytes to map; must be positive
   * @param arena the arena that scopes the mapping's lifetime
   * @return a segment of exactly {@code size} bytes over the mapped region
   * @throws IllegalArgumentException if {@code offset} or {@code size} is out of range
   * @throws IllegalStateException if this handle has been closed or detached
   * @throws IOException if {@code mmap(2)} fails ({@code EACCES}, {@code ENODEV}, ...)
   */
  MemorySegment map(MapMode mode, long offset, long size, Arena arena) throws IOException;

  /**
   * Blocks until at least one byte (or end-of-stream) is available and reads up to
   * {@code dst.byteSize()} bytes into {@code dst} starting at offset 0, as by {@code read(2)}.
   *
   * @param dst the writable destination; must not be zero-length
   * @return the number of bytes read, or {@code -1} at end-of-stream; never {@code 0}
   * @throws IllegalArgumentException if {@code dst} is zero-length or read-only
   * @throws IllegalStateException if this handle has been closed or detached
   * @throws IOException on I/O errors
   */
  int read(MemorySegment dst) throws IOException;

  /**
   * {@link ByteBuffer} convenience for {@link #read(MemorySegment)}: reads into the buffer's
   * remaining space (position to limit) and advances the position by the count when positive.
   * The buffer must not be read-only and must have remaining space.
   */
  default int read(ByteBuffer dst) throws IOException {
    int n = read(MemorySegment.ofBuffer(dst));
    if (n > 0) {
      dst.position(dst.position() + n);
    }
    return n;
  }

  /**
   * Reads this descriptor to end-of-stream and returns everything as one byte array — the
   * natural shape for bounded streams like a clipboard pipe. Blocks until the writer closes its
   * end. Not for descriptors that never reach end-of-stream.
   *
   * @throws IllegalStateException if this handle has been closed or detached
   * @throws IOException on I/O errors
   */
  default byte[] readAllBytes() throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] chunk = new byte[8192];
    MemorySegment segment = MemorySegment.ofArray(chunk);
    int n;
    while ((n = read(segment)) != -1) {
      out.write(chunk, 0, n);
    }
    return out.toByteArray();
  }

  /**
   * Writes all {@code src.byteSize()} bytes, as by {@code write(2)}. Write-fully semantics: if
   * the kernel accepts only part of the data, the call blocks and retries until every byte is
   * written — consistent with {@link UnixSocketChannel#send(MemorySegment, java.util.List)}.
   *
   * @throws IllegalStateException if this handle has been closed or detached
   * @throws IOException on I/O errors — notably {@code EPIPE} if the reading end of a pipe has
   *     been closed
   */
  void write(MemorySegment src) throws IOException;

  /**
   * {@link ByteBuffer} convenience for {@link #write(MemorySegment)}: writes the buffer's
   * remaining bytes (position to limit) and advances the position to the limit on success. The
   * buffer must not be a read-only heap buffer.
   */
  default void write(ByteBuffer src) throws IOException {
    write(MemorySegment.ofBuffer(src));
    src.position(src.limit());
  }

  /**
   * Returns the descriptor's current size in bytes, as by {@code fstat(2)} — useful for
   * validating a received shared-memory descriptor before mapping it.
   *
   * @throws IllegalStateException if this handle has been closed or detached
   * @throws IOException if {@code fstat(2)} fails
   */
  long size() throws IOException;

  /**
   * Sets the descriptor's size via {@code ftruncate(2)} — how a shared-memory pool grows (for
   * example for {@code wl_shm_pool.resize}). Existing mappings are unaffected; remap to see the
   * new region.
   *
   * @param size the new size in bytes; must be non-negative
   * @throws IllegalArgumentException if {@code size} is negative
   * @throws IllegalStateException if this handle has been closed or detached
   * @throws IOException if {@code ftruncate(2)} fails ({@code EINVAL} on pipes and sockets, ...)
   */
  void truncate(long size) throws IOException;

  /**
   * How {@link Fd#map Fd.map} maps a region, mirroring
   * {@link java.nio.channels.FileChannel.MapMode}.
   */
  enum MapMode {
    /** Shared read-only mapping ({@code PROT_READ}, {@code MAP_SHARED}). */
    READ_ONLY,

    /**
     * Shared read-write mapping ({@code PROT_READ | PROT_WRITE}, {@code MAP_SHARED}); writes are
     * visible to other processes mapping the same object — how shm-pool pixels reach a
     * compositor.
     */
    READ_WRITE,

    /**
     * Private copy-on-write mapping ({@code PROT_READ | PROT_WRITE}, {@code MAP_PRIVATE});
     * writes are not visible to other processes. This is what the Wayland keymap contract
     * requires receivers to use.
     */
    PRIVATE
  }
}
