package io.github.nhwalker.wayland.client;

import io.github.nhwalker.wayland.core.InterfaceDesc;
import io.github.nhwalker.wayland.core.MessageDesc;
import io.github.nhwalker.wayland.core.Proxy;
import io.github.nhwalker.wayland.core.WaylandConnection;
import io.github.nhwalker.wayland.core.WaylandException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Records every marshal call and runs the real {@link MessageDesc} validation, so stub tests
 * exercise since/arg checking for real. Doubles as the future emitter-conformance harness.
 */
final class FakeProxy implements Proxy {

  record Call(String kind, int opcode, InterfaceDesc childInterface, Integer childVersion,
      List<Object> args) {}

  private final FakeConnection connection;
  private final InterfaceDesc interfaceDesc;
  private final int id;
  private final int version;
  private boolean destroyed;
  private EventSink sink = (opcode, args) -> {};

  final List<Call> calls = new ArrayList<>();

  FakeProxy(FakeConnection connection, InterfaceDesc interfaceDesc, int id, int version) {
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
    validate(opcode, args);
    calls.add(new Call("marshal", opcode, null, null, Arrays.asList(args)));
  }

  @Override
  public void marshalDestructor(int opcode, Object... args) {
    MessageDesc message = validate(opcode, args);
    if (!message.destructor()) {
      throw new WaylandException(message.name() + " is not a destructor");
    }
    calls.add(new Call("destructor", opcode, null, null, Arrays.asList(args)));
    destroyed = true;
  }

  @Override
  public Proxy marshalConstructor(int opcode, int childVersion, InterfaceDesc childInterface,
      Object... args) {
    validate(opcode, args);
    FakeProxy child = connection.newProxy(childInterface, childVersion);
    calls.add(new Call("constructor", opcode, childInterface, childVersion,
        Arrays.asList(args)));
    return child;
  }

  private MessageDesc validate(int opcode, Object... args) {
    if (destroyed) {
      throw new IllegalStateException(interfaceDesc.name() + "@" + id + " is destroyed");
    }
    MessageDesc message = interfaceDesc.request(opcode);
    message.checkSince(version);
    message.checkArgs(args);
    return message;
  }

  @Override
  public void setEventHandler(EventSink sink) {
    this.sink = sink;
  }

  /** Injects an event as the wire layer would deliver it. */
  void fire(int opcode, Object... args) {
    sink.event(opcode, args);
  }
}
