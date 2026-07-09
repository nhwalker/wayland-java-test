package io.github.nhwalker.unixsocket;

/**
 * An owning handle to a POSIX file descriptor.
 *
 * <p>Each handle has exactly one owner at a time. Handles returned by
 * {@link ReceiveResult#fds()} are owned by the caller and must be {@link #close() closed} (or
 * {@link #detach() detached}) or the underlying descriptor leaks. Sending a handle over a
 * {@link UnixSocketChannel} does <em>not</em> transfer or invalidate it: the kernel installs an
 * independent duplicate of the descriptor in the receiving process, and the sender retains
 * ownership of its handle.
 *
 * <p>{@code close()} and {@code detach()} are thread-safe with respect to each other — the first
 * call wins and later calls are no-ops (for {@code close()}) or fail (for {@code detach()}). Any
 * other use of the handle after either has been called is an error.
 */
public interface Fd extends AutoCloseable {

  /**
   * Returns the raw integer file descriptor, for example to pass to FFM downcalls such as
   * {@code mmap(2)}. The handle keeps ownership; the returned value must not be closed by the
   * caller and is only valid until this handle is closed or detached.
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
}
