package io.github.nhwalker.wayland.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nhwalker.wayland.core.ArgDesc;
import io.github.nhwalker.wayland.core.ArgType;
import io.github.nhwalker.wayland.core.InterfaceDesc;
import io.github.nhwalker.wayland.core.MessageDesc;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Spot-checks the descriptor tables of every reference stub. */
class DescriptorTablesTest {

  @Test
  void namesAndVersions() {
    record Expected(InterfaceDesc desc, String name, int version) {}
    List<Expected> all = List.of(
        new Expected(WlDisplay.INTERFACE, "wl_display", 1),
        new Expected(WlRegistry.INTERFACE, "wl_registry", 1),
        new Expected(WlCallback.INTERFACE, "wl_callback", 1),
        new Expected(WlCompositor.INTERFACE, "wl_compositor", 6),
        new Expected(WlShm.INTERFACE, "wl_shm", 2),
        new Expected(WlShmPool.INTERFACE, "wl_shm_pool", 1),
        new Expected(WlBuffer.INTERFACE, "wl_buffer", 1),
        new Expected(WlSurface.INTERFACE, "wl_surface", 6),
        new Expected(WlRegion.INTERFACE, "wl_region", 1),
        new Expected(WlOutput.INTERFACE, "wl_output", 4));
    for (Expected expected : all) {
      assertEquals(expected.name(), expected.desc().name());
      assertEquals(expected.version(), expected.desc().version());
    }
  }

  @Test
  void registryBindIsTheDynamicNewId() {
    MessageDesc bind = WlRegistry.INTERFACE.request(0);
    assertEquals("bind", bind.name());
    ArgDesc newId = bind.newIdArg().orElseThrow();
    assertNull(newId.interfaceRef(), "bind's new_id must have no fixed interface");
    assertEquals(1, bind.marshalArgCount()); // just the uint name
  }

  @Test
  void fixedNewIdsResolveLazily() {
    ArgDesc callback = WlDisplay.INTERFACE.request(0).newIdArg().orElseThrow();
    assertSame(WlCallback.INTERFACE, callback.interfaceRef().get());

    ArgDesc pool = WlShm.INTERFACE.request(0).newIdArg().orElseThrow();
    assertSame(WlShmPool.INTERFACE, pool.interfaceRef().get());
  }

  @Test
  void destructorsAndSinceVersions() {
    MessageDesc shmRelease = WlShm.INTERFACE.request(1);
    assertTrue(shmRelease.destructor());
    assertEquals(2, shmRelease.since());

    assertTrue(WlSurface.INTERFACE.request(0).destructor());
    assertEquals(3, WlSurface.INTERFACE.request(8).since());   // set_buffer_scale
    assertEquals(3, WlOutput.INTERFACE.request(0).since());    // release
    assertEquals(6, WlSurface.INTERFACE.event(2).since());     // preferred_buffer_scale
  }

  @Test
  void nullableObjectArgs() {
    ArgDesc buffer = WlSurface.INTERFACE.request(1).args().get(0);
    assertEquals(ArgType.OBJECT, buffer.type());
    assertTrue(buffer.nullable());

    ArgDesc fd = WlShm.INTERFACE.request(0).args().get(1);
    assertEquals(ArgType.FD, fd.type());
  }
}
