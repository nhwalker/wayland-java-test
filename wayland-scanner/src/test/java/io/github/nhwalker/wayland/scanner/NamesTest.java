package io.github.nhwalker.wayland.scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NamesTest {

  @Test
  void classNames() {
    assertEquals("WlShmPool", Names.className("wl_shm_pool"));
    assertEquals("XdgToplevel", Names.className("xdg_toplevel"));
  }

  @Test
  void camelNames() {
    assertEquals("createPool", Names.camelName("create_pool"));
    assertEquals("physicalWidth", Names.camelName("physical_width"));
    assertEquals("x", Names.camelName("x"));
  }

  @Test
  void keywordsGetRenamed() {
    assertEquals("interfaceName", Names.camelName("interface"));
    assertEquals("className", Names.camelName("class"));
  }

  @Test
  void constantNames() {
    assertEquals("ARGB8888", Names.constantName("argb8888"));
    assertEquals("FLIPPED_90", Names.constantName("flipped_90"));
    assertEquals("_90", Names.constantName("90"));
  }
}
