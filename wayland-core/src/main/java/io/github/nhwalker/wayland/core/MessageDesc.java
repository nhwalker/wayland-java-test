package io.github.nhwalker.wayland.core;

import io.github.nhwalker.unixsocket.Fd;
import java.util.List;
import java.util.Optional;

/**
 * One request or event: name, availability version ({@code since}), destructor flag, and
 * argument shape. The opcode is implicit — a message's position in its interface's list.
 */
public record MessageDesc(String name, int since, boolean destructor, List<ArgDesc> args) {

  public MessageDesc {
    args = List.copyOf(args);
  }

  public static MessageDesc of(String name, int since, boolean destructor, ArgDesc... args) {
    return new MessageDesc(name, since, destructor, List.of(args));
  }

  /** The at-most-one {@link ArgType#NEW_ID} argument, if this message creates an object. */
  public Optional<ArgDesc> newIdArg() {
    return args.stream().filter(arg -> arg.type() == ArgType.NEW_ID).findFirst();
  }

  /** Argument count a marshal caller passes — NEW_ID slots are kernel-filled, so excluded. */
  public int marshalArgCount() {
    return (int) args.stream().filter(arg -> arg.type() != ArgType.NEW_ID).count();
  }

  /** Enforces this message's {@code since} version against the calling object's version. */
  public void checkSince(int proxyVersion) {
    if (proxyVersion < since) {
      throw new WaylandException(
          name + " requires version " + since + " but object is version " + proxyVersion);
    }
  }

  /**
   * Validates marshal arguments: arity, carrier type per {@link ArgType}, and nullability.
   * NEW_ID slots are skipped — callers never pass them.
   */
  public void checkArgs(Object... actual) {
    if (actual.length != marshalArgCount()) {
      throw new WaylandException(
          name + " expects " + marshalArgCount() + " arguments, got " + actual.length);
    }
    int i = 0;
    for (ArgDesc arg : args) {
      if (arg.type() == ArgType.NEW_ID) {
        continue;
      }
      Object value = actual[i++];
      if (value == null) {
        if (!arg.nullable()) {
          throw new WaylandException(name + ": argument '" + arg.name() + "' must not be null");
        }
        continue;
      }
      Class<?> carrier = switch (arg.type()) {
        case INT, UINT -> Integer.class;
        case FIXED -> Fixed.class;
        case STRING -> String.class;
        case OBJECT -> Proxy.class;
        case ARRAY -> WlArray.class;
        case FD -> Fd.class;
        case NEW_ID -> throw new AssertionError();
      };
      if (!carrier.isInstance(value)) {
        throw new WaylandException(name + ": argument '" + arg.name() + "' expects "
            + carrier.getSimpleName() + ", got " + value.getClass().getSimpleName());
      }
    }
  }
}
