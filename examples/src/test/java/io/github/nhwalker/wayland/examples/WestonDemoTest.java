package io.github.nhwalker.wayland.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.nhwalker.wayland.core.WaylandConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * The real-compositor integration test: runs the shm-window demo against whatever
 * {@code WAYLAND_DISPLAY} points at (headless Weston in CI). Skips when no compositor is
 * available, so the ordinary build stays hermetic.
 */
@Timeout(60)
class WestonDemoTest {

  @Test
  void rendersFramesOnARealCompositor() throws Exception {
    String waylandDisplay = System.getenv("WAYLAND_DISPLAY");
    assumeTrue(waylandDisplay != null && !waylandDisplay.isEmpty(),
        "WAYLAND_DISPLAY not set; skipping real-compositor test");
    assumeTrue(System.getenv("XDG_RUNTIME_DIR") != null,
        "XDG_RUNTIME_DIR not set; skipping real-compositor test");

    try (WaylandConnection connection = WaylandConnection.open()) {
      ShmWindow.Result result = ShmWindow.run(connection, 10);
      assertEquals(10, result.framesRendered());
      assertTrue(result.width() > 0 && result.height() > 0);
    }
  }
}
