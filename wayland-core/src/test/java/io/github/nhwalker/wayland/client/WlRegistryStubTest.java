package io.github.nhwalker.wayland.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WlRegistryStubTest {

  private final FakeConnection connection = new FakeConnection();
  private final WlRegistry registry =
      WlRegistry.TYPE.wrap(connection.newProxy(WlRegistry.INTERFACE, 1));

  @Test
  void bindMarshalsDynamicConstructorAndReturnsTypedWrapper() {
    WlShm shm = registry.bind(42, WlShm.TYPE, 2);

    FakeProxy.Call call = connection.calls("wl_registry").get(0);
    assertEquals("constructor", call.kind());
    assertEquals(0, call.opcode());
    assertSame(WlShm.INTERFACE, call.childInterface());
    assertEquals(2, call.childVersion());
    assertEquals(List.of(42), call.args());

    assertEquals(2, shm.version());
    assertSame(WlShm.INTERFACE, shm.proxy().interfaceDesc());
  }

  @Test
  void eventsDecodeToSealedRecords() {
    List<WlRegistry.Event> seen = new ArrayList<>();
    registry.onEvent(seen::add);

    ((FakeProxy) registry.proxy()).fire(0, 7, "wl_shm", 2);
    ((FakeProxy) registry.proxy()).fire(1, 7);

    assertEquals(List.of(
        new WlRegistry.Event.Global(7, "wl_shm", 2),
        new WlRegistry.Event.GlobalRemove(7)), seen);
  }

  @Test
  void eventSwitchIsExhaustive() {
    // Compile-time proof: a switch over Event needs no default branch.
    WlRegistry.Event event = new WlRegistry.Event.GlobalRemove(1);
    int name = switch (event) {
      case WlRegistry.Event.Global g -> g.name();
      case WlRegistry.Event.GlobalRemove r -> r.name();
    };
    assertEquals(1, name);
  }
}
