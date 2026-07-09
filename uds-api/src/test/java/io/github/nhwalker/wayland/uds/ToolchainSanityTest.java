package io.github.nhwalker.wayland.uds;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ToolchainSanityTest {

  @Test
  void runsOnJava25() {
    assertEquals(25, Runtime.version().feature());
  }
}
