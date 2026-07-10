package io.github.nhwalker.wayland.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MessageDescTest {

  private static final InterfaceDesc CHILD =
      InterfaceDesc.of("test_child", 1, java.util.List.of(), java.util.List.of());

  private static final MessageDesc CREATE = MessageDesc.of("create", 2, false,
      ArgDesc.newIdArg("id", () -> CHILD),
      ArgDesc.uintArg("count"),
      ArgDesc.stringArg("label"),
      ArgDesc.objectArg("target", () -> CHILD).asNullable());

  @Test
  void checkSinceEnforcesVersion() {
    assertDoesNotThrow(() -> CREATE.checkSince(2));
    assertDoesNotThrow(() -> CREATE.checkSince(5));
    WaylandException e = assertThrows(WaylandException.class, () -> CREATE.checkSince(1));
    assertTrue(e.getMessage().contains("requires version 2"), e.getMessage());
  }

  @Test
  void marshalArgCountExcludesNewId() {
    assertEquals(3, CREATE.marshalArgCount());
    assertTrue(CREATE.newIdArg().isPresent());
  }

  @Test
  void checkArgsAcceptsValidArguments() {
    assertDoesNotThrow(() -> CREATE.checkArgs(4, "name", null));
  }

  @Test
  void checkArgsRejectsWrongArity() {
    assertThrows(WaylandException.class, () -> CREATE.checkArgs(4, "name"));
  }

  @Test
  void checkArgsRejectsWrongCarrierType() {
    assertThrows(WaylandException.class, () -> CREATE.checkArgs("four", "name", null));
  }

  @Test
  void checkArgsRejectsNullForNonNullable() {
    assertThrows(WaylandException.class, () -> CREATE.checkArgs(4, null, null));
  }

  @Test
  void checkArgsValidatesEveryCarrier() {
    MessageDesc all = MessageDesc.of("all", 1, false,
        ArgDesc.intArg("i"), ArgDesc.fixedArg("f"), ArgDesc.arrayArg("a"),
        ArgDesc.fdArg("fd"));
    assertThrows(WaylandException.class,
        () -> all.checkArgs(1, 2.0 /* not Fixed */, WlArray.of(new byte[0]), null));
  }

  @Test
  void interfaceDescRejectsUnknownOpcodes() {
    InterfaceDesc desc = InterfaceDesc.of("test", 1,
        java.util.List.of(CREATE), java.util.List.of());
    assertEquals(CREATE, desc.request(0));
    assertThrows(WaylandException.class, () -> desc.request(1));
    assertThrows(WaylandException.class, () -> desc.event(0));
  }
}
