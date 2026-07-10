package io.github.nhwalker.wayland.core;

/**
 * The Wayland wire argument types and their Java carriers.
 *
 * <p>The carrier is the Java type an argument has in a {@link Proxy#marshal marshal} call and
 * in the decoded {@code Object[]} handed to a {@link Proxy.EventSink}:
 *
 * <table class="striped">
 *   <caption>Carrier types</caption>
 *   <tr><th>ArgType</th><th>Java carrier</th></tr>
 *   <tr><td>{@link #INT}, {@link #UINT}</td>
 *       <td>{@code int} (UINT is an unsigned 32-bit value in two's-complement)</td></tr>
 *   <tr><td>{@link #FIXED}</td><td>{@link Fixed}</td></tr>
 *   <tr><td>{@link #STRING}</td><td>{@link String} ({@code null} only if the arg is nullable)</td></tr>
 *   <tr><td>{@link #OBJECT}</td><td>{@link Proxy} ({@code null} if nullable, or if the
 *       referenced id is no longer live at event-decode time)</td></tr>
 *   <tr><td>{@link #NEW_ID}</td><td>never present in marshal args — the kernel allocates and
 *       inserts the id; in events it decodes to a fresh {@link Proxy} built from the arg's
 *       interface reference</td></tr>
 *   <tr><td>{@link #ARRAY}</td><td>{@link WlArray}</td></tr>
 *   <tr><td>{@link #FD}</td><td>{@link io.github.nhwalker.unixsocket.Fd}</td></tr>
 * </table>
 */
public enum ArgType {
  INT,
  UINT,
  FIXED,
  STRING,
  OBJECT,
  NEW_ID,
  ARRAY,
  FD
}
