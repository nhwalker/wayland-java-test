/**
 * Unix domain socket send/receive API with ancillary-data support.
 *
 * <p>Built on the Foreign Function &amp; Memory API to expose {@code sendmsg(2)} /
 * {@code recvmsg(2)} with {@code SCM_RIGHTS}, enabling file-descriptor passing that the
 * JDK's built-in Unix domain socket channels do not support.
 */
package io.github.nhwalker.unixsocket;
