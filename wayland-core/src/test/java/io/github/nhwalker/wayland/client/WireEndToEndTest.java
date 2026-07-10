package io.github.nhwalker.wayland.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nhwalker.unixsocket.Fd;
import io.github.nhwalker.unixsocket.UnixSocketProvider;
import io.github.nhwalker.wayland.core.WaylandConnection;
import io.github.nhwalker.wayland.core.WirePeer;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The full generated-stub client flow over the real wire engine and a real socketpair, against
 * a scripted compositor that parses raw wire bytes: registry discovery, sync roundtrip, typed
 * binds, shm pool sharing (the compositor maps the received fd and reads the client's pixels),
 * attach/damage/commit, and a server-sent buffer release.
 */
@Timeout(10)
class WireEndToEndTest {

  /** What the scripted compositor observed; read after join() for safe publication. */
  private static final class CompositorLog {
    String shmInterface;
    int shmVersion;
    String compositorInterface;
    int poolSize;
    byte pixel;
    int bufferOffset;
    int bufferWidth;
    int bufferHeight;
    int bufferStride;
    int bufferFormat;
    int attachedBufferId;
    int commitOpcode = -1;
  }

  @Test
  void fullClientFlowAgainstScriptedCompositor() throws Exception {
    UnixSocketProvider provider = UnixSocketProvider.provider();
    var pair = provider.pair();
    WaylandConnection connection = WaylandConnection.adopt(pair.first());
    WirePeer peer = new WirePeer(pair.second());

    CompositorLog log = new CompositorLog();
    AtomicReference<Throwable> compositorError = new AtomicReference<>();
    Thread compositor = Thread.ofPlatform().daemon().start(() -> {
      try (peer) {
        // get_registry, then sync.
        WirePeer.Message getRegistry = peer.read();
        int registryId = getRegistry.reader().readWord();
        WirePeer.Message sync = peer.read();
        int callbackId = sync.reader().readWord();

        // Advertise globals, complete the sync, retire the callback.
        peer.write(WirePeer.cat(
            WirePeer.msg(registryId, 0,
                WirePeer.words(1), WirePeer.str("wl_compositor"), WirePeer.words(6)),
            WirePeer.msg(registryId, 0,
                WirePeer.words(2), WirePeer.str("wl_shm"), WirePeer.words(2)),
            WirePeer.msg(callbackId, 0, WirePeer.words(1)),
            WirePeer.msg(1, 1, WirePeer.words(callbackId))));

        // bind wl_shm, bind wl_compositor (client binds in this order).
        WirePeer.BodyReader bindShm = peer.read().reader();
        bindShm.readWord(); // global name
        log.shmInterface = bindShm.readString();
        log.shmVersion = bindShm.readWord();
        int shmId = bindShm.readWord();

        WirePeer.BodyReader bindCompositor = peer.read().reader();
        bindCompositor.readWord();
        log.compositorInterface = bindCompositor.readString();
        bindCompositor.readWord();
        int compositorId = bindCompositor.readWord();

        // create_pool: read the client's pixels through the received fd.
        WirePeer.Message createPool = peer.read();
        WirePeer.BodyReader poolBody = createPool.reader();
        int poolId = poolBody.readWord();
        log.poolSize = poolBody.readWord();
        try (Fd poolFd = peer.takeFd(); Arena arena = Arena.ofConfined()) {
          MemorySegment pixels = poolFd.map(Fd.MapMode.READ_ONLY, 0, log.poolSize, arena);
          log.pixel = pixels.get(ValueLayout.JAVA_BYTE, log.poolSize - 1);
        }

        // create_buffer, create_surface.
        WirePeer.BodyReader createBuffer = peer.read().reader();
        int bufferId = createBuffer.readWord();
        log.bufferOffset = createBuffer.readWord();
        log.bufferWidth = createBuffer.readWord();
        log.bufferHeight = createBuffer.readWord();
        log.bufferStride = createBuffer.readWord();
        log.bufferFormat = createBuffer.readWord();

        WirePeer.BodyReader createSurface = peer.read().reader();
        int surfaceId = createSurface.readWord();

        // attach, damage, commit.
        log.attachedBufferId = peer.read().reader().readWord();
        peer.read(); // damage
        log.commitOpcode = peer.read().opcode();

        // The compositor is done with the buffer.
        peer.write(WirePeer.msg(bufferId, 0));
      } catch (Throwable t) {
        compositorError.set(t);
        try {
          peer.close();
        } catch (Exception ignored) {
          // best-effort unblock of the client
        }
      }
    });

    try (connection) {
      WlDisplay display = WlDisplay.from(connection);
      WlRegistry registry = display.getRegistry();

      Map<Integer, WlRegistry.Event.Global> globals = new HashMap<>();
      registry.onEvent(event -> {
        switch (event) {
          case WlRegistry.Event.Global g -> globals.put(g.name(), g);
          case WlRegistry.Event.GlobalRemove r -> globals.remove(r.name());
        }
      });

      WlCallback sync = display.sync();
      AtomicBoolean done = new AtomicBoolean();
      sync.onEvent(event -> done.set(true));
      connection.dispatchUntil(done::get);

      assertEquals(2, globals.size());
      assertTrue(sync.isDestroyed(), "delete_id must retire the callback");

      WlRegistry.Event.Global shmGlobal = globals.values().stream()
          .filter(g -> g.interfaceName().equals("wl_shm")).findFirst().orElseThrow();
      WlRegistry.Event.Global compositorGlobal = globals.values().stream()
          .filter(g -> g.interfaceName().equals("wl_compositor")).findFirst().orElseThrow();

      WlShm shm = registry.bind(shmGlobal.name(), WlShm.TYPE,
          Math.min(shmGlobal.version(), WlShm.INTERFACE.version()));
      WlCompositor wlCompositor = registry.bind(compositorGlobal.name(), WlCompositor.TYPE,
          Math.min(compositorGlobal.version(), WlCompositor.INTERFACE.version()));

      int width = 8;
      int height = 8;
      int stride = width * 4;
      int poolSize = stride * height;

      WlBuffer buffer;
      try (Fd fd = provider.sharedMemory(poolSize); Arena arena = Arena.ofConfined()) {
        MemorySegment pixels = fd.map(Fd.MapMode.READ_WRITE, 0, poolSize, arena);
        pixels.fill((byte) 0x42); // "draw"

        WlShmPool pool = shm.createPool(fd, poolSize);
        buffer = pool.createBuffer(0, width, height, stride, WlShm.Format.ARGB8888);
        WlSurface surface = wlCompositor.createSurface();
        surface.attach(buffer, 0, 0);
        surface.damage(0, 0, width, height);
        surface.commit();
        connection.flush();
      }

      AtomicBoolean released = new AtomicBoolean();
      buffer.onEvent(event -> {
        if (event instanceof WlBuffer.Event.Release) {
          released.set(true);
        }
      });
      connection.dispatchUntil(released::get);

      compositor.join(5_000);
      assertFalse(compositor.isAlive());
      assertNull(compositorError.get());

      // The compositor's view of the conversation.
      assertEquals("wl_shm", log.shmInterface);
      assertEquals(2, log.shmVersion);
      assertEquals("wl_compositor", log.compositorInterface);
      assertEquals(poolSize, log.poolSize);
      assertEquals((byte) 0x42, log.pixel, "compositor must see the client's pixels");
      assertEquals(0, log.bufferOffset);
      assertEquals(width, log.bufferWidth);
      assertEquals(height, log.bufferHeight);
      assertEquals(stride, log.bufferStride);
      assertEquals(WlShm.Format.ARGB8888.value(), log.bufferFormat);
      assertEquals(buffer.id(), log.attachedBufferId);
      assertEquals(6, log.commitOpcode);
    }
  }
}
