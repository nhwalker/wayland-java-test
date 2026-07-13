package io.github.nhwalker.wayland.scanner;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/**
 * The compile-time entry point: generates stubs for every {@link WaylandProtocols}-annotated
 * package through the same {@link StubEmitter} the CLI uses. Registered via
 * {@code META-INF/services}, so adding wayland-scanner to the annotation processor path is all
 * a build needs.
 */
public final class StubProcessor extends AbstractProcessor {

  /** Compiler option naming the directory that file-based protocol specs resolve against. */
  public static final String PROTOCOL_DIR_OPTION = "wayland.protocolDir";

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return Set.of(WaylandProtocols.class.getName());
  }

  @Override
  public Set<String> getSupportedOptions() {
    return Set.of(PROTOCOL_DIR_OPTION);
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element element : roundEnv.getElementsAnnotatedWith(WaylandProtocols.class)) {
      try {
        generate((PackageElement) element, element.getAnnotation(WaylandProtocols.class));
      } catch (Exception e) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
            "wayland-scanner: " + e.getMessage(), element);
      }
    }
    return true;
  }

  private void generate(PackageElement packageElement, WaylandProtocols config)
      throws IOException {
    String packageName = packageElement.getQualifiedName().toString();
    Map<String, String> external = new HashMap<>();
    List<Protocol> protocols = new ArrayList<>();
    for (WaylandProtocol spec : config.value()) {
      for (String importSpec : spec.imports()) {
        addImport(external, importSpec);
      }
      try (InputStream xml = open(spec.value())) {
        protocols.add(ProtocolParser.parse(xml));
      }
    }
    // Protocols listed together are generated together: their interfaces see each other.
    for (Protocol protocol : protocols) {
      for (Protocol.Interface iface : protocol.interfaces()) {
        external.put(iface.name(), packageName);
      }
    }
    for (Protocol protocol : protocols) {
      for (Protocol.Interface iface : protocol.interfaces()) {
        String source = StubEmitter.emit(protocol, iface, packageName, external);
        JavaFileObject file = processingEnv.getFiler().createSourceFile(
            packageName + "." + Names.className(iface.name()), packageElement);
        try (Writer writer = file.openWriter()) {
          writer.write(source);
        }
      }
    }
  }

  private void addImport(Map<String, String> external, String importSpec) throws IOException {
    int equals = importSpec.indexOf('=');
    if (equals < 0) {
      throw new IOException("imports entries must be PKG=SPEC, got: " + importSpec);
    }
    String importPackage = importSpec.substring(0, equals);
    try (InputStream xml = open(importSpec.substring(equals + 1))) {
      for (Protocol.Interface iface : ProtocolParser.parse(xml).interfaces()) {
        external.put(iface.name(), importPackage);
      }
    }
  }

  private InputStream open(String spec) throws IOException {
    if (spec.startsWith("builtin:")) {
      return Main.builtinXml(spec.substring("builtin:".length()));
    }
    String directory = processingEnv.getOptions().get(PROTOCOL_DIR_OPTION);
    if (directory == null) {
      throw new IOException("protocol file '" + spec + "' requires the -A"
          + PROTOCOL_DIR_OPTION + "=DIR compiler option");
    }
    Path path = Path.of(directory, spec);
    if (!Files.exists(path)) {
      throw new IOException("protocol file not found: " + path);
    }
    return Files.newInputStream(path);
  }
}
