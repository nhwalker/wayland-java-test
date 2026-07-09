# wayland-java-test

A pure-Java implementation of the [Wayland](https://wayland.freedesktop.org/) protocol,
built on the Java Foreign Function & Memory (FFM) API — no bindings to `libwayland`
required.

Instead of wrapping the C library, this project speaks the Wayland wire protocol
directly over a Unix domain socket, using FFM only where the JDK lacks the necessary
system-level primitives (most notably file-descriptor passing via ancillary data).
Protocol bindings are generated from the same XML protocol definitions used by the
reference `wayland-scanner`.

## Goals

- **Pure Java.** Implement the Wayland wire protocol in Java rather than binding to
  `libwayland`, so there is no native glue code to build or ship.
- **Modern platform.** Target Java 25 and lean on FFM (`java.lang.foreign`) for the
  small set of syscalls the JDK does not expose.
- **Generated protocol stubs.** Generate type-safe Java interfaces from Wayland
  protocol XML, keeping the core library protocol-agnostic and making it trivial to
  support protocol extensions.

## Modules

The build is organized as a multi-project Gradle build:

| Module | Description |
| --- | --- |
| `unix-socket` | Unix domain socket send/receive API with ancillary-data support |
| `wayland-scanner` | Code generator (annotation processor + CLI) for Wayland protocol XML |
| `wayland-core` | The core library: wire protocol, event loop, and runtime |

### `unix-socket`

An FFM-based Unix domain socket API supporting `sendmsg(2)`/`recvmsg(2)` with
ancillary data — in particular `SCM_RIGHTS`, which the Wayland protocol relies on to
pass file descriptors (shared-memory buffers, dmabufs, keymaps) between client and
compositor. The JDK's built-in `SocketChannel` support for Unix domain sockets does
not expose ancillary data, which is why this module exists.

The module is independent of Wayland and usable on its own wherever fd-passing over
Unix sockets is needed.

### `wayland-scanner`

The Java analog of the C `wayland-scanner`: it reads Wayland protocol definitions
(`wayland.xml`, `xdg-shell.xml`, third-party extensions, …) and generates Java stubs
for the interfaces, requests, events, and enums they declare. It is usable two ways:

- **Annotation processor** — declare the protocols a project uses and have stubs
  generated at compile time as part of the normal build.
- **CLI tool** — generate sources ahead of time, mirroring the workflow of the C
  scanner.

> **Naming note:** `wayland-scanner` is the working name, chosen for instant
> recognition by anyone familiar with the C tooling. Alternatives under
> consideration: `wayland-protogen`, `wlgen`, and `wayland-stubgen`.

### `wayland-core`

The core runtime that ties everything together: connection management over
`unix-socket`, wire-format marshalling/unmarshalling, object/proxy lifecycle, and event
dispatch for the stubs produced by `wayland-scanner`.

## Requirements

- **JDK 25** — the FFM API and current language features are used throughout.
- **Gradle 9.2.1** — provided via the Gradle wrapper; no local install needed.
- **Linux** — Wayland and Unix domain sockets are inherently platform-specific.

## Building

```sh
./gradlew build
```

## Status

Early development. The module layout and APIs described above are the design target
and are subject to change.

## License

This is free and unencumbered software released into the public domain. See
[LICENSE](LICENSE) for details.
