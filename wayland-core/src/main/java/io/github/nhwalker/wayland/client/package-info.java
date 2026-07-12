/**
 * The typed client surface for the core {@code wayland.xml} protocol. The classes in this
 * package are <em>generated at build time</em> by {@code wayland-scanner} from its bundled
 * protocol definition; only this file is hand-written. The emitted form is pinned by the
 * scanner's golden-file conformance tests.
 *
 * <p>Generator conventions:
 *
 * <ul>
 *   <li>{@code wl_foo_bar} → {@code WlFooBar}; requests → camelCase methods; Java-keyword arg
 *       names are renamed ({@code interface} → {@code interfaceName}); enum entries starting
 *       with a digit are prefixed with {@code _} ({@code 90} → {@code _90}).
 *   <li>Every class is final, wraps a {@link io.github.nhwalker.wayland.core.Proxy}
 *       (composition), and exposes {@code INTERFACE}, {@code TYPE} (except {@code wl_display}),
 *       a package-private {@code Proxy} constructor, and
 *       {@code proxy()/id()/version()/isDestroyed()}.
 *   <li>Events form a {@code sealed interface Event} of records, delivered through
 *       {@code onEvent(Consumer)}; enum-typed event fields carry the raw {@code int} for
 *       forward compatibility (decode via the enum's {@code lookup}), while request parameters
 *       take the typed enum.
 *   <li>An interface whose destructor request is named exactly {@code destroy} also implements
 *       {@link AutoCloseable}; {@code close()} delegates and is a no-op once destroyed.
 *   <li>Messages with {@code since > 1} are documented and runtime-enforced via
 *       {@link io.github.nhwalker.wayland.core.MessageDesc#checkSince}.
 * </ul>
 */
package io.github.nhwalker.wayland.client;
