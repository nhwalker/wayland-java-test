package io.github.nhwalker.unixsocket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class UnixSocketProviderTest {

  @Test
  void providerDiscoversServiceLoaderRegistration() {
    var types = ServiceLoader.load(UnixSocketProvider.class).stream()
        .map(ServiceLoader.Provider::type)
        .toList();
    assertTrue(types.contains(FakeProvider.class));
  }

  @Test
  void providerThrowsWhenNoImplementationIsInstalled() {
    Thread thread = Thread.currentThread();
    ClassLoader original = thread.getContextClassLoader();
    try (var empty = new URLClassLoader(new URL[0], ClassLoader.getPlatformClassLoader())) {
      thread.setContextClassLoader(empty);
      assertThrows(IllegalStateException.class, UnixSocketProvider::provider);
    } catch (IOException e) {
      throw new AssertionError(e);
    } finally {
      thread.setContextClassLoader(original);
    }
  }

  @Test
  void pairCloseClosesBothEndsEvenIfFirstFails() {
    var first = new FakeUnixSocketChannel() {
      @Override
      public void close() {
        super.close();
        throw new RuntimeException("first close failed");
      }
    };
    var second = new FakeUnixSocketChannel();
    var pair = new UnixSocketProvider.Pair() {
      @Override
      public UnixSocketChannel first() {
        return first;
      }

      @Override
      public UnixSocketChannel second() {
        return second;
      }
    };

    assertThrows(RuntimeException.class, pair::close);

    assertFalse(first.isOpen());
    assertFalse(second.isOpen());
  }

  @Test
  void pairCloseClosesBothEnds() throws Exception {
    var first = new FakeUnixSocketChannel();
    var second = new FakeUnixSocketChannel();
    var pair = new UnixSocketProvider.Pair() {
      @Override
      public UnixSocketChannel first() {
        return first;
      }

      @Override
      public UnixSocketChannel second() {
        return second;
      }
    };
    assertTrue(first.isOpen());
    assertTrue(second.isOpen());

    pair.close();

    assertFalse(first.isOpen());
    assertFalse(second.isOpen());
  }
}
