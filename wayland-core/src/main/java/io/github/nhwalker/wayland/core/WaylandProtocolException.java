package io.github.nhwalker.wayland.core;

/**
 * A fatal {@code wl_display.error} sent by the server. Once raised via
 * {@link WaylandConnection#fatalError}, the connection is dead and every further operation
 * rethrows this.
 */
public final class WaylandProtocolException extends WaylandException {

  private final int objectId;
  private final String interfaceName;
  private final int code;

  public WaylandProtocolException(int objectId, String interfaceName, int code, String message) {
    super("protocol error on " + interfaceName + "@" + objectId + " (code " + code + "): "
        + message);
    this.objectId = objectId;
    this.interfaceName = interfaceName;
    this.code = code;
  }

  public int objectId() {
    return objectId;
  }

  public String interfaceName() {
    return interfaceName;
  }

  public int code() {
    return code;
  }
}
