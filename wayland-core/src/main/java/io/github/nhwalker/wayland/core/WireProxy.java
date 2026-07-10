package io.github.nhwalker.wayland.core;

import io.github.nhwalker.unixsocket.Fd;

/** The wire engine's {@link Proxy}: validates against the descriptor, then hands off encoding
 * and delivery to its {@link WireConnection}. */
final class WireProxy implements Proxy {

  private final WireConnection connection;
  private final InterfaceDesc interfaceDesc;
  private final int id;
  private final int version;
  private volatile boolean destroyed;
  private volatile EventSink sink;

  WireProxy(WireConnection connection, InterfaceDesc interfaceDesc, int id, int version) {
    this.connection = connection;
    this.interfaceDesc = interfaceDesc;
    this.id = id;
    this.version = version;
  }

  @Override
  public int id() {
    return id;
  }

  @Override
  public int version() {
    return version;
  }

  @Override
  public InterfaceDesc interfaceDesc() {
    return interfaceDesc;
  }

  @Override
  public WaylandConnection connection() {
    return connection;
  }

  @Override
  public boolean isDestroyed() {
    return destroyed;
  }

  @Override
  public void marshal(int opcode, Object... args) {
    MessageDesc message = prepare(opcode, args);
    connection.enqueue(this, opcode, message, args, null, 0);
  }

  @Override
  public void marshalDestructor(int opcode, Object... args) {
    MessageDesc message = prepare(opcode, args);
    if (!message.destructor()) {
      throw new WaylandException(interfaceDesc.name() + "." + message.name()
          + " is not a destructor");
    }
    connection.enqueue(this, opcode, message, args, null, 0);
    // Destroyed now; the id itself is retired only when the server confirms via delete_id.
    destroyed = true;
  }

  @Override
  public Proxy marshalConstructor(int opcode, int childVersion, InterfaceDesc childInterface,
      Object... args) {
    MessageDesc message = prepare(opcode, args);
    if (message.newIdArg().isEmpty()) {
      throw new WaylandException(interfaceDesc.name() + "." + message.name()
          + " creates no object");
    }
    WireProxy child = connection.createChild(childInterface, childVersion);
    connection.enqueue(this, opcode, message, args, child, childVersion);
    return child;
  }

  private MessageDesc prepare(int opcode, Object[] args) {
    if (destroyed) {
      throw new IllegalStateException(interfaceDesc.name() + "@" + id + " is destroyed");
    }
    connection.checkUsable();
    MessageDesc message = interfaceDesc.request(opcode);
    message.checkSince(version);
    message.checkArgs(args);
    return message;
  }

  @Override
  public void setEventHandler(EventSink sink) {
    this.sink = sink;
  }

  /** Delivers a decoded event, or drops it (closing any fd args) if no handler is set. */
  void deliver(int opcode, Object[] args) {
    EventSink current = sink;
    if (current == null) {
      closeFdArgs(args);
      return;
    }
    current.event(opcode, args);
  }

  /** Marks this proxy dead without a client-side destructor (server-initiated). */
  void markDestroyed() {
    destroyed = true;
  }

  static void closeFdArgs(Object[] args) {
    for (Object arg : args) {
      if (arg instanceof Fd fd) {
        fd.close();
      }
    }
  }
}
