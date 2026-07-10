package io.github.nhwalker.wayland.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nhwalker.wayland.core.WaylandException;
import org.junit.jupiter.api.Test;

class WlSurfaceStubTest {

  private final FakeConnection connection = new FakeConnection();

  private WlSurface surfaceAtVersion(int version) {
    return WlSurface.TYPE.wrap(connection.newProxy(WlSurface.INTERFACE, version));
  }

  @Test
  void attachAcceptsNullBuffer() {
    WlSurface surface = surfaceAtVersion(6);
    surface.attach(null, 0, 0);

    FakeProxy.Call call = connection.calls("wl_surface").get(0);
    assertNull(call.args().get(0));
  }

  @Test
  void attachPassesTheBuffersProxy() {
    WlSurface surface = surfaceAtVersion(6);
    WlBuffer buffer = WlBuffer.TYPE.wrap(connection.newProxy(WlBuffer.INTERFACE, 1));
    surface.attach(buffer, 0, 0);

    FakeProxy.Call call = connection.calls("wl_surface").get(0);
    assertSame(buffer.proxy(), call.args().get(0));
  }

  @Test
  void sinceGuardsAreEnforced() {
    WlSurface old = surfaceAtVersion(2);
    WaylandException e = assertThrows(WaylandException.class, () -> old.setBufferScale(2));
    assertTrue(e.getMessage().contains("requires version 3"), e.getMessage());

    surfaceAtVersion(3).setBufferScale(2); // allowed at 3
  }

  @Test
  void tryWithResourcesDestroys() {
    WlSurface surface = surfaceAtVersion(6);
    try (surface) {
      surface.commit();
    }
    assertTrue(surface.isDestroyed());

    // Second close is a no-op, and further requests are rejected.
    surface.close();
    assertThrows(IllegalStateException.class, surface::commit);
  }

  @Test
  void frameReturnsTypedCallback() {
    WlSurface surface = surfaceAtVersion(6);
    WlCallback callback = surface.frame();
    assertSame(WlCallback.INTERFACE, callback.proxy().interfaceDesc());
    assertEquals(surface.version(), callback.version()); // child inherits version
  }

  @Test
  void bufferTransformTakesTypedEnum() {
    WlSurface surface = surfaceAtVersion(6);
    surface.setBufferTransform(WlOutput.Transform._90);

    FakeProxy.Call call = connection.calls("wl_surface").get(0);
    assertEquals(1, call.args().get(0));
  }
}
