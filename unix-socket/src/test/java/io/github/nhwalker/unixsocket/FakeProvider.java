package io.github.nhwalker.unixsocket;

import java.net.UnixDomainSocketAddress;

/** Registered via {@code META-INF/services} so {@link UnixSocketProvider#provider()} finds it. */
public final class FakeProvider implements UnixSocketProvider {

  @Override
  public UnixSocketChannel connect(UnixDomainSocketAddress address) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Pair pair() {
    throw new UnsupportedOperationException();
  }

  @Override
  public UnixSocketChannel adopt(Fd socket) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Fd adoptFd(int rawFd) {
    throw new UnsupportedOperationException();
  }
}
