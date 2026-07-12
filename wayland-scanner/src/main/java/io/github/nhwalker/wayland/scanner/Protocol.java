package io.github.nhwalker.wayland.scanner;

import java.util.List;

/** A parsed Wayland protocol definition — the object form of a protocol XML file. */
public record Protocol(String name, List<Interface> interfaces) {

  public Protocol {
    interfaces = List.copyOf(interfaces);
  }

  /** One {@code <interface>}. */
  public record Interface(String name, int version, List<String> description,
      List<Message> requests, List<Message> events, List<EnumDef> enums) {

    public Interface {
      description = List.copyOf(description);
      requests = List.copyOf(requests);
      events = List.copyOf(events);
      enums = List.copyOf(enums);
    }
  }

  /** One {@code <request>} or {@code <event>}; the opcode is its list position. */
  public record Message(String name, int since, boolean destructor, List<String> description,
      List<Arg> args) {

    public Message {
      description = List.copyOf(description);
      args = List.copyOf(args);
    }
  }

  /**
   * One {@code <arg>}. {@code type} is the wire type name ({@code int}, {@code uint},
   * {@code fixed}, {@code string}, {@code object}, {@code new_id}, {@code array}, {@code fd});
   * {@code interfaceName} is the {@code interface} attribute or null; {@code enumRef} is the
   * {@code enum} attribute or null ({@code "name"} local, {@code "wl_iface.name"} qualified).
   */
  public record Arg(String name, String type, String interfaceName, boolean allowNull,
      String enumRef) {}

  /** One {@code <enum>}. */
  public record EnumDef(String name, boolean bitfield, List<String> description,
      List<Entry> entries) {

    public EnumDef {
      description = List.copyOf(description);
      entries = List.copyOf(entries);
    }
  }

  /**
   * One {@code <entry>}. {@code value} is the parsed number; {@code literal} preserves the
   * XML's spelling (decimal or {@code 0x} hex) so generated code keeps the source's form.
   */
  public record Entry(String name, int value, String literal) {}
}
