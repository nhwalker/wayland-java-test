package io.github.nhwalker.wayland.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nhwalker.unixsocket.Fd;
import io.github.nhwalker.unixsocket.UnixSocketProvider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Engine-level tests over a real socketpair, using synthetic descriptors — the kernel is
 * protocol-agnostic, so the tests define their own protocol.
 */
@Timeout(10)
class WireConnectionTest {

  private static final InterfaceDesc CHILD = InterfaceDesc.of("test_child", 1,
      List.of(
          MessageDesc.of("destroy", 1, true)),
      List.of(
          MessageDesc.of("pinged", 1, false, ArgDesc.uintArg("serial"))));

  private static final InterfaceDesc THING = InterfaceDesc.of("test_thing", 3,
      List.of(
          MessageDesc.of("poke", 1, false,
              ArgDesc.intArg("value"),
              ArgDesc.fixedArg("f"),
              ArgDesc.stringArg("label").asNullable(),
              ArgDesc.arrayArg("blob")),
          MessageDesc.of("give", 1, false,
              ArgDesc.fdArg("fd")),
          MessageDesc.of("spawn", 1, false,
              ArgDesc.newIdArg("id", () -> CHILD)),
          MessageDesc.of("bind_like", 1, false,
              ArgDesc.newIdArg("id", null))),
      List.of(
          MessageDesc.of("poked", 1, false,
              ArgDesc.uintArg("serial"),
              ArgDesc.stringArg("label").asNullable(),
              ArgDesc.fixedArg("f"),
              ArgDesc.arrayArg("blob")),
          MessageDesc.of("gave", 1, false,
              ArgDesc.fdArg("fd")),
          MessageDesc.of("spawned", 1, false,
              ArgDesc.newIdArg("id", () -> CHILD)),
          MessageDesc.of("linked", 1, false,
              ArgDesc.objectArg("other", () -> CHILD).asNullable())));

  private final UnixSocketProvider provider = UnixSocketProvider.provider();
  private WaylandConnection connection;
  private WirePeer peer;
  private Proxy thing;

  @BeforeEach
  void setUp() throws IOException {
    var pair = provider.pair();
    connection = WaylandConnection.adopt(pair.first());
    peer = new WirePeer(pair.second());
    thing = connection.display(THING);
  }

  @AfterEach
  void tearDown() throws IOException {
    connection.close();
    peer.close();
  }

  private List<Object[]> record(Proxy proxy) {
    List<Object[]> events = new ArrayList<>();
    proxy.setEventHandler((opcode, args) -> {
      Object[] withOpcode = new Object[args.length + 1];
      withOpcode[0] = opcode;
      System.arraycopy(args, 0, withOpcode, 1, args.length);
      events.add(withOpcode);
    });
    return events;
  }

  @Test
  void encodesWordsStringsAndArrays() throws IOException {
    thing.marshal(0, 42, Fixed.of(1.5), "hi", WlArray.of(new byte[] {1, 2, 3, 4, 5}));
    connection.flush();

    WirePeer.Message message = peer.read();
    assertEquals(1, message.objectId());
    assertEquals(0, message.opcode());
    assertArrayEquals(
        WirePeer.cat(
            WirePeer.words(42, Fixed.of(1.5).raw()),
            WirePeer.str("hi"),
            WirePeer.array(new byte[] {1, 2, 3, 4, 5})),
        message.body());
  }

  @Test
  void encodesNullString() throws IOException {
    thing.marshal(0, 1, Fixed.ZERO, null, WlArray.of(new byte[0]));
    connection.flush();

    WirePeer.BodyReader body = peer.read().reader();
    assertEquals(1, body.readWord());
    assertEquals(0, body.readWord());       // fixed zero
    assertNull(body.readString());          // null string = length 0
    assertEquals(0, body.readWord());       // empty array = length 0
    assertEquals(0, body.remaining());
  }

  @Test
  void sendsFdsAsAncillaryData() throws Exception {
    try (Fd fd = provider.sharedMemory(new byte[] {9, 9, 9})) {
      thing.marshal(1, fd);
      connection.flush();

      WirePeer.Message message = peer.read();
      assertEquals(1, message.opcode());
      assertEquals(0, message.body().length); // fd is out of band
      try (Fd received = peer.takeFd()) {
        assertEquals(3, received.size()); // the compositor got a live duplicate
      }
    }
  }

  @Test
  void constructorEncodesNewIdAndAllocatesFromTwo() throws IOException {
    Proxy child = thing.marshalConstructor(2, CHILD);
    assertEquals(2, child.id());
    assertSame(CHILD, child.interfaceDesc());
    assertEquals(thing.version(), child.version());
    connection.flush();

    WirePeer.Message message = peer.read();
    assertEquals(2, message.opcode());
    assertArrayEquals(WirePeer.words(2), message.body());
  }

  @Test
  void dynamicNewIdEncodesInterfaceAndVersion() throws IOException {
    Proxy child = thing.marshalConstructor(3, 1, CHILD);
    connection.flush();

    WirePeer.Message message = peer.read();
    assertEquals(3, message.opcode());
    assertArrayEquals(
        WirePeer.cat(WirePeer.str("test_child"), WirePeer.words(1, child.id())),
        message.body());
  }

  @Test
  void decodesEventArguments() throws IOException {
    List<Object[]> events = record(thing);
    peer.write(WirePeer.msg(1, 0,
        WirePeer.words(7),
        WirePeer.str("label"),
        WirePeer.words(Fixed.of(-2.5).raw()),
        WirePeer.array(new byte[] {5, 6})));

    assertEquals(1, connection.dispatch());
    Object[] event = events.get(0);
    assertEquals(0, event[0]);
    assertEquals(7, event[1]);
    assertEquals("label", event[2]);
    assertEquals(Fixed.of(-2.5), event[3]);
    assertEquals(WlArray.of(new byte[] {5, 6}), event[4]);
  }

  @Test
  void decodesNullString() throws IOException {
    List<Object[]> events = record(thing);
    peer.write(WirePeer.msg(1, 0,
        WirePeer.words(7), WirePeer.str(null), WirePeer.words(0), WirePeer.array(new byte[0])));

    connection.dispatch();
    assertNull(events.get(0)[2]);
  }

  @Test
  void decodesFdEvents() throws Exception {
    List<Object[]> events = record(thing);
    try (Fd fd = provider.sharedMemory(new byte[] {1, 2, 3, 4})) {
      peer.write(WirePeer.msg(1, 1), fd);
    }

    connection.dispatch();
    try (Fd received = assertInstanceOf(Fd.class, events.get(0)[1])) {
      assertEquals(4, received.size());
    }
  }

  @Test
  void eventNewIdCreatesServerProxy() throws IOException {
    List<Object[]> events = record(thing);
    int serverId = 0xFF000001;
    peer.write(WirePeer.msg(1, 2, WirePeer.words(serverId)));
    connection.dispatch();

    Proxy created = assertInstanceOf(Proxy.class, events.get(0)[1]);
    assertEquals(serverId, created.id());
    assertSame(CHILD, created.interfaceDesc());

    // The server-created object is registered: events addressed to it are delivered.
    List<Object[]> childEvents = record(created);
    peer.write(WirePeer.msg(serverId, 0, WirePeer.words(99)));
    connection.dispatch();
    assertEquals(99, childEvents.get(0)[1]);
  }

  @Test
  void objectEventArgsResolveThroughTheObjectMap() throws IOException {
    Proxy child = thing.marshalConstructor(2, CHILD);
    List<Object[]> events = record(thing);

    peer.write(WirePeer.msg(1, 3, WirePeer.words(child.id())));
    connection.dispatch();
    assertSame(child, events.get(0)[1]);

    peer.write(WirePeer.msg(1, 3, WirePeer.words(0)));
    connection.dispatch();
    assertNull(events.get(0 + 1)[1]);
  }

  @Test
  void partialMessagesAreBufferedAcrossReceives() throws IOException {
    List<Object[]> events = record(thing);
    byte[] message = WirePeer.msg(1, 0,
        WirePeer.words(7), WirePeer.str("split"), WirePeer.words(0),
        WirePeer.array(new byte[0]));

    peer.write(java.util.Arrays.copyOfRange(message, 0, 6));
    peer.write(java.util.Arrays.copyOfRange(message, 6, message.length));

    assertEquals(1, connection.dispatch()); // blocks until the full message assembles
    assertEquals("split", events.get(0)[2]);
  }

  @Test
  void zombieEventsAreDroppedAndIdsReusedAfterRetire() throws IOException {
    Proxy child = thing.marshalConstructor(2, CHILD);
    int childId = child.id();
    List<Object[]> childEvents = record(child);

    child.marshalDestructor(0);
    assertTrue(child.isDestroyed());

    // Event already in flight for the zombie: decoded (signature known) but dropped.
    peer.write(WirePeer.msg(childId, 0, WirePeer.words(1)));
    connection.dispatch();
    assertTrue(childEvents.isEmpty());

    // Server confirms deletion; the id becomes reusable.
    connection.retireId(childId);
    Proxy next = thing.marshalConstructor(2, CHILD);
    assertEquals(childId, next.id());
  }

  @Test
  void endOfStreamThrowsOnDispatch() throws IOException {
    peer.close();
    assertThrows(IOException.class, connection::dispatch);
  }

  @Test
  void fatalErrorPoisonsTheConnection() {
    connection.fatalError(thing, 5, "boom");

    assertTrue(connection.error().isPresent());
    WaylandProtocolException e =
        assertThrows(WaylandProtocolException.class, () -> thing.marshal(0, 1, Fixed.ZERO,
            null, WlArray.of(new byte[0])));
    assertEquals(5, e.code());
    assertEquals("test_thing", e.interfaceName());
  }

  @Test
  void closedConnectionRejectsUse() {
    connection.close();
    connection.close(); // idempotent
    assertFalse(connection.isOpen());
    assertThrows(WaylandException.class,
        () -> thing.marshal(0, 1, Fixed.ZERO, null, WlArray.of(new byte[0])));
  }

  @Test
  void destroyedProxyRejectsRequests() throws IOException {
    Proxy child = thing.marshalConstructor(2, CHILD);
    child.marshalDestructor(0);
    assertThrows(IllegalStateException.class, () -> child.marshalDestructor(0));
  }
}
