/**
 * Unix domain socket send/receive API with ancillary-data support.
 *
 * <p>Exposes {@code sendmsg(2)}/{@code recvmsg(2)} with {@code SCM_RIGHTS}, enabling
 * file-descriptor passing that the JDK's built-in Unix domain socket channels do not support.
 * The API consists entirely of interfaces; implementations (the reference one is built on the
 * Foreign Function &amp; Memory API) are discovered through
 * {@link io.github.nhwalker.unixsocket.UnixSocketProvider#provider()}.
 *
 * <p>The central contracts, in brief:
 *
 * <ul>
 *   <li><b>Ownership.</b> {@link io.github.nhwalker.unixsocket.Fd} is an owning handle with
 *       exactly one owner at a time. Received descriptors belong to the caller and must be
 *       closed. Sending never consumes the sender's handle — the kernel duplicates the
 *       descriptor into the receiver.
 *   <li><b>Blocking.</b> All operations block. Sends have write-fully semantics; receives
 *       return once at least one byte (or end-of-stream) is available.
 *   <li><b>CLOEXEC.</b> Every descriptor created or received by an implementation has
 *       {@code FD_CLOEXEC} set. This is a guarantee, not an option.
 *   <li><b>Buffers.</b> {@link java.lang.foreign.MemorySegment} is the primitive data carrier;
 *       {@link java.nio.ByteBuffer} overloads are provided as conveniences.
 * </ul>
 */
package io.github.nhwalker.unixsocket;
