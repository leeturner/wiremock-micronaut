# Message stubs and SSE support

## Goal

Message stubs and server-sent events (SSE), added in WireMock `4.0.0-beta.39`,
work with this extension the same way HTTP stubs do: loaded from files,
isolated per test, documented, and shown in both examples.

## What already works (no change needed)

- WireMock loads `<root>/message-mappings/*.json` automatically whenever a
  files root is set (`usingFilesUnderDirectory` or `usingFilesUnderClasspath`).
- SSE needs no configuration: an HTTP stub with `"openSseChannel": true`
  (`aResponse().openSseChannel()`) opens the stream, served by the Jetty in
  `wiremock-standalone`.
- The message DSL (`server.messageStubFor`, `verifyMessageEvent`,
  `waitForMessageEvent`, `listAllMessageChannels`, and the static
  `WireMock.messageStubFor`) works through `@InjectWireMock`. The extension's
  existing `WireMock.configureFor` covers the static form.

No new annotation attributes.

## Library changes

### 1. Stub directory detection

`WireMockServerCreator.firstExistingStubDirectory` accepts a directory that
contains `mappings`, `__files` **or** `message-mappings`. This applies to both
`filesUnderDirectory` and the default-directory search. The
`filesUnderDirectory` error message lists all three names.

### 2. Per-test reset

In WireMock beta.39, `resetAll()` only resets HTTP stubs and the request
journal, then reloads the file mappings. Programmatic message stubs and the
message journal leak between tests. When `resetWireMockServer` is true,
`WireMockMicronautExtension.beforeEach` now runs:

```java
server.resetMessageStubs(); // clear message stubs, including programmatic ones
server.resetAll();          // reset HTTP stubs and requests; reload file stubs (HTTP + message)
server.resetMessageJournal();
```

The order matters: clearing first and then reloading keeps the file-loaded
message stubs.

Open SSE channels are not closed between tests. The client owns those
connections.

### 3. Library tests

The test fixtures live under `src/test/resources` or `src/test/` next to the
existing ones.

- **Message-mappings-only directory.** A `filesUnderDirectory` root that
  contains only `message-mappings` is accepted, and its message stub is loaded
  (`getMessageStubMappingsList()` contains it).
- **Reset isolation**, ordered like `FileStubsSurviveResetTest`. Test 1 adds a
  programmatic message stub. Test 2 asserts that only the file-loaded message
  stub remains and that the message journal is empty.
- **SSE round trip.** File-loaded `mappings` open the SSE channel at
  `/events` and declare a trigger stub with a fixed id. A file-loaded
  `message-mappings` stub with an `http-stub` trigger sends an event to that
  channel. The test:
  1. opens the stream with `java.net.http` (`BodyHandlers.ofLines`);
  2. waits until `listAllMessageChannels()` shows the channel;
  3. calls the trigger;
  4. asserts that the `data:` line arrives and that `waitForMessageEvent`
     finds it.

## Example (Java and Kotlin)

This keeps the artist theme: the app follows a gig's live setlist from an
upstream SSE feed.

### Build

In both example builds:

- Replace `micronaut-http-client-jdk` with `io.micronaut:micronaut-http-client`
  (Netty). Only the Netty client ships `SseClientFactory`. The existing
  examples keep working with it.
- Add `io.micronaut.reactor:micronaut-reactor`.

### App code

- **`LiveSetlistClient`** is a `@Client("live-setlist")` with
  `@Get(value = "/gigs/{gigId}/live", processes = TEXT_EVENT_STREAM)`. It
  returns `Publisher<Event<String>>`.
- **`LiveSetlistService`** has `Flux<String> songs(String gigId)`. It takes
  events until one named `end`, keeps those named `song`, and maps each to its
  data.
- **`application.properties`** gets
  `micronaut.http.services.live-setlist.url=https://live.example.com`.
- No controller is added. The service is the unit the test exercises.

### Test stubs

The stubs sit under
`src/test/resources/stubs-by-server/live-setlist/`, which shows
`message-mappings` loading from the classpath.

- **`mappings/live.json`** holds two stubs:
  - `GET /gigs/radiohead-2026/live` with `openSseChannel: true`;
  - `POST /gigs/radiohead-2026/start` returning 204, with a fixed `id`.
- **`message-mappings/live-setlist.json`** has a single message mapping with
  an `http-stub` trigger on the start stub's id. Its three `send` actions use
  `channelType: "sse"` with a `requestPattern` on the live URL:
  1. `song` "Airbag";
  2. `song` "Paranoid Android";
  3. `end`.

### Test

The test is `LiveSetlistTest` in each example, with
`@EnableWireMock(@ConfigureWireMock(name = "live-setlist", filesUnderClasspath = "stubs-by-server/live-setlist", baseUrlProperties = "micronaut.http.services.live-setlist.url"))`.

1. Start `service.songs("radiohead-2026").collectList().toFuture()`.
2. Wait until `liveSetlist.listAllMessageChannels()` shows one channel. Use a
   short poll loop with a timeout: the trigger must fire after the client
   connects.
3. POST the start trigger.
4. Assert that the future yields `["Airbag", "Paranoid Android"]` within 5
   seconds.
5. Verify with `waitForMessageEvent` that the `end` event was sent.

The Kotlin test mirrors the Java one.

## README

A new "Message stubs and SSE" section covers:

- the `mappings` + `message-mappings` layout, with a trimmed JSON pair;
- `openSseChannel`;
- the DSL through `@InjectWireMock` or the static `WireMock`;
- `waitForMessageEvent`;
- the fact that message stubs and the message journal are reset per test;
- the requirement for the Netty `micronaut-http-client` when consuming SSE
  from Micronaut.

The `filesUnderDirectory` row in the attributes table mentions
`message-mappings`. The Examples section mentions the live setlist test.

## Out of scope

- WebSocket examples. The DSL works the same way; the README mentions it in
  one line.
- Closing SSE channels between tests.
- Fixing `resetAll()` in WireMock itself. That's worth raising upstream, but
  the extension needs the workaround for beta.39.
