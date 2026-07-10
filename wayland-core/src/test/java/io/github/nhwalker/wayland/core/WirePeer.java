package io.github.nhwalker.wayland.core;

import io.github.nhwalker.unixsocket.Fd;
import io.github.nhwalker.unixsocket.ReceiveResult;
import io.github.nhwalker.unixsocket.UnixSocketChannel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Test double for the compositor side of a wire connection: reads and writes raw wire-format
 * messages over the peer {@link UnixSocketChannel}, independent of the engine under test.
 */
public final class WirePeer implements AutoCloseable {

  private final UnixSocketChannel channel;
  private byte[] buffer = new byte[8192];
  private int start;
  private int end;
  private final Deque<Fd> receivedFds = new ArrayDeque<>();

  public WirePeer(UnixSocketChannel channel) {
    this.channel = channel;
  }

  public record Message(int objectId, int opcode, byte[] body) {

    public BodyReader reader() {
      return new BodyReader(body);
    }
  }

  /** Cursor over a message body, mirroring the wire argument encodings. */
  public static final class BodyReader {
    private final byte[] body;
    private int at;

    BodyReader(byte[] body) {
      this.body = body;
    }

    public int readWord() {
      int value = leWord(body, at);
      at += 4;
      return value;
    }

    public String readString() {
      int length = readWord();
      if (length == 0) {
        return null;
      }
      String value = new String(body, at, length - 1, StandardCharsets.UTF_8);
      at += (length + 3) & ~3;
      return value;
    }

    public int remaining() {
      return body.length - at;
    }
  }

  /** Blocking read of the next complete message. */
  public Message read() throws IOException {
    while (true) {
      int available = end - start;
      if (available >= 8) {
        int size = leWord(buffer, start + 4) >>> 16;
        if (available >= size) {
          int objectId = leWord(buffer, start);
          int opcode = leWord(buffer, start + 4) & 0xFFFF;
          byte[] body = new byte[size - 8];
          System.arraycopy(buffer, start + 8, body, 0, body.length);
          start += size;
          if (start == end) {
            start = 0;
            end = 0;
          }
          return new Message(objectId, opcode, body);
        }
      }
      if (end == buffer.length) {
        System.arraycopy(buffer, start, buffer, 0, end - start);
        end -= start;
        start = 0;
      }
      ReceiveResult result = channel.receive(
          MemorySegment.ofArray(buffer).asSlice(end, buffer.length - end));
      if (result.endOfStream()) {
        throw new IOException("client closed the connection");
      }
      end += result.bytesReceived();
      receivedFds.addAll(result.fds());
    }
  }

  /** The next fd received as ancillary data (fds are consumed in arrival order). */
  public Fd takeFd() {
    Fd fd = receivedFds.poll();
    if (fd == null) {
      throw new IllegalStateException("no fd received");
    }
    return fd;
  }

  public void write(byte[] bytes) throws IOException {
    channel.send(MemorySegment.ofArray(bytes));
  }

  public void write(byte[] bytes, Fd fd) throws IOException {
    channel.send(MemorySegment.ofArray(bytes), List.of(fd));
  }

  @Override
  public void close() throws IOException {
    channel.close();
  }

  // ---- message builders (independent re-implementation of the wire encodings) ----

  public static byte[] msg(int objectId, int opcode, byte[]... bodyParts) {
    byte[] body = cat(bodyParts);
    int size = 8 + body.length;
    ByteArrayOutputStream out = new ByteArrayOutputStream(size);
    putLeWord(out, objectId);
    putLeWord(out, (size << 16) | opcode);
    out.writeBytes(body);
    return out.toByteArray();
  }

  public static byte[] words(int... values) {
    ByteArrayOutputStream out = new ByteArrayOutputStream(values.length * 4);
    for (int value : values) {
      putLeWord(out, value);
    }
    return out.toByteArray();
  }

  public static byte[] str(String value) {
    if (value == null) {
      return words(0);
    }
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    int lengthWithNul = bytes.length + 1;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    putLeWord(out, lengthWithNul);
    out.writeBytes(bytes);
    for (int i = bytes.length; i < ((lengthWithNul + 3) & ~3); i++) {
      out.write(0);
    }
    return out.toByteArray();
  }

  public static byte[] array(byte[] content) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    putLeWord(out, content.length);
    out.writeBytes(content);
    for (int i = content.length; i < ((content.length + 3) & ~3); i++) {
      out.write(0);
    }
    return out.toByteArray();
  }

  public static byte[] cat(byte[]... parts) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (byte[] part : parts) {
      out.writeBytes(part);
    }
    return out.toByteArray();
  }

  private static void putLeWord(ByteArrayOutputStream out, int value) {
    out.write(value);
    out.write(value >>> 8);
    out.write(value >>> 16);
    out.write(value >>> 24);
  }

  private static int leWord(byte[] bytes, int at) {
    return (bytes[at] & 0xFF)
        | (bytes[at + 1] & 0xFF) << 8
        | (bytes[at + 2] & 0xFF) << 16
        | (bytes[at + 3] & 0xFF) << 24;
  }
}
