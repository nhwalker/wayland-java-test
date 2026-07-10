package io.github.nhwalker.wayland.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WlDisplayControlTest {

  private final FakeConnection connection = new FakeConnection();
  private final WlDisplay display = WlDisplay.from(connection);

  @Test
  void displayWrapsObjectIdOne() {
    assertEquals(1, display.id());
    assertSame(connection, display.connection());
  }

  @Test
  void errorEventRaisesFatalErrorAndReachesUserHandler() {
    List<WlDisplay.Event> seen = new ArrayList<>();
    display.onEvent(seen::add);

    ((FakeProxy) display.proxy()).fire(0, display.proxy(), 3, "boom");

    assertEquals(1, connection.fatalErrors.size());
    assertEquals(3, connection.fatalErrors.get(0).code());
    assertTrue(connection.error().isPresent());
    assertEquals(new WlDisplay.Event.Error(display.proxy(), 3, "boom"), seen.get(0));
  }

  @Test
  void deleteIdEventRetiresId() {
    ((FakeProxy) display.proxy()).fire(1, 42);
    assertEquals(List.of(42), connection.retiredIds);
  }

  @Test
  void userHandlerReplacementDoesNotDisturbControlWiring() {
    display.onEvent(e -> {});
    display.onEvent(e -> {});
    ((FakeProxy) display.proxy()).fire(1, 7);
    assertEquals(List.of(7), connection.retiredIds);
  }

  @Test
  void syncAndGetRegistryReturnTypedChildren() {
    WlCallback callback = display.sync();
    WlRegistry registry = display.getRegistry();

    assertSame(WlCallback.INTERFACE, callback.proxy().interfaceDesc());
    assertSame(WlRegistry.INTERFACE, registry.proxy().interfaceDesc());

    List<FakeProxy.Call> calls = connection.calls("wl_display");
    assertEquals(0, calls.get(0).opcode());
    assertEquals(1, calls.get(1).opcode());
  }
}
