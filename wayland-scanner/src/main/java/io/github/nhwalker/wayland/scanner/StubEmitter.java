package io.github.nhwalker.wayland.scanner;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Emits one generated stub class per protocol interface, reproducing the reference-stub
 * conventions: descriptor table, {@code TYPE} handle, typed request facades, sealed event
 * records with a decode switch, {@code WireEnum} enums, {@code AutoCloseable} for a
 * {@code destroy} destructor, and the special-cased {@code wl_display} bootstrap.
 */
public final class StubEmitter {

  private static final int MAX_WIDTH = 100;

  private static final String CORE = "io.github.nhwalker.wayland.core.";

  private final Protocol protocol;
  private final Protocol.Interface iface;
  private final String packageName;
  private final String className;
  private final boolean isDisplay;
  private final TreeSet<String> imports = new TreeSet<>();
  private final StringBuilder body = new StringBuilder();

  private StubEmitter(Protocol protocol, Protocol.Interface iface, String packageName) {
    this.protocol = protocol;
    this.iface = iface;
    this.packageName = packageName;
    this.className = Names.className(iface.name());
    this.isDisplay = iface.name().equals("wl_display");
  }

  public static String emit(Protocol protocol, Protocol.Interface iface, String packageName) {
    return new StubEmitter(protocol, iface, packageName).emitFile();
  }

  private String emitFile() {
    imports.add(CORE + "InterfaceDesc");
    imports.add(CORE + "MessageDesc");
    imports.add(CORE + "Proxy");
    imports.add(CORE + "WaylandGenerated");
    imports.add("java.util.List");
    if (!isDisplay) {
      imports.add(CORE + "ProxyType");
    }

    emitClassBody();

    StringBuilder file = new StringBuilder();
    file.append("package ").append(packageName).append(";\n\n");
    for (String importName : imports) {
      file.append("import ").append(importName).append(";\n");
    }
    file.append('\n');
    file.append(body);
    return file.toString();
  }

  private void emitClassBody() {
    if (isDisplay) {
      javadoc(0, List.of(
          "The connection's root object (id 1). The kernel names no interface — this generated"
              + " stub",
          "supplies the descriptor via {@link #from} and installs control wiring that routes",
          "{@code error}/{@code delete_id} to the connection's generic",
          "{@link WaylandConnection#fatalError}/{@link WaylandConnection#retireId} hooks."
              + " wl_display is",
          "the one interface the generator special-cases (by its literal name) to emit this"
              + " wiring."));
    } else {
      javadoc(0, iface.description());
    }
    line(0, "@WaylandGenerated(protocol = \"" + protocol.name() + "\", interfaceName = \""
        + iface.name() + "\", version = " + iface.version() + ")");
    boolean closeable = !isDisplay && iface.requests().stream()
        .anyMatch(request -> request.destructor() && request.name().equals("destroy"));
    line(0, "public final class " + className + (closeable ? " implements AutoCloseable" : "")
        + " {");
    blank();

    emitDescriptor();
    if (isDisplay) {
      emitDisplayPreamble();
    } else {
      emitTypeConstant();
      emitFieldsAndConstructor();
      emitAccessors();
    }
    emitRequests(closeable);
    if (isDisplay) {
      emitDisplayOnEvent();
    }
    emitEvents();
    emitEnums();

    body.setLength(body.length() - 1); // drop the trailing blank separator line
    line(0, "}");
  }

  // ---- descriptor ----

  private void emitDescriptor() {
    line(2, "public static final InterfaceDesc INTERFACE = InterfaceDesc.of(\"" + iface.name()
        + "\", " + iface.version() + ",");
    emitMessageList(iface.requests(), ",");
    emitMessageList(iface.events(), ");");
    blank();
  }

  private void emitMessageList(List<Protocol.Message> messages, String suffix) {
    if (messages.isEmpty()) {
      line(6, "List.of()" + suffix);
      return;
    }
    line(6, "List.of(");
    for (int i = 0; i < messages.size(); i++) {
      Protocol.Message message = messages.get(i);
      String closing = (i == messages.size() - 1 ? ")" : ",") + (i == messages.size() - 1
          ? suffix : "");
      String head = "MessageDesc.of(\"" + message.name() + "\", " + message.since() + ", "
          + message.destructor();
      if (message.args().isEmpty()) {
        line(10, head + ")" + closing);
      } else {
        line(10, head + ",");
        List<Protocol.Arg> args = message.args();
        for (int a = 0; a < args.size(); a++) {
          line(14, argDesc(args.get(a)) + (a == args.size() - 1 ? ")" + closing : ","));
        }
      }
    }
  }

  private String argDesc(Protocol.Arg arg) {
    String descriptor = switch (arg.type()) {
      case "int" -> "ArgDesc.intArg(\"" + arg.name() + "\")";
      case "uint" -> "ArgDesc.uintArg(\"" + arg.name() + "\")";
      case "fixed" -> "ArgDesc.fixedArg(\"" + arg.name() + "\")";
      case "string" -> "ArgDesc.stringArg(\"" + arg.name() + "\")";
      case "array" -> "ArgDesc.arrayArg(\"" + arg.name() + "\")";
      case "fd" -> "ArgDesc.fdArg(\"" + arg.name() + "\")";
      case "object" -> "ArgDesc.objectArg(\"" + arg.name() + "\", " + interfaceRef(arg) + ")";
      case "new_id" -> "ArgDesc.newIdArg(\"" + arg.name() + "\", " + interfaceRef(arg) + ")";
      default -> throw new IllegalArgumentException("unknown arg type: " + arg.type());
    };
    imports.add(CORE + "ArgDesc");
    return arg.allowNull() ? descriptor + ".asNullable()" : descriptor;
  }

  private String interfaceRef(Protocol.Arg arg) {
    return arg.interfaceName() == null
        ? "null"
        : "() -> " + Names.className(arg.interfaceName()) + ".INTERFACE";
  }

  // ---- standard preamble ----

  private void emitTypeConstant() {
    wrap(2, "public static final ProxyType<" + className + "> TYPE = ProxyType.of(",
        List.of("INTERFACE", className + "::new"), ");");
    blank();
  }

  private void emitFieldsAndConstructor() {
    line(2, "private final Proxy proxy;");
    blank();
    line(2, className + "(Proxy proxy) {");
    line(4, "this.proxy = proxy;");
    line(2, "}");
    blank();
  }

  private void emitAccessors() {
    accessor("Proxy", "proxy", "proxy");
    accessor("int", "id", "proxy.id()");
    accessor("int", "version", "proxy.version()");
    accessor("boolean", "isDestroyed", "proxy.isDestroyed()");
  }

  private void accessor(String type, String name, String expression) {
    line(2, "public " + type + " " + name + "() {");
    line(4, "return " + expression + ";");
    line(2, "}");
    blank();
  }

  // ---- wl_display template ----

  private void emitDisplayPreamble() {
    imports.add(CORE + "WaylandConnection");
    imports.add("java.io.IOException");
    imports.add("java.nio.file.Path");
    imports.add("java.util.function.Consumer");
    line(2, "// No ProxyType: wl_display is never bound or created by another object; use"
        + " from(...).");
    blank();
    javadoc(2, List.of("Connects using the standard environment resolution and wraps the"
        + " display."));
    line(2, "public static " + className + " connect() throws IOException {");
    line(4, "return from(WaylandConnection.open());");
    line(2, "}");
    blank();
    javadoc(2, List.of("Connects to an explicit socket path and wraps the display."));
    line(2, "public static " + className + " connect(Path socket) throws IOException {");
    line(4, "return from(WaylandConnection.open(socket));");
    line(2, "}");
    blank();
    javadoc(2, List.of("Wraps object id 1 of {@code connection} and installs the control"
        + " wiring."));
    line(2, "public static " + className + " from(WaylandConnection connection) {");
    line(4, "return new " + className + "(connection.display(INTERFACE));");
    line(2, "}");
    blank();
    line(2, "private final Proxy proxy;");
    line(2, "private volatile Consumer<? super Event> handler = event -> {};");
    blank();
    line(2, "private " + className + "(Proxy proxy) {");
    line(4, "this.proxy = proxy;");
    line(4, "proxy.setEventHandler(this::control);");
    line(2, "}");
    blank();
    accessor("Proxy", "proxy", "proxy");
    accessor("int", "id", "proxy.id()");
    accessor("int", "version", "proxy.version()");
    accessor("WaylandConnection", "connection", "proxy.connection()");
    Protocol.Message error = eventNamed("error");
    Protocol.Message deleteId = eventNamed("delete_id");
    line(2, "private void control(int opcode, Object[] args) {");
    line(4, "Event event = decode(opcode, args);");
    line(4, "switch (event) {");
    line(6, "case Event." + Names.className(error.name()) + " e -> proxy.connection()"
        + ".fatalError(" + accessors("e", error) + ");");
    line(6, "case Event." + Names.className(deleteId.name()) + " d -> proxy.connection()"
        + ".retireId(" + accessors("d", deleteId) + ");");
    line(4, "}");
    line(4, "handler.accept(event);");
    line(2, "}");
    blank();
  }

  private Protocol.Message eventNamed(String name) {
    return iface.events().stream()
        .filter(event -> event.name().equals(name))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException(
            iface.name() + " is missing the required '" + name + "' event"));
  }

  private static String accessors(String variable, Protocol.Message event) {
    return String.join(", ", event.args().stream()
        .map(arg -> variable + "." + Names.camelName(arg.name()) + "()").toList());
  }

  private void emitDisplayOnEvent() {
    javadoc(2, List.of("Replaces the user-visible handler; the control wiring is never"
        + " displaced."));
    line(2, "public void onEvent(Consumer<? super Event> handler) {");
    line(4, "this.handler = handler;");
    line(2, "}");
    blank();
  }

  // ---- requests ----

  private void emitRequests(boolean closeable) {
    List<Protocol.Message> requests = iface.requests();
    for (int opcode = 0; opcode < requests.size(); opcode++) {
      Protocol.Message request = requests.get(opcode);
      emitRequest(opcode, request);
      if (closeable && request.destructor() && request.name().equals("destroy")) {
        emitClose();
      }
    }
  }

  private void emitRequest(int opcode, Protocol.Message request) {
    javadoc(2, messageDoc(request));
    Protocol.Arg newId = request.args().stream()
        .filter(arg -> arg.type().equals("new_id")).findFirst().orElse(null);
    if (newId != null && newId.interfaceName() == null) {
      emitDynamicBind(opcode, request, newId);
      return;
    }
    List<String> parameters = new ArrayList<>();
    List<String> marshalArgs = new ArrayList<>();
    for (Protocol.Arg arg : request.args()) {
      if (arg.type().equals("new_id")) {
        continue;
      }
      String name = Names.camelName(arg.name());
      parameters.add(paramType(arg) + " " + name);
      marshalArgs.add(marshalExpression(arg, name));
    }
    String methodName = Names.camelName(request.name());
    if (newId != null) {
      String childClass = Names.className(newId.interfaceName());
      wrap(2, "public " + childClass + " " + methodName + "(", parameters, ") {");
      List<String> callArgs = new ArrayList<>(List.of(String.valueOf(opcode),
          childClass + ".INTERFACE"));
      callArgs.addAll(marshalArgs);
      wrap(4, "return " + childClass + ".TYPE.wrap(proxy.marshalConstructor(", callArgs, "));");
    } else {
      wrap(2, "public void " + methodName + "(", parameters, ") {");
      String marshal = request.destructor() ? "proxy.marshalDestructor(" : "proxy.marshal(";
      List<String> callArgs = new ArrayList<>(List.of(String.valueOf(opcode)));
      callArgs.addAll(marshalArgs);
      wrap(4, marshal, callArgs, ");");
    }
    line(2, "}");
    blank();
  }

  private void emitDynamicBind(int opcode, Protocol.Message request, Protocol.Arg newId) {
    List<String> parameters = new ArrayList<>();
    List<String> marshalArgs = new ArrayList<>();
    for (Protocol.Arg arg : request.args()) {
      if (arg == newId) {
        continue;
      }
      String name = Names.camelName(arg.name());
      parameters.add(paramType(arg) + " " + name);
      marshalArgs.add(marshalExpression(arg, name));
    }
    parameters.add("ProxyType<T> type");
    parameters.add("int version");
    wrap(2, "public <T> T " + Names.camelName(request.name()) + "(", parameters, ") {");
    List<String> callArgs = new ArrayList<>(List.of(String.valueOf(opcode), "version",
        "type.descriptor()"));
    callArgs.addAll(marshalArgs);
    wrap(4, "return type.wrap(proxy.marshalConstructor(", callArgs, "));");
    line(2, "}");
    blank();
  }

  private void emitClose() {
    javadoc(2, List.of("{@link #destroy()}; no-op if already destroyed."));
    line(2, "@Override");
    line(2, "public void close() {");
    line(4, "if (!proxy.isDestroyed()) {");
    line(6, "destroy();");
    line(4, "}");
    line(2, "}");
    blank();
  }

  private String paramType(Protocol.Arg arg) {
    return switch (arg.type()) {
      case "int", "uint" -> arg.enumRef() != null ? enumTypeName(arg.enumRef()) : "int";
      case "fixed" -> imported(CORE + "Fixed");
      case "string" -> "String";
      case "array" -> imported(CORE + "WlArray");
      case "fd" -> imported("io.github.nhwalker.unixsocket.Fd");
      case "object" -> arg.interfaceName() == null ? "Proxy"
          : Names.className(arg.interfaceName());
      default -> throw new IllegalArgumentException("no parameter type for " + arg.type());
    };
  }

  private String marshalExpression(Protocol.Arg arg, String name) {
    if (arg.type().equals("object")) {
      String proxyOf = arg.interfaceName() == null ? name : name + ".proxy()";
      return arg.allowNull() ? name + " == null ? null : " + proxyOf : proxyOf;
    }
    if ((arg.type().equals("int") || arg.type().equals("uint")) && arg.enumRef() != null) {
      return name + ".value()";
    }
    return name;
  }

  private String enumTypeName(String enumRef) {
    int dot = enumRef.indexOf('.');
    if (dot < 0) {
      return Names.enumClassName(enumRef);
    }
    return Names.className(enumRef.substring(0, dot)) + "."
        + Names.enumClassName(enumRef.substring(dot + 1));
  }

  // ---- events ----

  private void emitEvents() {
    List<Protocol.Message> events = iface.events();
    if (events.isEmpty()) {
      return;
    }
    imports.add(CORE + "WaylandException");
    imports.add("java.util.function.Consumer");

    List<String> permits = events.stream()
        .map(event -> "Event." + Names.className(event.name())).toList();
    wrap(2, "public sealed interface Event permits ", permits, " {");
    blank();
    for (int i = 0; i < events.size(); i++) {
      Protocol.Message event = events.get(i);
      javadoc(4, messageDoc(event));
      List<String> components = new ArrayList<>();
      for (Protocol.Arg arg : event.args()) {
        components.add(componentType(arg) + " " + Names.camelName(arg.name()));
      }
      wrap(4, "record " + Names.className(event.name()) + "(", components,
          ") implements Event {}");
      if (i < events.size() - 1) {
        blank();
      }
    }
    line(2, "}");
    blank();

    if (!isDisplay) {
      javadoc(2, List.of("Installs the event handler for this object, replacing any previous"
          + " one."));
      line(2, "public void onEvent(Consumer<? super Event> handler) {");
      line(4, "proxy.setEventHandler((opcode, args) -> handler.accept(decode(opcode,"
          + " args)));");
      line(2, "}");
      blank();
    }

    line(2, "private static Event decode(int opcode, Object[] args) {");
    line(4, "return switch (opcode) {");
    for (int opcode = 0; opcode < events.size(); opcode++) {
      Protocol.Message event = events.get(opcode);
      List<String> casts = new ArrayList<>();
      for (int a = 0; a < event.args().size(); a++) {
        casts.add("(" + castType(event.args().get(a)) + ") args[" + a + "]");
      }
      wrap(6, "case " + opcode + " -> new Event." + Names.className(event.name()) + "(",
          casts, ");");
    }
    line(6, "default -> throw new WaylandException(\"" + iface.name()
        + ": unknown event opcode \" + opcode);");
    line(4, "};");
    line(2, "}");
    blank();
  }

  private String componentType(Protocol.Arg arg) {
    return switch (arg.type()) {
      case "int", "uint" -> "int"; // raw wire value even for enums: forward compatibility
      case "fixed" -> imported(CORE + "Fixed");
      case "string" -> "String";
      case "array" -> imported(CORE + "WlArray");
      case "fd" -> imported("io.github.nhwalker.unixsocket.Fd");
      case "object", "new_id" -> "Proxy";
      default -> throw new IllegalArgumentException("no component type for " + arg.type());
    };
  }

  private String castType(Protocol.Arg arg) {
    return switch (arg.type()) {
      case "int", "uint" -> "Integer";
      case "fixed" -> imported(CORE + "Fixed");
      case "string" -> "String";
      case "array" -> imported(CORE + "WlArray");
      case "fd" -> imported("io.github.nhwalker.unixsocket.Fd");
      case "object", "new_id" -> "Proxy";
      default -> throw new IllegalArgumentException("no cast type for " + arg.type());
    };
  }

  // ---- enums ----

  private void emitEnums() {
    for (Protocol.EnumDef enumDef : iface.enums()) {
      imports.add(CORE + "WireEnum");
      imports.add(CORE + "WireEnums");
      String enumName = Names.enumClassName(enumDef.name());
      javadoc(2, enumDef.description());
      line(2, "public enum " + enumName + " implements WireEnum {");
      List<Protocol.Entry> entries = enumDef.entries();
      for (int i = 0; i < entries.size(); i++) {
        Protocol.Entry entry = entries.get(i);
        line(4, Names.constantName(entry.name()) + "(" + entry.literal() + ")"
            + (i == entries.size() - 1 ? ";" : ","));
      }
      blank();
      line(4, "private final int value;");
      blank();
      line(4, enumName + "(int value) {");
      line(6, "this.value = value;");
      line(4, "}");
      blank();
      line(4, "@Override");
      line(4, "public int value() {");
      line(6, "return value;");
      line(4, "}");
      blank();
      if (enumDef.bitfield()) {
        imports.add("java.util.EnumSet");
        line(4, "public static EnumSet<" + enumName + "> setOf(int bits) {");
        line(6, "return WireEnums.setOf(" + enumName + ".class, bits);");
        line(4, "}");
      } else {
        imports.add("java.util.Optional");
        line(4, "public static Optional<" + enumName + "> lookup(int value) {");
        line(6, "return WireEnums.lookup(" + enumName + ".class, value);");
        line(4, "}");
      }
      line(2, "}");
      blank();
    }
  }

  // ---- shared helpers ----

  /** Doc lines for a message: the description, prefixed/merged with a since note. */
  private List<String> messageDoc(Protocol.Message message) {
    List<String> description = message.description();
    if (message.since() <= 1) {
      return description;
    }
    String since = "Since protocol version " + message.since() + ".";
    if (description.isEmpty()) {
      return List.of(since);
    }
    if (description.size() == 1) {
      return List.of(since + " " + description.get(0));
    }
    List<String> lines = new ArrayList<>();
    lines.add(since);
    lines.addAll(description);
    return lines;
  }

  private String imported(String qualifiedName) {
    imports.add(qualifiedName);
    return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
  }

  private void javadoc(int indent, List<String> lines) {
    if (lines.isEmpty()) {
      return;
    }
    if (lines.size() == 1 && indent + 4 + 3 + lines.get(0).length() <= MAX_WIDTH) {
      line(indent, "/** " + lines.get(0) + " */");
      return;
    }
    line(indent, "/**");
    for (String docLine : lines) {
      line(indent, docLine.isEmpty() ? " *" : " * " + docLine);
    }
    line(indent, " */");
  }

  private void line(int indent, String text) {
    body.append(" ".repeat(indent)).append(text).append('\n');
  }

  private void blank() {
    body.append('\n');
  }

  /**
   * Greedy line fill: {@code prefix} + comma-separated {@code parts} + {@code suffix}, packed
   * to {@value #MAX_WIDTH} columns with continuations at {@code indent + 4}.
   */
  private void wrap(int indent, String prefix, List<String> parts, String suffix) {
    String oneLine = prefix + String.join(", ", parts) + suffix;
    if (indent + oneLine.length() <= MAX_WIDTH) {
      line(indent, oneLine);
      return;
    }
    StringBuilder current = new StringBuilder(" ".repeat(indent)).append(prefix);
    boolean lineHasParts = false;
    for (int i = 0; i < parts.size(); i++) {
      String piece = parts.get(i) + (i == parts.size() - 1 ? suffix : ",");
      String separator = lineHasParts ? " " : "";
      if (current.length() + separator.length() + piece.length() > MAX_WIDTH && lineHasParts) {
        body.append(current).append('\n');
        current = new StringBuilder(" ".repeat(indent + 4)).append(piece);
      } else {
        current.append(separator).append(piece);
      }
      lineHasParts = true;
    }
    body.append(current).append('\n');
  }
}
