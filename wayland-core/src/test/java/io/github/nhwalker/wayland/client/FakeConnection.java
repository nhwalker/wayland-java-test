package io.github.nhwalker.wayland.client;

import io.github.nhwalker.wayland.core.InterfaceDesc;
import io.github.nhwalker.wayland.core.Proxy;
import io.github.nhwalker.wayland.core.WaylandConnection;
import io.github.nhwalker.wayland.core.WaylandException;
import io.github.nhwalker.wayland.core.WaylandProtocolException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * A scriptable in-memory {@link WaylandConnection}: proxies are {@link FakeProxy}s, and
 * "server" events are queued with {@link #script} then delivered by {@link #dispatch()}.
 */
final class FakeConnection implements WaylandConnection {

  private final List<FakeProxy> proxies = new ArrayList<>();
  private final Deque<Runnable> pending = new ArrayDeque<>();
  private FakeProxy display;
  private int nextId = 2;
  private boolean open = true;
  private WaylandProtocolException error;

  int flushCount;
  final List<WaylandProtocolException> fatalErrors = new ArrayList<>();
  final List<Integer> retiredIds = new ArrayList<>();

  FakeProxy newProxy(InterfaceDesc interfaceDesc, int version) {
    FakeProxy proxy = new FakeProxy(this, interfaceDesc, nextId++, version);
    proxies.add(proxy);
    return proxy;
  }

  /** All marshal calls recorded by proxies of the given wire interface, in creation order. */
  List<FakeProxy.Call> calls(String interfaceName) {
    List<FakeProxy.Call> result = new ArrayList<>();
    for (FakeProxy proxy : proxies) {
      if (proxy.interfaceDesc().name().equals(interfaceName)) {
        result.addAll(proxy.calls);
      }
    }
    return result;
  }

  /** Queues an event on {@code target} for the next {@link #dispatch()}. */
  void script(Proxy target, int opcode, Object... args) {
    pending.add(() -> ((FakeProxy) target).fire(opcode, args));
  }

  @Override
  public Proxy display(InterfaceDesc displayInterface) {
    if (display == null) {
      display = new FakeProxy(this, displayInterface, 1, 1);
      proxies.add(display);
    } else if (!display.interfaceDesc().equals(displayInterface)) {
      throw new WaylandException("display already bound to a different descriptor");
    }
    return display;
  }

  @Override
  public void flush() {
    flushCount++;
  }

  @Override
  public int dispatch() {
    if (pending.isEmpty()) {
      throw new IllegalStateException("dispatch would block: nothing scripted");
    }
    return dispatchPending();
  }

  @Override
  public int dispatchPending() {
    int count = 0;
    Runnable next;
    while ((next = pending.poll()) != null) {
      next.run();
      count++;
    }
    return count;
  }

  @Override
  public void fatalError(Proxy object, int code, String message) {
    error = new WaylandProtocolException(
        object == null ? 0 : object.id(),
        object == null ? "unknown" : object.interfaceDesc().name(),
        code, message);
    fatalErrors.add(error);
  }

  @Override
  public void retireId(int id) {
    retiredIds.add(id);
  }

  @Override
  public Optional<WaylandProtocolException> error() {
    return Optional.ofNullable(error);
  }

  @Override
  public boolean isOpen() {
    return open;
  }

  @Override
  public void close() {
    open = false;
  }
}
