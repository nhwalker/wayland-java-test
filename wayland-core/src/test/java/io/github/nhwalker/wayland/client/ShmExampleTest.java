package io.github.nhwalker.wayland.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nhwalker.unixsocket.Fd;
import io.github.nhwalker.unixsocket.UnixSocketProvider;
import io.github.nhwalker.wayland.core.WaylandConnection;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * The target end-to-end UX, compiled and executed: connect, discover globals, roundtrip, bind
 * wl_shm, share pixels via a real memfd, attach and commit. The compositor side is the
 * scriptable {@link FakeConnection} (the wire engine is the next increment); the shared-memory
 * fd and its mapping are real.
 */
class ShmExampleTest {

  @Test
  void endToEndClientFlow() throws Exception {
    FakeConnection fake = new FakeConnection();
    WaylandConnection connection = fake;

    WlDisplay display = WlDisplay.from(connection);
    WlRegistry registry = display.getRegistry();

    Map<Integer, WlRegistry.Event.Global> globals = new HashMap<>();
    registry.onEvent(event -> {
      switch (event) {
        case WlRegistry.Event.Global g -> globals.put(g.name(), g);
        case WlRegistry.Event.GlobalRemove r -> globals.remove(r.name());
      }
    });

    // The "compositor" advertises its globals, then completes the sync.
    fake.script(registry.proxy(), 0, 1, "wl_compositor", 6);
    fake.script(registry.proxy(), 0, 2, "wl_shm", 2);

    WlCallback sync = display.sync();
    AtomicBoolean done = new AtomicBoolean();
    sync.onEvent(event -> done.set(true));
    fake.script(sync.proxy(), 0, 1);
    connection.dispatchUntil(done::get);

    assertEquals(2, globals.size());
    WlRegistry.Event.Global shmGlobal = globals.values().stream()
        .filter(g -> g.interfaceName().equals("wl_shm"))
        .findFirst().orElseThrow();
    WlRegistry.Event.Global compositorGlobal = globals.values().stream()
        .filter(g -> g.interfaceName().equals("wl_compositor"))
        .findFirst().orElseThrow();

    WlShm shm = registry.bind(shmGlobal.name(), WlShm.TYPE,
        Math.min(shmGlobal.version(), WlShm.INTERFACE.version()));
    WlCompositor compositor = registry.bind(compositorGlobal.name(), WlCompositor.TYPE,
        Math.min(compositorGlobal.version(), WlCompositor.INTERFACE.version()));

    int width = 64;
    int height = 64;
    int stride = width * 4;
    long poolSize = (long) stride * height;

    try (Fd fd = UnixSocketProvider.provider().sharedMemory(poolSize);
        Arena arena = Arena.ofConfined()) {
      MemorySegment pixels = fd.map(Fd.MapMode.READ_WRITE, 0, poolSize, arena);
      pixels.fill((byte) 0x7F); // "draw"

      WlShmPool pool = shm.createPool(fd, (int) poolSize);
      WlBuffer buffer = pool.createBuffer(0, width, height, stride, WlShm.Format.ARGB8888);
      WlSurface surface = compositor.createSurface();
      surface.attach(buffer, 0, 0);
      surface.damage(0, 0, width, height);
      surface.commit();
      connection.flush();

      // The recorded marshal stream is exactly the protocol traffic a compositor would see.
      FakeProxy.Call createPool = fake.calls("wl_shm").get(0);
      assertSame(WlShmPool.INTERFACE, createPool.childInterface());
      assertSame(fd, createPool.args().get(0)); // the very fd we mapped

      FakeProxy.Call createBuffer = fake.calls("wl_shm_pool").get(0);
      assertEquals(List.of(0, width, height, stride, WlShm.Format.ARGB8888.value()),
          createBuffer.args());

      List<FakeProxy.Call> surfaceCalls = fake.calls("wl_surface");
      assertSame(buffer.proxy(), surfaceCalls.get(0).args().get(0)); // attach
      assertEquals("marshal", surfaceCalls.get(1).kind());           // damage
      assertEquals(6, surfaceCalls.get(2).opcode());                 // commit
      assertTrue(fake.flushCount > 0);

      // The mapping is real: the memfd still holds what we drew.
      assertEquals((byte) 0x7F, pixels.get(ValueLayout.JAVA_BYTE, poolSize - 1));
    }
  }
}
