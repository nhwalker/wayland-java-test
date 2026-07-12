package io.github.nhwalker.wayland.scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The emitter's specification: generating the bundled wayland protocol must reproduce the
 * golden files byte-for-byte. The goldens are the reviewed generated form of wayland-core's
 * original hand-written reference stubs.
 */
class GoldenConformanceTest {

  @Test
  void bundledWaylandProtocolMatchesGoldenFiles() throws IOException {
    Protocol protocol;
    try (InputStream xml = Main.builtinXml("wayland")) {
      protocol = ProtocolParser.parse(xml);
    }
    assertEquals(10, protocol.interfaces().size());

    for (Protocol.Interface iface : protocol.interfaces()) {
      String className = Names.className(iface.name());
      String actual = StubEmitter.emit(protocol, iface, "io.github.nhwalker.wayland.client");
      String expected = golden(className);
      if (!expected.equals(actual)) {
        fail(className + " diverged from golden file, first difference at line "
            + firstDifferingLine(expected, actual));
      }
    }
  }

  private static String golden(String className) throws IOException {
    try (InputStream golden =
        GoldenConformanceTest.class.getResourceAsStream("/golden/" + className + ".java")) {
      if (golden == null) {
        throw new IOException("missing golden file for " + className);
      }
      return new String(golden.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static String firstDifferingLine(String expected, String actual) {
    List<String> expectedLines = expected.lines().toList();
    List<String> actualLines = actual.lines().toList();
    int count = Math.min(expectedLines.size(), actualLines.size());
    for (int i = 0; i < count; i++) {
      if (!expectedLines.get(i).equals(actualLines.get(i))) {
        return (i + 1) + ":\n  expected: " + expectedLines.get(i)
            + "\n  actual:   " + actualLines.get(i);
      }
    }
    return (count + 1) + " (files differ in length: expected " + expectedLines.size()
        + " lines, actual " + actualLines.size() + ")";
  }
}
