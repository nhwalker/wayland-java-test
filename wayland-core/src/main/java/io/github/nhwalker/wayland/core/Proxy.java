package io.github.nhwalker.wayland.core;

/**
 * An untyped handle to one protocol object on a connection. Generated stubs wrap a Proxy and
 * funnel every request through the three marshal methods; the kernel knows signatures only
 * through {@link InterfaceDesc}, never interface names.
 *
 * <p>Marshal calls only buffer; bytes move at {@link WaylandConnection#flush()}. An
 * {@link io.github.nhwalker.unixsocket.Fd} argument must remain open until flush returns.
 * Validation failures (unknown opcode, version older than a message's {@code since}, wrong
 * argument arity/type/nullability) throw {@link WaylandException}; use after a destructor
 * throws {@link IllegalStateException}.
 */
public interface Proxy {

  int id();

  /** The protocol version this object was created with — bounds which messages are legal. */
  int version();

  InterfaceDesc interfaceDesc();

  WaylandConnection connection();

  /** True after a destructor request was marshalled or the server retired the object. */
  boolean isDestroyed();

  /** Sends a request that creates no object. */
  void marshal(int opcode, Object... args);

  /** Sends a destructor request and marks this proxy destroyed. */
  void marshalDestructor(int opcode, Object... args);

  /**
   * Sends an object-creating request; the kernel allocates the new id and inserts it at the
   * NEW_ID slot ({@code args} exclude it). The child's version is inherited from this proxy —
   * the common fixed-interface case.
   */
  default Proxy marshalConstructor(int opcode, InterfaceDesc childInterface, Object... args) {
    return marshalConstructor(opcode, version(), childInterface, args);
  }

  /**
   * Object-creating request with an explicit child version (before the descriptor to keep the
   * overloads unambiguous). When the message's NEW_ID arg has no interface ref
   * ({@code wl_registry.bind}), the wire layer emits the dynamic
   * {@code string interface + uint version + new_id} triple from {@code childInterface}.
   *
   * @return the new child proxy
   */
  Proxy marshalConstructor(int opcode, int childVersion, InterfaceDesc childInterface,
      Object... args);

  /**
   * Installs the single event handler for this object, replacing any previous one. Args arrive
   * decoded per the {@link ArgType} carrier table; ownership of any
   * {@link io.github.nhwalker.unixsocket.Fd} arg transfers to the handler. Events for a proxy
   * with no handler are dropped, their fds closed by the kernel.
   */
  void setEventHandler(EventSink sink);

  @FunctionalInterface
  interface EventSink {
    void event(int opcode, Object[] args);
  }
}
