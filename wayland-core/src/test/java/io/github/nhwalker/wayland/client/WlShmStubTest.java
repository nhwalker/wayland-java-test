package io.github.nhwalker.wayland.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nhwalker.unixsocket.Fd;
import io.github.nhwalker.unixsocket.UnixSocketProvider;
import io.github.nhwalker.wayland.core.WaylandException;
import java.util.List;
import org.junit.jupiter.api.Test;

class WlShmStubTest {

  private final FakeConnection connection = new FakeConnection();

  @Test
  void createPoolPassesTheFdThrough() throws Exception {
    WlShm shm = WlShm.TYPE.wrap(connection.newProxy(WlShm.INTERFACE, 2));
    try (Fd fd = UnixSocketProvider.provider().sharedMemory(4096)) {
      WlShmPool pool = shm.createPool(fd, 4096);

      FakeProxy.Call call = connection.calls("wl_shm").get(0);
      assertSame(fd, call.args().get(0));
      assertEquals(4096, call.args().get(1));
      assertSame(WlShmPool.INTERFACE, pool.proxy().interfaceDesc());
    }
  }

  @Test
  void releaseIsSinceVersion2() {
    WlShm shmV1 = WlShm.TYPE.wrap(connection.newProxy(WlShm.INTERFACE, 1));
    WaylandException e = assertThrows(WaylandException.class, shmV1::release);
    assertTrue(e.getMessage().contains("requires version 2"), e.getMessage());

    WlShm shmV2 = WlShm.TYPE.wrap(connection.newProxy(WlShm.INTERFACE, 2));
    shmV2.release();
    assertTrue(shmV2.isDestroyed());
  }

  @Test
  void formatEventCarriesRawIntDecodableViaLookup() {
    WlShm shm = WlShm.TYPE.wrap(connection.newProxy(WlShm.INTERFACE, 2));
    List<WlShm.Event> seen = new java.util.ArrayList<>();
    shm.onEvent(seen::add);

    ((FakeProxy) shm.proxy()).fire(0, WlShm.Format.XRGB8888.value());

    WlShm.Event.Format event = (WlShm.Event.Format) seen.get(0);
    assertEquals(WlShm.Format.XRGB8888, WlShm.Format.lookup(event.format()).orElseThrow());
    assertTrue(WlShm.Format.lookup(0x12345678).isEmpty());
  }

  @Test
  void createBufferEncodesEnumAsInt() {
    WlShm shm = WlShm.TYPE.wrap(connection.newProxy(WlShm.INTERFACE, 2));
    WlShmPool pool;
    try (Fd fd = fakeUsableFd()) {
      pool = shm.createPool(fd, 64);
    }
    pool.createBuffer(0, 4, 4, 16, WlShm.Format.ARGB8888);

    FakeProxy.Call call = connection.calls("wl_shm_pool").get(0);
    assertEquals(WlShm.Format.ARGB8888.value(), call.args().get(4));
    assertEquals(0, call.args().get(4)); // ARGB8888 is wire value 0
    assertFalse(pool.isDestroyed());
  }

  private static Fd fakeUsableFd() {
    try {
      return UnixSocketProvider.provider().sharedMemory(64);
    } catch (java.io.IOException e) {
      throw new RuntimeException(e);
    }
  }
}
