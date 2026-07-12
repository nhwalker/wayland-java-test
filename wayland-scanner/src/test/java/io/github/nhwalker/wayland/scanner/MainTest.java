package io.github.nhwalker.wayland.scanner;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {

  @TempDir
  Path tempDir;

  @Test
  void generatesFilesFromBuiltinProtocol() throws Exception {
    Path out = tempDir.resolve("gen");
    Main.main(new String[] {
        "--out", out.toString(),
        "--package", "com.example.wl",
        "--builtin", "wayland"});

    Path packageDir = out.resolve("com/example/wl");
    assertTrue(Files.exists(packageDir.resolve("WlDisplay.java")));
    assertTrue(Files.exists(packageDir.resolve("WlSurface.java")));
    String surface = Files.readString(packageDir.resolve("WlSurface.java"));
    assertTrue(surface.startsWith("package com.example.wl;"));
  }

  @Test
  void generatesFromAFileArgument() throws Exception {
    Path xml = tempDir.resolve("mini.xml");
    Files.writeString(xml, """
        <?xml version="1.0" encoding="UTF-8"?>
        <protocol name="mini">
          <interface name="mini_thing" version="1">
            <description summary="a thing">A test thing.</description>
            <request name="poke">
              <arg name="value" type="int"/>
            </request>
          </interface>
        </protocol>
        """);
    Path out = tempDir.resolve("gen");
    Main.main(new String[] {
        "--out", out.toString(), "--package", "com.example.mini", xml.toString()});

    String source = Files.readString(out.resolve("com/example/mini/MiniThing.java"));
    assertTrue(source.contains("public final class MiniThing"));
    assertTrue(source.contains("public void poke(int value)"));
    assertTrue(source.contains(
        "@WaylandGenerated(protocol = \"mini\", interfaceName = \"mini_thing\", version = 1)"));
  }
}
