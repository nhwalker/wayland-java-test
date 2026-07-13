package io.github.nhwalker.wayland.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.nhwalker.wayland.core.WaylandConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.IsRunningStartupCheckStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;

/**
 * The real-compositor integration test: starts headless Weston in a container (Testcontainers)
 * and runs the shm-window demo against it. The Wayland unix socket crosses the container
 * boundary through a bind-mounted runtime directory; Weston runs as the host user so the
 * socket is connectable from the test JVM. Skips when Docker is unavailable, so the build
 * stays runnable everywhere.
 */
@Timeout(300)
class WestonDemoTest {

  private static final String SOCKET_NAME = "wayland-demo";

  @Test
  void rendersFramesOnWestonInAContainer() throws Exception {
    assumeTrue(isDockerAvailable(), "Docker not available; skipping containerized Weston test");

    Path runtimeDir = Files.createTempDirectory("wayland-demo");
    Files.setPosixFilePermissions(runtimeDir, PosixFilePermissions.fromString("rwx------"));
    int uid = (Integer) Files.getAttribute(runtimeDir, "unix:uid");
    int gid = (Integer) Files.getAttribute(runtimeDir, "unix:gid");

    ImageFromDockerfile image =
        new ImageFromDockerfile("wayland-java/weston-headless:ubuntu-24.04", false)
            .withDockerfileFromBuilder(builder -> builder
                .from("ubuntu:24.04")
                .run("apt-get update && apt-get install -y --no-install-recommends weston"
                    + " && rm -rf /var/lib/apt/lists/*")
                .build());

    try (GenericContainer<?> weston = new GenericContainer<>(image)) {
      weston
          .withCreateContainerCmdModifier(cmd -> cmd.withUser(uid + ":" + gid))
          .withEnv("XDG_RUNTIME_DIR", "/run/wayland")
          .withEnv("HOME", "/tmp")
          .withFileSystemBind(runtimeDir.toString(), "/run/wayland", BindMode.READ_WRITE)
          .withCommand("weston", "--backend=headless", "--socket=" + SOCKET_NAME,
              "--idle-time=0", "--no-config")
          .withStartupCheckStrategy(new IsRunningStartupCheckStrategy());
      weston.start();

      Path socket = awaitSocket(weston, runtimeDir.resolve(SOCKET_NAME));
      try (WaylandConnection connection = WaylandConnection.open(socket)) {
        ShmWindow.Result result = ShmWindow.run(connection, 10);
        assertEquals(10, result.framesRendered());
        assertTrue(result.width() > 0 && result.height() > 0);
      }
    }
  }

  private static Path awaitSocket(GenericContainer<?> weston, Path socket)
      throws InterruptedException {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
    while (!Files.exists(socket)) {
      if (!weston.isRunning() || Instant.now().isAfter(deadline)) {
        fail("weston did not create " + socket + "; container logs:\n" + weston.getLogs());
      }
      Thread.sleep(200);
    }
    return socket;
  }

  private static boolean isDockerAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (Throwable t) {
      return false;
    }
  }
}
