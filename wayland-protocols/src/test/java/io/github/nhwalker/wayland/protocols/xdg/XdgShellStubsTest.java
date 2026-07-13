package io.github.nhwalker.wayland.protocols.xdg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nhwalker.wayland.client.WlSeat;
import io.github.nhwalker.wayland.client.WlSurface;
import io.github.nhwalker.wayland.core.ArgType;
import io.github.nhwalker.wayland.core.MessageDesc;
import org.junit.jupiter.api.Test;

/** Shape checks on the generated xdg-shell stubs, incl. cross-protocol references. */
class XdgShellStubsTest {

  @Test
  void descriptorsExist() {
    assertEquals("xdg_wm_base", XdgWmBase.INTERFACE.name());
    assertEquals("xdg_surface", XdgSurface.INTERFACE.name());
    assertEquals("xdg_toplevel", XdgToplevel.INTERFACE.name());
    assertEquals("xdg_popup", XdgPopup.INTERFACE.name());
    assertEquals("xdg_positioner", XdgPositioner.INTERFACE.name());
  }

  @Test
  void crossProtocolReferencesResolveToTheClientPackage() {
    // xdg_wm_base.get_xdg_surface takes a wl_surface from wayland-core's generated package.
    MessageDesc getXdgSurface = XdgWmBase.INTERFACE.request(2);
    assertEquals("get_xdg_surface", getXdgSurface.name());
    assertEquals(ArgType.OBJECT, getXdgSurface.args().get(1).type());
    assertEquals(WlSurface.INTERFACE, getXdgSurface.args().get(1).interfaceRef().get());

    // xdg_toplevel.move takes a wl_seat.
    MessageDesc move = XdgToplevel.INTERFACE.request(5);
    assertEquals("move", move.name());
    assertEquals(WlSeat.INTERFACE, move.args().get(0).interfaceRef().get());
  }

  @Test
  void toplevelConfigureCarriesTheStatesArray() {
    MessageDesc configure = XdgToplevel.INTERFACE.event(0);
    assertEquals("configure", configure.name());
    assertEquals(ArgType.ARRAY, configure.args().get(2).type());
  }

  @Test
  void wmBaseIsCloseableAndPingable() {
    assertTrue(AutoCloseable.class.isAssignableFrom(XdgWmBase.class));
    assertEquals("pong", XdgWmBase.INTERFACE.request(3).name());
    assertEquals("ping", XdgWmBase.INTERFACE.event(0).name());
  }
}
