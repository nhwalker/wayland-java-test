package io.github.nhwalker.wayland.core;

/**
 * Base unchecked exception for protocol-level failures: marshal validation, since-version
 * violations, bad opcodes, and fatal server errors ({@link WaylandProtocolException}).
 */
public class WaylandException extends RuntimeException {

  public WaylandException(String message) {
    super(message);
  }

  public WaylandException(String message, Throwable cause) {
    super(message, cause);
  }
}
