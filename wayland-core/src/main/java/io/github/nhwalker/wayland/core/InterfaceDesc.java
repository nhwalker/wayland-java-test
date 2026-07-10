package io.github.nhwalker.wayland.core;

import java.util.List;

/**
 * A protocol interface's machine-readable signature table — name, maximum known version, and
 * its requests and events in opcode order (position = opcode).
 */
public record InterfaceDesc(String name, int version, List<MessageDesc> requests,
    List<MessageDesc> events) {

  public InterfaceDesc {
    requests = List.copyOf(requests);
    events = List.copyOf(events);
  }

  public static InterfaceDesc of(String name, int version, List<MessageDesc> requests,
      List<MessageDesc> events) {
    return new InterfaceDesc(name, version, requests, events);
  }

  public MessageDesc request(int opcode) {
    if (opcode < 0 || opcode >= requests.size()) {
      throw new WaylandException(name + ": unknown request opcode " + opcode);
    }
    return requests.get(opcode);
  }

  public MessageDesc event(int opcode) {
    if (opcode < 0 || opcode >= events.size()) {
      throw new WaylandException(name + ": unknown event opcode " + opcode);
    }
    return events.get(opcode);
  }
}
