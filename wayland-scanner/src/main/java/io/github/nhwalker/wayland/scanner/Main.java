package io.github.nhwalker.wayland.scanner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The wayland-scanner CLI: generates Java stubs from Wayland protocol XML.
 *
 * <pre>
 *   wayland-scanner --out DIR --package PKG [--import PKG=SPEC]... [--builtin NAME | FILE.xml]...
 * </pre>
 *
 * <p>{@code --builtin wayland} uses the bundled core-protocol definition instead of a file.
 * {@code --import} resolves cross-protocol interface references: {@code SPEC} is a protocol XML
 * file (or {@code builtin:NAME}) whose interfaces were already generated into {@code PKG}.
 */
public final class Main {

  private Main() {}

  public static void main(String[] args) throws IOException {
    Path out = null;
    String packageName = null;
    List<String> builtins = new ArrayList<>();
    List<Path> files = new ArrayList<>();
    Map<String, String> externalInterfaces = new HashMap<>();
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--out" -> out = Path.of(requireValue(args, ++i, "--out"));
        case "--package" -> packageName = requireValue(args, ++i, "--package");
        case "--builtin" -> builtins.add(requireValue(args, ++i, "--builtin"));
        case "--import" -> addImport(externalInterfaces, requireValue(args, ++i, "--import"));
        default -> files.add(Path.of(args[i]));
      }
    }
    if (out == null || packageName == null || (builtins.isEmpty() && files.isEmpty())) {
      System.err.println("usage: wayland-scanner --out DIR --package PKG"
          + " [--import PKG=SPEC]... [--builtin NAME | FILE.xml]...");
      System.exit(2);
    }

    Path packageDir = out.resolve(packageName.replace('.', '/'));
    Files.createDirectories(packageDir);
    for (String builtin : builtins) {
      try (InputStream xml = builtinXml(builtin)) {
        generate(ProtocolParser.parse(xml), packageName, packageDir, externalInterfaces);
      }
    }
    for (Path file : files) {
      try (InputStream xml = Files.newInputStream(file)) {
        generate(ProtocolParser.parse(xml), packageName, packageDir, externalInterfaces);
      }
    }
  }

  /** {@code PKG=SPEC}: maps every interface of SPEC's protocol to the package PKG. */
  private static void addImport(Map<String, String> externalInterfaces, String value)
      throws IOException {
    int equals = value.indexOf('=');
    if (equals < 0) {
      throw new IllegalArgumentException("--import expects PKG=SPEC, got: " + value);
    }
    String importPackage = value.substring(0, equals);
    String spec = value.substring(equals + 1);
    Protocol protocol;
    try (InputStream xml = spec.startsWith("builtin:")
        ? builtinXml(spec.substring("builtin:".length()))
        : Files.newInputStream(Path.of(spec))) {
      protocol = ProtocolParser.parse(xml);
    }
    for (Protocol.Interface iface : protocol.interfaces()) {
      externalInterfaces.put(iface.name(), importPackage);
    }
  }

  /** Opens a protocol definition bundled with the scanner (e.g. {@code "wayland"}). */
  public static InputStream builtinXml(String name) throws IOException {
    InputStream xml = Main.class.getResourceAsStream("protocols/" + name + ".xml");
    if (xml == null) {
      throw new IOException("no builtin protocol named '" + name + "'");
    }
    return xml;
  }

  private static void generate(Protocol protocol, String packageName, Path packageDir,
      Map<String, String> externalInterfaces) throws IOException {
    for (Protocol.Interface iface : protocol.interfaces()) {
      String source = StubEmitter.emit(protocol, iface, packageName, externalInterfaces);
      Files.writeString(packageDir.resolve(Names.className(iface.name()) + ".java"), source);
    }
  }

  private static String requireValue(String[] args, int index, String option) {
    if (index >= args.length) {
      throw new IllegalArgumentException(option + " requires a value");
    }
    return args[index];
  }
}
