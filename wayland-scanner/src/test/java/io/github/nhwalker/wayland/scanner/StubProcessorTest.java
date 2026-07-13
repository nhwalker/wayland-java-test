package io.github.nhwalker.wayland.scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Drives the annotation processor through the real compiler ({@code -proc:only}). */
class StubProcessorTest {

  @TempDir
  Path tempDir;

  private record Compilation(boolean success, Path generatedDir, List<String> errors) {}

  private Compilation compile(String packageInfoSource, String... extraOptions)
      throws IOException {
    return compile(List.of(packageInfoSource), extraOptions);
  }

  private Compilation compile(List<String> packageInfoSources, String... extraOptions)
      throws IOException {
    List<Path> sources = new ArrayList<>();
    for (int i = 0; i < packageInfoSources.size(); i++) {
      Path dir = Files.createDirectories(tempDir.resolve("src" + i));
      Path source = dir.resolve("package-info.java");
      Files.writeString(source, packageInfoSources.get(i));
      sources.add(source);
    }
    Path generated = Files.createDirectories(tempDir.resolve("generated"));

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    try (StandardJavaFileManager fileManager =
        compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
      List<String> options = new ArrayList<>(List.of(
          "-proc:only",
          "-processor", StubProcessor.class.getName(),
          "-s", generated.toString(),
          "-classpath", System.getProperty("java.class.path")));
      options.addAll(List.of(extraOptions));
      boolean success = compiler.getTask(new StringWriter(), fileManager, diagnostics, options,
          null, fileManager.getJavaFileObjects(sources.toArray(Path[]::new))).call();
      List<String> errors = diagnostics.getDiagnostics().stream()
          .filter(d -> d.getKind() == javax.tools.Diagnostic.Kind.ERROR)
          .map(d -> d.getMessage(null))
          .toList();
      return new Compilation(success, generated, errors);
    }
  }

  @Test
  void generatesBuiltinProtocolIntoTheAnnotatedPackage() throws IOException {
    Compilation result = compile("""
        @WaylandProtocols(@WaylandProtocol("builtin:wayland"))
        package com.example.gen;

        import io.github.nhwalker.wayland.scanner.WaylandProtocol;
        import io.github.nhwalker.wayland.scanner.WaylandProtocols;
        """);

    assertTrue(result.success(), () -> String.join("\n", result.errors()));
    Path packageDir = result.generatedDir().resolve("com/example/gen");
    assertTrue(Files.exists(packageDir.resolve("WlDisplay.java")));
    assertTrue(Files.exists(packageDir.resolve("WlTouch.java")));

    // Identical to the CLI's output (the golden files), modulo the target package.
    String golden = golden("WlDisplay")
        .replace("package io.github.nhwalker.wayland.client;", "package com.example.gen;");
    assertEquals(golden, Files.readString(packageDir.resolve("WlDisplay.java")));
  }

  @Test
  void resolvesFilesAgainstProtocolDirAndImportsAcrossPackages() throws IOException {
    Files.writeString(tempDir.resolve("mini.xml"), """
        <?xml version="1.0" encoding="UTF-8"?>
        <protocol name="mini">
          <interface name="mini_holder" version="1">
            <description summary="a holder">Holds a surface.</description>
            <request name="hold">
              <arg name="surface" type="object" interface="wl_surface"/>
            </request>
          </interface>
        </protocol>
        """);

    Compilation result = compile(List.of("""
        @WaylandProtocols(@WaylandProtocol("builtin:wayland"))
        package com.example.core;

        import io.github.nhwalker.wayland.scanner.WaylandProtocol;
        import io.github.nhwalker.wayland.scanner.WaylandProtocols;
        """, """
        @WaylandProtocols(@WaylandProtocol(value = "mini.xml",
            imports = "com.example.core=builtin:wayland"))
        package com.example.mini;

        import io.github.nhwalker.wayland.scanner.WaylandProtocol;
        import io.github.nhwalker.wayland.scanner.WaylandProtocols;
        """),
        "-A" + StubProcessor.PROTOCOL_DIR_OPTION + "=" + tempDir);

    assertTrue(result.success(), () -> String.join("\n", result.errors()));
    String source =
        Files.readString(result.generatedDir().resolve("com/example/mini/MiniHolder.java"));
    assertTrue(source.contains("import com.example.core.WlSurface;"));
    assertTrue(source.contains("public void hold(WlSurface surface)"));
  }

  @Test
  void protocolsListedTogetherSeeEachOtherWithoutImports() throws IOException {
    Files.writeString(tempDir.resolve("mini.xml"), """
        <?xml version="1.0" encoding="UTF-8"?>
        <protocol name="mini">
          <interface name="mini_holder" version="1">
            <description summary="a holder">Holds a surface.</description>
            <request name="hold">
              <arg name="surface" type="object" interface="wl_surface"/>
            </request>
          </interface>
        </protocol>
        """);

    Compilation result = compile("""
        @WaylandProtocols({
            @WaylandProtocol("builtin:wayland"),
            @WaylandProtocol("mini.xml")})
        package com.example.together;

        import io.github.nhwalker.wayland.scanner.WaylandProtocol;
        import io.github.nhwalker.wayland.scanner.WaylandProtocols;
        """,
        "-A" + StubProcessor.PROTOCOL_DIR_OPTION + "=" + tempDir);

    assertTrue(result.success(), () -> String.join("\n", result.errors()));
    String source = Files.readString(
        result.generatedDir().resolve("com/example/together/MiniHolder.java"));
    assertTrue(source.contains("public void hold(WlSurface surface)"));
    assertFalse(source.contains("import com.example"), "same package needs no import");
  }

  @Test
  void missingProtocolDirIsAClearCompileError() throws IOException {
    Compilation result = compile("""
        @WaylandProtocols(@WaylandProtocol("mini.xml"))
        package com.example.broken;

        import io.github.nhwalker.wayland.scanner.WaylandProtocol;
        import io.github.nhwalker.wayland.scanner.WaylandProtocols;
        """);

    assertFalse(result.success());
    assertTrue(result.errors().stream().anyMatch(
        message -> message.contains(StubProcessor.PROTOCOL_DIR_OPTION)),
        () -> String.join("\n", result.errors()));
  }

  private static String golden(String className) throws IOException {
    try (InputStream golden =
        StubProcessorTest.class.getResourceAsStream("/golden/" + className + ".java")) {
      return new String(golden.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
