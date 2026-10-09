# wiremock-micronaut

[![Maven Central](https://img.shields.io/maven-central/v/io.github.leeturner/wiremock-micronaut)](https://central.sonatype.com/artifact/io.github.leeturner/wiremock-micronaut)

WireMock for Micronaut 5 tests: start WireMock servers per test class, bind
their URLs into Micronaut configuration before your beans are created, and
inject them into your tests. Annotations and attributes mirror
[wiremock-spring-boot](https://github.com/wiremock/wiremock-spring-boot).

## Requirements

- Java 25
- Micronaut 5.2.1 or later (5.x) with `micronaut-test-junit5`
- Building this repo needs Gradle running on JDK 25 (see `.sdkmanrc`)

## Install

```kotlin
testImplementation("io.github.leeturner:wiremock-micronaut:<version>")
```

The latest `<version>` is on the Maven Central badge above.

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
| `filesUnderClasspath` | `""` | Classpath root with `mappings`/`__files`/`message-mappings`. |
| `filesUnderDirectory` | `{}` | Directories with `mappings`/`__files`/`message-mappings` (first existing wins; takes precedence over classpath). |
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

## Message stubs and server-sent events

WireMock's message stubs and SSE need no extra configuration. Put message
stubs in `message-mappings`, next to `mappings`:

```
stubs-by-server/live-setlist/
  mappings/live.json                     # opens the SSE stream, plus a trigger stub
  message-mappings/live-setlist.json     # what to send when the trigger is hit
```

An HTTP stub opens the stream:

```json
{ "request": { "method": "GET", "urlPath": "/gigs/radiohead-2026/live" },
  "response": { "status": 200, "openSseChannel": true } }
```

A message stub sends events to it when another stub (by `id`) is hit:

```json
{ "trigger": { "type": "http-stub", "stubId": "cccccccc-0000-4000-8000-000000000001" },
  "actions": [ { "type": "send",
    "message": { "body": { "data": "Airbag" }, "headers": { "event": "song" } },
    "channelTarget": { "type": "request-initiated", "channelType": "sse",
                       "requestPattern": { "urlPath": "/gigs/radiohead-2026/live" } } } ] }
```

The Java DSL works on an injected server or, with one server, the static
`WireMock` methods:

```java
liveSetlist.messageStubFor(
    message()
        .triggeredByHttpRequest(newRequestPattern().withUrl(urlPathEqualTo("/start")))
        .willTriggerActions(
            sendSse("Airbag").withEventName("song")
                .onChannelsMatching(newRequestPattern().withUrl(urlPathEqualTo("/live")).build())));

liveSetlist.waitForMessageEvent(
    messagePattern().withBody(equalTo("Airbag")).build(), Duration.ofSeconds(5));
```

- Message stubs and the message journal are reset before each test, like
  HTTP stubs. Stubs from `message-mappings` are reloaded.
- Trigger events only after your client has connected. Events sent before
  then have no channel and are dropped. Note the size of
  `server.listAllMessageChannels().getChannels()` before connecting and
  wait for it to grow. Don't just wait for it to be non-empty: a
  disconnected SSE channel stays listed until a send to it fails.
- To consume SSE with a Micronaut declarative client, use the Netty
  `io.micronaut:micronaut-http-client`. The JDK client does not support
  SSE.
- WebSockets work the same way, with `openWebsocketChannel` and
  `"channelType": "websocket"`.

## Gotchas

- `@EnableWireMock`/`@ConfigureWireMock` belong on the top-level test
  class, not on `@Nested` classes.
- Without `@MicronautTest`, servers still run and inject, but nothing is
  bound into Micronaut configuration.
- `@InjectWireMock` is not a jakarta `@Qualifier`. In a class with any
  `registerBean` server, test-method parameters must use `@Named("<name>")`
  (`@InjectWireMock` fields still work); `@InjectWireMock` parameters fail
  fast there.
- Apps using Micronaut declarative HTTP clients need a JSON module on the
  runtime classpath, for example `io.micronaut.serde:micronaut-serde-jackson`.
  The examples add it.

## Pyronaut

[Pyronaut](https://pyronaut.io) apps are Micronaut apps written in Python.
The extension works in their JUnit 5 tests, whether written in Java
(`test-java/`) or as Python test modules. pytest tests don't run through
JUnit, so the extension does nothing there.

```toml
[tool.pyronaut.dependencies]
test = [
    "io.micronaut.test:micronaut-test-junit5",
    "io.github.leeturner:wiremock-micronaut:<version>",
]

[tool.pyronaut.test]
engine = "junit"  # or "both" to run pytest tests too
```

A Python test module calls the annotations at module level:

```python
MicronautTest()
EnableWireMock(
    ConfigureWireMock(name="users", baseUrlProperties=["users.url"], registerBean=True))

client: Annotated[UsersClient, Inject]
users: Annotated[WireMockServer, Inject, Named("users")]

@Test
def test_fetches_a_user():
    users.stubFor(WireMock.get("/users/1").willReturn(WireMock.ok("alice")))
    assert str(client.user("1")) == "alice"
```

- `@InjectWireMock` does nothing in a Python module, because Pyronaut only
  injects module attributes marked `Inject`. Set `registerBean=True` and
  inject the server with `Named("<name>")`, as above. With a single server,
  the static `WireMock.stubFor(...)` also works.
- Python can't import Java static methods: write `WireMock.get(...)`, not
  `get(...)`.
- Annotation attributes keep their Java names (`baseUrlProperties`,
  `startApplication`). Array attributes take lists.
- `filesUnderClasspath` looks in Pyronaut's test resources (`tests-config/`).
  This needs a version later than 0.1.0. Default stub directories are
  relative to the project, for example `wiremock/`.
- In pytest, start a `WireMockServer` in a fixture and pass its `baseUrl()`
  to `MicronautTest(properties=...)`.

## Examples

[`examples/java`](examples/java), [`examples/kotlin`](examples/kotlin) and
[`examples/pyronaut`](examples/pyronaut) are the same small Micronaut
service: `GET /artists/{mbid}` combines an artist
from MusicBrainz (a `${musicbrainz.url}` client) with recent setlists from
setlist.fm (a `setlist-fm` service id client), and `LiveSetlistService`
follows a gig's setlist from an SSE feed (a `live-setlist` service id
client). Each test class shows one way to stub them:

| Test | Technique |
|---|---|
| `ProgrammaticStubsTest` | Stubs in the test with `stubFor`, `verify` of request headers, MusicBrainz 404, setlist.fm 404 (no setlists), 500 and connection faults. |
| `ClasspathStubsTest` | One classpath folder per server (`filesUnderClasspath`), bodies from `__files` via `bodyFileName`, and a test stub overriding a file stub. |
| `DefaultDirectoryTest` | No files configuration: stubs load from `src/test/resources/wiremock` (`wiremock/` in the Pyronaut example). That directory is shared by every such server. |
| `LiveSetlistTest` | SSE: `mappings` opens the stream, `message-mappings` sends the songs when a trigger stub is hit, and `waitForMessageEvent` verifies what was sent. |

The Pyronaut example uses this repo's code, not a release. Run it with
[GraalPy](https://github.com/oracle/graalpython) on your `PATH`:

```shell
./gradlew publishToMavenLocal
cd examples/pyronaut
pip install pyronaut && pyronaut setup
graalpy -m venv .venv && pyronaut install && pyronaut test
```

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
