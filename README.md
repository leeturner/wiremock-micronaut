# wiremock-micronaut

WireMock for Micronaut 5 tests: start WireMock servers per test class, bind
their URLs into Micronaut configuration before your beans are created, and
inject them into your tests. Annotations and attributes mirror
[wiremock-spring-boot](https://github.com/wiremock/wiremock-spring-boot).

## Requirements

- Java 25
- Micronaut 5.x with `micronaut-test-junit5`

## Install

```kotlin
testImplementation("com.leeturner:wiremock-micronaut:0.1.0")
```

The library brings `wiremock-standalone` (a shaded jar), so WireMock's own
dependencies never clash with Micronaut's.

## Quick start (Java)

```java
@MicronautTest
@EnableWireMock(@ConfigureWireMock(name = "users", baseUrlProperties = "users.url"))
class UsersClientTest {
  @Inject UsersClient client;           // @Client("${users.url}")
  @InjectWireMock("users") WireMockServer users;

  @Test
  void fetchesAUser() {
    users.stubFor(get("/users/1").willReturn(ok("alice")));
    assertThat(client.user("1")).isEqualTo("alice");
  }
}
```

## Quick start (Kotlin)

```kotlin
@MicronautTest
@EnableWireMock(ConfigureWireMock(name = "users", baseUrlProperties = ["users.url"]))
class UsersClientTest {
    @Inject lateinit var client: UsersClient
    @InjectWireMock("users") lateinit var users: WireMockServer
}
```

## Mocking Micronaut HTTP clients

Both ways of declaring a client work.

**URL placeholder.** Bind the property the placeholder uses:

```java
@Client("${users.url}")                     // or "${micronaut.http.services.users.url}"
public interface UsersClient { /* ... */ }

@EnableWireMock(@ConfigureWireMock(name = "users", baseUrlProperties = "users.url"))
```

**Service id.** Bind the service's URL property. WireMock's value overrides
the real URL in your `application.yml`/`application.properties` for the
test:

```java
@Client("setlist-fm")
public interface SetlistFmClient { /* ... */ }

@EnableWireMock(
    @ConfigureWireMock(
        name = "setlist-fm",
        baseUrlProperties = "micronaut.http.services.setlist-fm.url"))
```

If your configuration uses `micronaut.http.services.<id>.urls` (a list),
bind that key instead.

## How it works

- A bare `@EnableWireMock` starts one server named `wiremock`.
- Its properties (`wiremock.server.port`, `wiremock.server.baseUrl`, and
  the HTTPS equivalents when enabled) are bound into the Micronaut context
  before any bean is created, so `@Client("${...}")`, `@Value` and eager
  beans all see them.
- Servers are reset before each test (`resetWireMockServer`). When exactly
  one server is configured, the static `WireMock.stubFor(...)` DSL points
  at it.
- `@Nested` classes share their enclosing class's servers.
  `@MicronautTest(rebuildContext = true)` keeps the same servers and ports.
- Servers stop after the test class, even if the context fails to start.

## `@ConfigureWireMock` attributes

| Attribute | Default | Meaning |
|---|---|---|
| `name` | `"wiremock"` | Server name for `@InjectWireMock`. |
| `port` | `0` | HTTP port: `0` dynamic, `-1` disabled. |
| `httpsPort` | `-1` | HTTPS port: `-1` disabled, `0` dynamic. |
| `portProperties` | `wiremock.server.port` | Properties set to the HTTP port. |
| `httpsPortProperties` | `wiremock.server.httpsPort` | Properties set to the HTTPS port. |
| `baseUrlProperties` | `wiremock.server.baseUrl` | Properties set to `http://localhost:<port>`. |
| `httpsBaseUrlProperties` | `wiremock.server.httpsBaseUrl` | Properties set to `https://localhost:<httpsPort>`. |
| `filesUnderClasspath` | `""` | Classpath root with `mappings`/`__files`. |
| `filesUnderDirectory` | `{}` | Directories with `mappings`/`__files` (first existing wins; takes precedence over classpath). |
| `extensions` / `extensionFactories` | `{}` | WireMock extensions (public no-arg constructor). |
| `configurationCustomizers` | `{}` | `WireMockConfigurationCustomizer`s, applied last. |
| `resetWireMockServer` | `true` | Reset before each test. |
| `registerBean` | `false` | Also expose the server as `@Named("<name>") WireMockServer`. |
| `globalTemplating` | `false` | Response templating for every stub. |
| `keystore*`, `trustStore*`, `needClientAuth` | — | HTTPS/TLS settings. |

With no files location configured, stubs load from the first existing
directory in `wiremock`, `stubs`, `mappings`, `src/test/resources/wiremock`,
`src/test/resources/stubs`, `src/test/resources/mappings` (and the
`src/integtest/resources/...` equivalents).

## Several servers

```java
@MicronautTest
@EnableWireMock({
  @ConfigureWireMock(name = "users", baseUrlProperties = "users.url"),
  @ConfigureWireMock(name = "orders", baseUrlProperties = "orders.url")
})
class CheckoutTest { /* ... */ }
```

Default property names shared by several servers are not bound, because
they would be ambiguous. Give each server its own property names. Explicit
duplicates fail fast.

## Gotchas

- `@EnableWireMock`/`@ConfigureWireMock` belong on the top-level test
  class, not on `@Nested` classes.
- Without `@MicronautTest`, servers still run and inject, but nothing is
  bound into Micronaut configuration.
- `@InjectWireMock` is not a jakarta `@Qualifier`. For `registerBean`
  servers, inject method parameters with `@Named("<name>")`;
  `@InjectWireMock` fields still work.
- Apps using Micronaut declarative HTTP clients need a JSON module on the
  runtime classpath, for example `io.micronaut.serde:micronaut-serde-jackson`.
  The examples add it.

## Migrating from `io.github.nahuel92:wiremock-micronaut`

| Before | After |
|---|---|
| `@MicronautWireMockTest(...)` | `@MicronautTest` + `@EnableWireMock(...)` |
| `@ConfigureWireMock(properties = "x")` | `@ConfigureWireMock(baseUrlProperties = "x")` |
| `portProperty = "x"` | `portProperties = "x"` |
| package `io.github.nahuel92.wiremock.micronaut` | `com.leeturner.wiremock.micronaut` |
| gRPC (`WireMockGrpcService`) | Not supported |

## Versioning

`0.x` while WireMock 4 is in beta. `1.0.0` will follow WireMock 4 GA.
