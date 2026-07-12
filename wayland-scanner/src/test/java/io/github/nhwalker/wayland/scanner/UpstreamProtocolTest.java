package io.github.nhwalker.wayland.scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/**
 * Robustness against the real, full upstream wayland.xml (vendored): the parser must handle
 * everything in it, and the emitter must produce plausible source for every interface —
 * including the ones far outside the curated slice (pointers, keyboards, data devices).
 */
class UpstreamProtocolTest {

  private static Protocol upstream() throws IOException {
    try (InputStream xml =
        UpstreamProtocolTest.class.getResourceAsStream("/upstream/wayland.xml")) {
      return ProtocolParser.parse(xml);
    }
  }

  @Test
  void parsesTheFullUpstreamProtocol() throws IOException {
    Protocol protocol = upstream();
    assertEquals("wayland", protocol.name());
    assertTrue(protocol.interfaces().size() >= 20);

    Protocol.Interface registry = protocol.interfaces().stream()
        .filter(iface -> iface.name().equals("wl_registry")).findFirst().orElseThrow();
    Protocol.Arg bindId = registry.requests().get(0).args().get(1);
    assertEquals("new_id", bindId.type());
    assertNull(bindId.interfaceName(), "bind's new_id is dynamic upstream too");

    Protocol.Interface shm = protocol.interfaces().stream()
        .filter(iface -> iface.name().equals("wl_shm")).findFirst().orElseThrow();
    Protocol.EnumDef format = shm.enums().stream()
        .filter(enumDef -> enumDef.name().equals("format")).findFirst().orElseThrow();
    assertTrue(format.entries().size() > 100, "upstream has the full fourcc list");
  }

  @Test
  void emitsEveryUpstreamInterface() throws IOException {
    Protocol protocol = upstream();
    for (Protocol.Interface iface : protocol.interfaces()) {
      String source = StubEmitter.emit(protocol, iface, "io.github.nhwalker.wayland.client");
      String className = Names.className(iface.name());
      assertTrue(source.contains("public final class " + className), className);
      assertTrue(source.contains("public static final InterfaceDesc INTERFACE"), className);
      assertFalse(source.contains("\t"), className + " must not contain tabs");
      // Keyword-safe naming: wl_registry.global's 'interface' arg must have been renamed.
      if (iface.name().equals("wl_registry")) {
        assertTrue(source.contains("String interfaceName"));
      }
    }
  }
}
