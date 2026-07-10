package io.github.nhwalker.wayland.core;

import io.github.nhwalker.unixsocket.Fd;
import io.github.nhwalker.unixsocket.ReceiveResult;
import io.github.nhwalker.unixsocket.UnixSocketChannel;
import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The wire engine: encodes requests into a send buffer, parses received bytes into events, and
 * tracks object ids — all driven purely by {@link InterfaceDesc} tables (no protocol names).
 *
 * <p>Wire format: each message is a 32-bit object id, then a word holding
 * {@code size << 16 | opcode} (size includes the 8-byte header), then the arguments as 32-bit
 * words in the platform's byte order (Wayland is same-machine IPC; this implementation targets
 * little-endian Linux). Strings carry {@code length-including-NUL, bytes, pad-to-4}; arrays
 * {@code length, bytes, pad-to-4}; fds travel out of band as {@code SCM_RIGHTS} ancillary data
 * and are consumed in arrival order by fd-typed arguments.
 *
 * <p>Object lifecycle: client ids are allocated from 2 upward (the display is 1) and reused
 * only after the server confirms deletion via {@code delete_id} → {@link #retireId}. A proxy
 * destroyed client-side stays in the object map as a "zombie" until then, so events already in
 * flight for it can still be decoded (and their fds consumed) before being dropped.
 */
final class WireConnection implements WaylandConnection {

  private static final int SERVER_ID_BASE = 0xFF00_0000;

  private final UnixSocketChannel channel;
  private final Map<Integer, WireProxy> objects = new HashMap<>();
  private final Deque<Integer> freeIds = new ArrayDeque<>();
  private int nextId = 2;
  private WireProxy display;
  private WaylandProtocolException fatal;
  private boolean open = true;

  private byte[] out = new byte[4096];
  private int outLength;
  private final List<Fd> outFds = new ArrayList<>();

  private byte[] in = new byte[4096];
  private int inStart;
  private int inEnd;
  private final Deque<Fd> inFds = new ArrayDeque<>();

  WireConnection(UnixSocketChannel channel) {
    this.channel = channel;
  }

  // ---- WaylandConnection ----

  @Override
  public Proxy display(InterfaceDesc displayInterface) {
    if (display == null) {
      display = new WireProxy(this, displayInterface, 1, 1);
      objects.put(1, display);
    } else if (!display.interfaceDesc().equals(displayInterface)) {
      throw new WaylandException("display is already bound to "
          + display.interfaceDesc().name());
    }
    return display;
  }

  @Override
  public void flush() throws IOException {
    checkUsable();
    if (outLength == 0) {
      return;
    }
    MemorySegment data = MemorySegment.ofArray(out).asSlice(0, outLength);
    channel.send(data, List.copyOf(outFds));
    outLength = 0;
    outFds.clear();
  }

  @Override
  public int dispatch() throws IOException {
    int delivered = dispatchPending();
    if (delivered > 0) {
      return delivered;
    }
    while (true) {
      readMore();
      delivered = dispatchPending();
      if (delivered > 0) {
        return delivered;
      }
    }
  }

  @Override
  public int dispatchPending() {
    checkUsable();
    int count = 0;
    while (true) {
      int available = inEnd - inStart;
      if (available < 8) {
        break;
      }
      int id = wordAt(inStart);
      int sizeAndOpcode = wordAt(inStart + 4);
      int size = sizeAndOpcode >>> 16;
      int opcode = sizeAndOpcode & 0xFFFF;
      if (size < 8 || (size & 3) != 0) {
        throw new WaylandException("malformed message: size " + size);
      }
      if (available < size) {
        break;
      }
      int bodyStart = inStart + 8;
      inStart += size;
      deliver(id, opcode, bodyStart, size - 8);
      count++;
    }
    if (inStart == inEnd) {
      inStart = 0;
      inEnd = 0;
    }
    return count;
  }

  @Override
  public void fatalError(Proxy object, int code, String message) {
    if (fatal == null) {
      fatal = new WaylandProtocolException(
          object == null ? 0 : object.id(),
          object == null ? "unknown" : object.interfaceDesc().name(),
          code, message);
    }
  }

  @Override
  public void retireId(int id) {
    WireProxy proxy = objects.remove(id);
    if (proxy != null) {
      proxy.markDestroyed();
    }
    if (id >= 2 && Integer.compareUnsigned(id, SERVER_ID_BASE) < 0) {
      freeIds.add(id);
    }
  }

  @Override
  public Optional<WaylandProtocolException> error() {
    return Optional.ofNullable(fatal);
  }

  @Override
  public boolean isOpen() {
    return open;
  }

  @Override
  public void close() {
    if (open) {
      open = false;
      try {
        channel.close();
      } catch (IOException ignored) {
        // closing is best-effort
      }
    }
  }

  // ---- engine internals (called by WireProxy) ----

  void checkUsable() {
    if (fatal != null) {
      throw fatal;
    }
    if (!open) {
      throw new WaylandException("connection is closed");
    }
  }

  WireProxy createChild(InterfaceDesc interfaceDesc, int version) {
    Integer free = freeIds.poll();
    int id = free != null ? free : nextId++;
    WireProxy child = new WireProxy(this, interfaceDesc, id, version);
    objects.put(id, child);
    return child;
  }

  /**
   * Encodes one request. {@code child} is non-null for constructors; a NEW_ID arg whose
   * descriptor has no interface ref is the dynamic form and is preceded on the wire by the
   * child's interface name and version.
   */
  void enqueue(WireProxy sender, int opcode, MessageDesc message, Object[] args,
      WireProxy child, int childVersion) {
    int headerStart = outLength;
    putWord(sender.id());
    putWord(0); // patched below once the size is known
    int argIndex = 0;
    for (ArgDesc arg : message.args()) {
      switch (arg.type()) {
        case INT, UINT -> putWord((Integer) args[argIndex++]);
        case FIXED -> putWord(((Fixed) args[argIndex++]).raw());
        case STRING -> putString((String) args[argIndex++]);
        case ARRAY -> putArray((WlArray) args[argIndex++]);
        case OBJECT -> {
          Proxy object = (Proxy) args[argIndex++];
          putWord(object == null ? 0 : object.id());
        }
        case NEW_ID -> {
          if (arg.interfaceRef() == null) {
            putString(child.interfaceDesc().name());
            putWord(childVersion);
          }
          putWord(child.id());
        }
        case FD -> outFds.add((Fd) args[argIndex++]);
      }
    }
    int size = outLength - headerStart;
    setWord(headerStart + 4, (size << 16) | opcode);
  }

  // ---- receive path ----

  private void readMore() throws IOException {
    checkUsable();
    if (inEnd == in.length) {
      if (inStart > 0) {
        System.arraycopy(in, inStart, in, 0, inEnd - inStart);
        inEnd -= inStart;
        inStart = 0;
      } else {
        in = java.util.Arrays.copyOf(in, in.length * 2);
      }
    }
    MemorySegment dst = MemorySegment.ofArray(in).asSlice(inEnd, in.length - inEnd);
    ReceiveResult result = channel.receive(dst);
    if (result.endOfStream()) {
      throw new IOException("connection closed by compositor");
    }
    inEnd += result.bytesReceived();
    inFds.addAll(result.fds());
  }

  private void deliver(int id, int opcode, int offset, int length) {
    WireProxy target = objects.get(id);
    if (target == null) {
      throw new WaylandException("event for unknown object id " + id);
    }
    MessageDesc event = target.interfaceDesc().event(opcode);
    Object[] args = decodeArgs(target, event, offset, length);
    if (target.isDestroyed()) {
      WireProxy.closeFdArgs(args); // zombie: signature known, event dropped
      return;
    }
    target.deliver(opcode, args);
  }

  private Object[] decodeArgs(WireProxy target, MessageDesc event, int offset, int length) {
    int end = offset + length;
    Object[] args = new Object[event.args().size()];
    int cursor = offset;
    int index = 0;
    for (ArgDesc arg : event.args()) {
      switch (arg.type()) {
        case INT, UINT -> {
          args[index] = wordAt(cursor);
          cursor += 4;
        }
        case FIXED -> {
          args[index] = new Fixed(wordAt(cursor));
          cursor += 4;
        }
        case STRING -> {
          int stringLength = wordAt(cursor);
          cursor += 4;
          if (stringLength == 0) {
            args[index] = null;
          } else {
            args[index] = new String(in, cursor, stringLength - 1, StandardCharsets.UTF_8);
            cursor += pad4(stringLength);
          }
        }
        case ARRAY -> {
          int arrayLength = wordAt(cursor);
          cursor += 4;
          byte[] bytes = new byte[arrayLength];
          System.arraycopy(in, cursor, bytes, 0, arrayLength);
          args[index] = WlArray.of(bytes);
          cursor += pad4(arrayLength);
        }
        case OBJECT -> {
          int objectId = wordAt(cursor);
          cursor += 4;
          args[index] = objectId == 0 ? null : objects.get(objectId);
        }
        case NEW_ID -> {
          int newId = wordAt(cursor);
          cursor += 4;
          if (arg.interfaceRef() == null) {
            throw new WaylandException(event.name() + ": dynamic new_id in an event");
          }
          WireProxy created =
              new WireProxy(this, arg.interfaceRef().get(), newId, target.version());
          objects.put(newId, created);
          args[index] = created;
        }
        case FD -> {
          Fd fd = inFds.poll();
          if (fd == null) {
            throw new WaylandException(event.name() + ": fd argument but none received");
          }
          args[index] = fd;
        }
      }
      index++;
    }
    if (cursor != end) {
      throw new WaylandException(event.name() + ": message body size mismatch");
    }
    return args;
  }

  // ---- little-endian word access over the buffers ----

  private void putWord(int value) {
    ensureOut(4);
    out[outLength] = (byte) value;
    out[outLength + 1] = (byte) (value >>> 8);
    out[outLength + 2] = (byte) (value >>> 16);
    out[outLength + 3] = (byte) (value >>> 24);
    outLength += 4;
  }

  private void setWord(int at, int value) {
    out[at] = (byte) value;
    out[at + 1] = (byte) (value >>> 8);
    out[at + 2] = (byte) (value >>> 16);
    out[at + 3] = (byte) (value >>> 24);
  }

  private void putString(String value) {
    if (value == null) {
      putWord(0);
      return;
    }
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    int lengthWithNul = bytes.length + 1;
    putWord(lengthWithNul);
    int padded = pad4(lengthWithNul);
    ensureOut(padded);
    System.arraycopy(bytes, 0, out, outLength, bytes.length);
    for (int i = bytes.length; i < padded; i++) {
      out[outLength + i] = 0; // NUL terminator + padding
    }
    outLength += padded;
  }

  private void putArray(WlArray value) {
    byte[] bytes = value.bytes();
    putWord(bytes.length);
    int padded = pad4(bytes.length);
    ensureOut(padded);
    System.arraycopy(bytes, 0, out, outLength, bytes.length);
    for (int i = bytes.length; i < padded; i++) {
      out[outLength + i] = 0;
    }
    outLength += padded;
  }

  private void ensureOut(int extra) {
    if (outLength + extra > out.length) {
      int size = out.length;
      while (outLength + extra > size) {
        size *= 2;
      }
      out = java.util.Arrays.copyOf(out, size);
    }
  }

  private int wordAt(int at) {
    return (in[at] & 0xFF)
        | (in[at + 1] & 0xFF) << 8
        | (in[at + 2] & 0xFF) << 16
        | (in[at + 3] & 0xFF) << 24;
  }

  private static int pad4(int length) {
    return (length + 3) & ~3;
  }
}
