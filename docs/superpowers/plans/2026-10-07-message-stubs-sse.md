# Message Stubs and SSE Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task by task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make WireMock message stubs and SSE work with this extension the
way HTTP stubs do:

- loaded from `message-mappings`;
- reset before each test;
- documented in the README;
- shown in a live-setlist example in both languages.

**Architecture:**

- Two small library fixes:
  - `WireMockServerCreator.firstExistingStubDirectory` also recognises
    `message-mappings`.
  - `WireMockMicronautExtension.beforeEach` clears message stubs and the
    message journal around `resetAll()`.
- Each example gets:
  - a declarative SSE client (`LiveSetlistClient`);
  - a Reactor service (`LiveSetlistService`);
  - a test driven by classpath `mappings` plus `message-mappings`.

**Tech Stack:** Java 25, Kotlin 2.4.20 + KSP, Micronaut 5.2.1, Netty
`micronaut-http-client` (SSE), `micronaut-reactor`, WireMock
4.0.0-beta.39, AssertJ, JUnit.

**Spec:** `docs/superpowers/specs/2026-10-07-message-stubs-sse-design.md`

## Global Constraints

- Work in the repo root (this worktree). Paths are relative to it.
- Before any `./gradlew`, run
  `export JAVA_HOME=~/.sdkman/candidates/java/25-tem`.
- Commit with `git commit --no-gpg-sign`. End every commit message with
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Run `./gradlew spotlessApply` before committing Java or Kotlin changes.
- No new `@ConfigureWireMock` attributes.
- WireMock stays at `4.0.0-beta.39`. Its `resetAll()` does not clear message
  stubs or the message journal; that is fixed upstream separately.
- The Java and Kotlin examples are identical in behaviour, stub files and
  tests, and package `example`.
- Gig id: `radiohead-2026`. Songs: `Airbag`, then `Paranoid Android`. End
  event data: `encore over`.
- Service id `live-setlist`, property
  `micronaut.http.services.live-setlist.url`, real value
  `https://live.example.com`.

## Review Focus

1. **Trigger before connect.** If the trigger fires before the SSE client
   has connected, the events have no channel and are silently lost. Every
   test waits on `listAllMessageChannels()` before triggering, and the README
   says so (Tasks 2, 3, 4, 5).
2. **`resetWireMockServer = false`.** That server must keep programmatic
   message stubs, just as it keeps HTTP stubs. This is pinned in `ResetTest`
   (Task 2).
3. **Swapping the JDK client for the Netty client** could change the existing
   examples' behaviour (Optional 404s, faults becoming 502). Tasks 3 and 4
   run the whole example suite after the swap.
4. **A default directory with only `message-mappings`** (for example
   `src/test/resources/wiremock/message-mappings`) must be picked up. It goes
   through the same `firstExistingStubDirectory`, pinned in Task 1's unit
   test.
5. **The stream must end at the `end` event.** WireMock never closes an SSE
   stream, so `songs()` must complete on `end`, not hang. The examples' 5 s
   `future.get` pins this (Tasks 3, 4).

---

### Task 1: Recognise `message-mappings` stub directories

**Files:**
- Modify: `src/main/java/com/leeturner/wiremock/micronaut/internal/WireMockServerCreator.java`
  (`configureFiles` error message, `firstExistingStubDirectory`)
- Modify: `src/main/java/com/leeturner/wiremock/micronaut/ConfigureWireMock.java:57-60`
  (javadoc)
- Create: `src/test/message-stubs-only/message-mappings/message-only.json`
- Test: `src/test/java/com/leeturner/wiremock/micronaut/internal/WireMockServerCreatorTest.java`

**Interfaces:**
- Produces: `static Optional<String> firstExistingStubDirectory(List<String>)`.
  Its signature is unchanged. It now matches a directory that contains
  `mappings`, `__files` or `message-mappings`.

- [ ] **Step 1: Add the fixture directory**

`src/test/message-stubs-only/message-mappings/message-only.json`:

```json
{
  "name": "message-only",
  "trigger": {
    "type": "http-request",
    "requestPattern": { "method": "GET", "urlPath": "/never" }
  },
  "actions": [
    {
      "type": "send",
      "message": { "body": { "data": "unused" } },
      "channelTarget": {
        "type": "request-initiated",
        "channelType": "sse",
        "requestPattern": { "urlPath": "/never-stream" }
      }
    }
  ]
}
```

- [ ] **Step 2: Write the failing tests**

In `WireMockServerCreatorTest`, add this fixture next to `DirectoryStubs`:

```java
  @ConfigureWireMock(filesUnderDirectory = "src/test/message-stubs-only")
  static class MessageStubsOnlyDirectory {}
```

Then add this test. It needs
`import com.github.tomakehurst.wiremock.message.MessageStubMapping;`.

```java
  @Test
  void directoryWithOnlyMessageMappingsIsUsed() {
    WireMockServer server = create(MessageStubsOnlyDirectory.class);
    assertThat(server.getMessageStubMappingsList())
        .extracting(MessageStubMapping::getName)
        .containsExactly("message-only");
  }
```

Extend `firstCandidateWithMappingsOrFilesWins`: rename it to
`firstCandidateWithAnyStubFolderWins` and add this before its final
assertion:

```java
    Path withMessages =
        Files.createDirectories(tmp.resolve("withMessages/message-mappings")).getParent();
    assertThat(
            WireMockServerCreator.firstExistingStubDirectory(
                List.of(empty.toString(), withMessages.toString())))
        .contains(withMessages.toString());
```

In `missingDirectoryFails`, add `.hasMessageContaining("'message-mappings'")`.

- [ ] **Step 3: Run the tests and confirm they fail**

Run: `./gradlew test --tests '*WireMockServerCreatorTest'`

Expected: three FAILs.
- `directoryWithOnlyMessageMappingsIsUsed` fails with
  `ExtensionConfigurationException` "None of filesUnderDirectory".
- `firstCandidateWithAnyStubFolderWins` fails with an empty Optional.
- `missingDirectoryFails` fails because the message lacks
  `'message-mappings'`.

- [ ] **Step 4: Implement**

In `WireMockServerCreator.firstExistingStubDirectory`:

```java
  static Optional<String> firstExistingStubDirectory(List<String> candidates) {
    return candidates.stream()
        .filter(
            dir ->
                Files.isDirectory(Path.of(dir, "mappings"))
                    || Files.isDirectory(Path.of(dir, "__files"))
                    || Files.isDirectory(Path.of(dir, "message-mappings")))
        .findFirst();
  }
```

In `configureFiles`, change the error text:

```java
                          ("None of filesUnderDirectory %s for WireMock server '%s' on %s contains"
                                  + " a 'mappings', '__files' or 'message-mappings' directory.")
```

In `ConfigureWireMock`, update the two javadocs:

```java
  /** Classpath root holding {@code mappings}/{@code __files}/{@code message-mappings}. */
```

```java
  /**
   * Directories holding {@code mappings}/{@code __files}/{@code message-mappings}; the first
   * existing one is used.
   */
```

- [ ] **Step 5: Run the tests and confirm they pass**

Run: `./gradlew test --tests '*WireMockServerCreatorTest'`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApply
git add src/main src/test/message-stubs-only src/test/java/com/leeturner/wiremock/micronaut/internal/WireMockServerCreatorTest.java
git commit --no-gpg-sign -m "feat: Recognise message-mappings stub directories

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Reset message stubs and the message journal before each test

**Files:**
- Modify: `src/main/java/com/leeturner/wiremock/micronaut/internal/WireMockMicronautExtension.java`
  (`beforeEach`)
- Modify: `src/test/java/com/leeturner/wiremock/micronaut/testsupport/Http.java`
  (add `stream`)
- Create: `src/test/resources/sse-stubs/mappings/events.json`
- Create: `src/test/resources/sse-stubs/message-mappings/hello.json`
- Create: `src/test/java/com/leeturner/wiremock/micronaut/MessageStubsTest.java`
- Modify: `src/test/java/com/leeturner/wiremock/micronaut/ResetTest.java`

**Interfaces:**
- Produces: `Http.stream(String url)`, which returns
  `HttpResponse<Stream<String>>`. The response returns once headers arrive,
  and the lines are read lazily.

- [ ] **Step 1: Add the classpath fixtures**

`src/test/resources/sse-stubs/mappings/events.json`:

```json
{
  "mappings": [
    {
      "request": { "method": "GET", "urlPath": "/events" },
      "response": { "status": 200, "openSseChannel": true }
    },
    {
      "id": "bbbbbbbb-0000-4000-8000-000000000001",
      "request": { "method": "GET", "urlPath": "/trigger" },
      "response": { "status": 204 }
    }
  ]
}
```

`src/test/resources/sse-stubs/message-mappings/hello.json`:

```json
{
  "name": "hello on trigger",
  "trigger": { "type": "http-stub", "stubId": "bbbbbbbb-0000-4000-8000-000000000001" },
  "actions": [
    {
      "type": "send",
      "message": { "body": { "data": "hello" } },
      "channelTarget": {
        "type": "request-initiated",
        "channelType": "sse",
        "requestPattern": { "urlPath": "/events" }
      }
    }
  ]
}
```

- [ ] **Step 2: Add `Http.stream`**

Make `send` generic and add `stream`. Add `import java.util.stream.Stream;`.

```java
  public static HttpResponse<String> get(String url) {
    return send(
        HttpRequest.newBuilder(URI.create(url)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  /** Returns once headers arrive; the body lines are read lazily (for SSE). */
  public static HttpResponse<Stream<String>> stream(String url) {
    return send(
        HttpRequest.newBuilder(URI.create(url)).GET().build(),
        HttpResponse.BodyHandlers.ofLines());
  }

  public static HttpResponse<String> post(String url, String body) {
    return send(
        HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private static <T> HttpResponse<T> send(
      HttpRequest request, HttpResponse.BodyHandler<T> handler) {
    try {
      return CLIENT.send(request, handler);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
```

- [ ] **Step 3: Write the tests**

`src/test/java/com/leeturner/wiremock/micronaut/MessageStubsTest.java`:

```java
package com.leeturner.wiremock.micronaut;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.message;
import static com.github.tomakehurst.wiremock.client.WireMock.sendSse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.matching.RequestPatternBuilder.newRequestPattern;
import static com.github.tomakehurst.wiremock.message.MessagePattern.messagePattern;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.message.MessageStubMapping;
import com.leeturner.wiremock.micronaut.testsupport.Http;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@EnableWireMock(@ConfigureWireMock(filesUnderClasspath = "sse-stubs"))
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MessageStubsTest {

  @InjectWireMock WireMockServer server;

  @Test
  @Order(1)
  void fileMessageStubSendsAnSseEventWhenTriggered() throws Exception {
    server.messageStubFor(
        message()
            .withName("programmatic")
            .triggeredByHttpRequest(newRequestPattern().withUrl(urlPathEqualTo("/programmatic")))
            .willTriggerActions(
                sendSse("p")
                    .onChannelsMatching(
                        newRequestPattern().withUrl(urlPathEqualTo("/events")).build())));

    try (Stream<String> lines = Http.stream(server.baseUrl() + "/events").body()) {
      awaitOpenChannel();
      Http.get(server.baseUrl() + "/trigger");

      String data =
          CompletableFuture.supplyAsync(
                  () -> lines.filter(l -> l.startsWith("data:")).findFirst().orElseThrow())
              .get(5, SECONDS);
      assertThat(data.substring("data:".length()).strip()).isEqualTo("hello");
    }
    assertThat(
            server.waitForMessageEvent(
                messagePattern().withBody(equalTo("hello")).build(), Duration.ofSeconds(5)))
        .isPresent();
  }

  @Test
  @Order(2)
  void programmaticMessageStubsAndTheJournalAreResetButFileStubsStay() {
    assertThat(server.getMessageStubMappingsList())
        .extracting(MessageStubMapping::getName)
        .containsExactly("hello on trigger");
    assertThat(server.getAllMessageServeEvents()).isEmpty();
  }

  /** Events sent before the client connects have no channel and are dropped. */
  private void awaitOpenChannel() throws InterruptedException {
    long deadline = System.nanoTime() + SECONDS.toNanos(5);
    while (server.listAllMessageChannels().getChannels().isEmpty()) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("SSE channel never opened");
      }
      Thread.sleep(20);
    }
  }
}
```

In `ResetTest`, pin `resetWireMockServer = false` for message stubs too:
- Add these static imports: `WireMock.message`, `WireMock.sendSse`,
  `WireMock.urlPathEqualTo`, `RequestPatternBuilder.newRequestPattern`.
- In `stubBoth`, add the same message stub to both servers:

```java
    keep.messageStubFor(
        message()
            .triggeredByHttpRequest(newRequestPattern().withUrl(urlPathEqualTo("/m")))
            .willTriggerActions(
                sendSse("m")
                    .onChannelsMatching(newRequestPattern().withUrl(urlPathEqualTo("/s")).build())));
    reset.messageStubFor(
        message()
            .triggeredByHttpRequest(newRequestPattern().withUrl(urlPathEqualTo("/m")))
            .willTriggerActions(
                sendSse("m")
                    .onChannelsMatching(newRequestPattern().withUrl(urlPathEqualTo("/s")).build())));
```

Then add to `onlyTheResettingServerWasCleared`:

```java
    assertThat(reset.getMessageStubMappingsList()).isEmpty();
    assertThat(keep.getMessageStubMappingsList()).hasSize(1);
```

- [ ] **Step 4: Run the tests and confirm the reset assertions fail**

Run: `./gradlew test --tests '*MessageStubsTest' --tests '*ResetTest'`

Expected:
- `fileMessageStubSendsAnSseEventWhenTriggered` PASSES. WireMock already
  supports this, and it proves the fixtures are right.
- `programmaticMessageStubsAndTheJournalAreResetButFileStubsStay` FAILS: the
  names also contain `"programmatic"`.
- `onlyTheResettingServerWasCleared` FAILS: `reset` still has one message
  stub.

If test 1 fails, fix the fixtures or the test before going on. Check:
- the SSE frame prefix (`data:`);
- that the `MessagePattern` body matches the SSE `data`.

`SseAcceptanceTest` in `~/dev/wiremock/wiremock` (tag `4.0.0-beta.39`) is the
reference.

- [ ] **Step 5: Implement**

In `WireMockMicronautExtension.beforeEach`, replace the reset stream:

```java
    servers.values().stream()
        .filter(running -> running.options().resetWireMockServer())
        .map(RunningServer::server)
        .forEach(
            server -> {
              // resetAll() keeps message stubs and the message journal; clear them, then let
              // resetAll() reload the file stubs (HTTP and message).
              server.resetMessageStubs();
              server.resetAll();
              server.resetMessageJournal();
            });
```

- [ ] **Step 6: Run the whole library suite**

Run: `./gradlew test`

Expected: PASS. That includes `FileStubsSurviveResetTest`.

- [ ] **Step 7: Commit**

```bash
./gradlew spotlessApply
git add src
git commit --no-gpg-sign -m "fix: Reset message stubs and the message journal before each test

WireMock's resetAll() keeps both, so message stubs added in one test
leaked into the next.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Java example: live setlist over SSE

**Files:**
- Modify: `examples/java/build.gradle.kts`
- Modify: `examples/java/src/main/resources/application.properties`
- Create: `examples/java/src/main/java/example/LiveSetlistClient.java`
- Create: `examples/java/src/main/java/example/LiveSetlistService.java`
- Create: `examples/java/src/test/resources/stubs-by-server/live-setlist/mappings/live.json`
- Create: `examples/java/src/test/resources/stubs-by-server/live-setlist/message-mappings/live-setlist.json`
- Test: `examples/java/src/test/java/example/LiveSetlistTest.java`

**Interfaces:**
- Produces:
  - `LiveSetlistClient.live(String gigId)`, which returns
    `Publisher<Event<String>>`;
  - `LiveSetlistService.songs(String gigId)`, which returns `Flux<String>`.
- The stub files are copied verbatim into Kotlin in Task 4.

- [ ] **Step 1: Swap to the Netty client and add Reactor**

In `examples/java/build.gradle.kts`:
- replace `implementation("io.micronaut:micronaut-http-client-jdk")` with
  `implementation("io.micronaut:micronaut-http-client")`;
- add `implementation("io.micronaut.reactor:micronaut-reactor")`.

Only the Netty client ships `SseClientFactory`.

Run: `./gradlew :examples:java:test`

Expected: PASS. The existing tests are unaffected by the swap. If anything
fails, stop and report it (Review Focus 3).

- [ ] **Step 2: Add the stub files**

`examples/java/src/test/resources/stubs-by-server/live-setlist/mappings/live.json`:

```json
{
  "mappings": [
    {
      "name": "Live setlist stream",
      "request": { "method": "GET", "urlPath": "/gigs/radiohead-2026/live" },
      "response": { "status": 200, "openSseChannel": true }
    },
    {
      "name": "Start the gig",
      "id": "cccccccc-0000-4000-8000-000000000001",
      "request": { "method": "POST", "urlPath": "/gigs/radiohead-2026/start" },
      "response": { "status": 204 }
    }
  ]
}
```

`examples/java/src/test/resources/stubs-by-server/live-setlist/message-mappings/live-setlist.json`:

```json
{
  "name": "Radiohead 2026 setlist",
  "trigger": { "type": "http-stub", "stubId": "cccccccc-0000-4000-8000-000000000001" },
  "actions": [
    {
      "type": "send",
      "message": { "body": { "data": "Airbag" }, "headers": { "event": "song" } },
      "channelTarget": {
        "type": "request-initiated",
        "channelType": "sse",
        "requestPattern": { "urlPath": "/gigs/radiohead-2026/live" }
      }
    },
    {
      "type": "send",
      "message": { "body": { "data": "Paranoid Android" }, "headers": { "event": "song" } },
      "channelTarget": {
        "type": "request-initiated",
        "channelType": "sse",
        "requestPattern": { "urlPath": "/gigs/radiohead-2026/live" }
      }
    },
    {
      "type": "send",
      "message": { "body": { "data": "encore over" }, "headers": { "event": "end" } },
      "channelTarget": {
        "type": "request-initiated",
        "channelType": "sse",
        "requestPattern": { "urlPath": "/gigs/radiohead-2026/live" }
      }
    }
  ]
}
```

- [ ] **Step 3: Write the failing test**

`examples/java/src/test/java/example/LiveSetlistTest.java`:

```java
package example;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.message.MessagePattern.messagePattern;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import com.leeturner.wiremock.micronaut.EnableWireMock;
import com.leeturner.wiremock.micronaut.InjectWireMock;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * The upstream streams a gig's setlist as server-sent events. mappings/live.json opens the SSE
 * channel and declares a "start" stub; message-mappings/live-setlist.json sends the songs when
 * that stub is hit.
 */
@MicronautTest
@EnableWireMock(
    @ConfigureWireMock(
        name = "live-setlist",
        baseUrlProperties = "micronaut.http.services.live-setlist.url",
        filesUnderClasspath = "stubs-by-server/live-setlist"))
class LiveSetlistTest {

  static final String GIG = "radiohead-2026";

  @Inject LiveSetlistService service;

  @InjectWireMock("live-setlist")
  WireMockServer liveSetlist;

  @Test
  void streamsSongsUntilTheGigEnds() throws Exception {
    CompletableFuture<List<String>> songs = service.songs(GIG).collectList().toFuture();

    awaitOpenChannel();
    HttpClient.newHttpClient()
        .send(
            HttpRequest.newBuilder(URI.create(liveSetlist.baseUrl() + "/gigs/" + GIG + "/start"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.discarding());

    assertThat(songs.get(5, SECONDS)).containsExactly("Airbag", "Paranoid Android");
    assertThat(
            liveSetlist.waitForMessageEvent(
                messagePattern().withBody(equalTo("encore over")).build(),
                Duration.ofSeconds(5)))
        .isPresent();
  }

  /** Trigger only once the app is connected: events sent before then have no channel. */
  private void awaitOpenChannel() throws InterruptedException {
    long deadline = System.nanoTime() + SECONDS.toNanos(5);
    while (liveSetlist.listAllMessageChannels().getChannels().isEmpty()) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("The app never opened the SSE stream");
      }
      Thread.sleep(20);
    }
  }
}
```

Run: `./gradlew :examples:java:test --tests '*LiveSetlistTest'`

Expected: FAIL to compile (`LiveSetlistService` is not defined).

- [ ] **Step 4: Implement the client, the service and the property**

Append to `examples/java/src/main/resources/application.properties`:

```properties
micronaut.http.services.live-setlist.url=https://live.example.com
```

`examples/java/src/main/java/example/LiveSetlistClient.java`:

```java
package example;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.sse.Event;
import org.reactivestreams.Publisher;

/** Streams a gig's setlist as server-sent events: "song" per song, then "end". */
@Client("live-setlist")
public interface LiveSetlistClient {

  @Get(value = "/gigs/{gigId}/live", processes = MediaType.TEXT_EVENT_STREAM)
  Publisher<Event<String>> live(String gigId);
}
```

`examples/java/src/main/java/example/LiveSetlistService.java`:

```java
package example;

import io.micronaut.http.sse.Event;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;

@Singleton
public class LiveSetlistService {

  private final LiveSetlistClient client;

  public LiveSetlistService(LiveSetlistClient client) {
    this.client = client;
  }

  /** Song titles as they are played; completes when the gig ends. */
  public Flux<String> songs(String gigId) {
    return Flux.from(client.live(gigId))
        .takeWhile(event -> !"end".equals(event.getName()))
        .filter(event -> "song".equals(event.getName()))
        .map(Event::getData);
  }
}
```

Match the constructor and field style of the existing `ArtistService.java`
if it differs.

- [ ] **Step 5: Run the example suite**

Run: `./gradlew :examples:java:test`

Expected: PASS, including `LiveSetlistTest`.

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApply
git add examples/java
git commit --no-gpg-sign -m "test(examples): Stream a live setlist over SSE in Java

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Kotlin example: live setlist over SSE

**Files:**
- Modify: `examples/kotlin/build.gradle.kts`
- Modify: `examples/kotlin/src/main/resources/application.properties`
- Create: `examples/kotlin/src/main/kotlin/example/LiveSetlistClient.kt`
- Create: `examples/kotlin/src/main/kotlin/example/LiveSetlistService.kt`
- Create: `examples/kotlin/src/test/resources/stubs-by-server/live-setlist/mappings/live.json`
  (byte-identical to Task 3's)
- Create: `examples/kotlin/src/test/resources/stubs-by-server/live-setlist/message-mappings/live-setlist.json`
  (byte-identical to Task 3's)
- Test: `examples/kotlin/src/test/kotlin/example/LiveSetlistTest.kt`

**Interfaces:**
- Produces:
  - `LiveSetlistClient.live(gigId: String)`, which returns
    `Publisher<Event<String>>`;
  - `LiveSetlistService.songs(gigId: String)`, which returns
    `Flux<String>`.

- [ ] **Step 1: Swap to the Netty client and add Reactor**

In `examples/kotlin/build.gradle.kts`:
- replace `implementation("io.micronaut:micronaut-http-client-jdk")` with
  `implementation("io.micronaut:micronaut-http-client")`;
- add `implementation("io.micronaut.reactor:micronaut-reactor")`.

Run: `./gradlew :examples:kotlin:test`

Expected: PASS. If not, stop and report.

- [ ] **Step 2: Copy the stub files**

```bash
mkdir -p examples/kotlin/src/test/resources/stubs-by-server
cp -R examples/java/src/test/resources/stubs-by-server/live-setlist examples/kotlin/src/test/resources/stubs-by-server/
```

- [ ] **Step 3: Write the failing test**

`examples/kotlin/src/test/kotlin/example/LiveSetlistTest.kt`:

```kotlin
package example

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.message.MessagePattern.messagePattern
import com.leeturner.wiremock.micronaut.ConfigureWireMock
import com.leeturner.wiremock.micronaut.EnableWireMock
import com.leeturner.wiremock.micronaut.InjectWireMock
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit.SECONDS

/**
 * The upstream streams a gig's setlist as server-sent events. mappings/live.json opens the SSE
 * channel and declares a "start" stub; message-mappings/live-setlist.json sends the songs when
 * that stub is hit.
 */
@MicronautTest
@EnableWireMock(
    ConfigureWireMock(
        name = "live-setlist",
        baseUrlProperties = ["micronaut.http.services.live-setlist.url"],
        filesUnderClasspath = "stubs-by-server/live-setlist",
    ),
)
class LiveSetlistTest {

    private val gig = "radiohead-2026"

    @Inject
    lateinit var service: LiveSetlistService

    @InjectWireMock("live-setlist")
    lateinit var liveSetlist: WireMockServer

    @Test
    fun `streams songs until the gig ends`() {
        val songs = service.songs(gig).collectList().toFuture()

        awaitOpenChannel()
        HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("${liveSetlist.baseUrl()}/gigs/$gig/start"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.discarding(),
        )

        assertThat(songs.get(5, SECONDS)).containsExactly("Airbag", "Paranoid Android")
        assertThat(
            liveSetlist.waitForMessageEvent(
                messagePattern().withBody(equalTo("encore over")).build(),
                Duration.ofSeconds(5),
            ),
        ).isPresent
    }

    /** Trigger only once the app is connected: events sent before then have no channel. */
    private fun awaitOpenChannel() {
        val deadline = System.nanoTime() + SECONDS.toNanos(5)
        while (liveSetlist.listAllMessageChannels().channels.isEmpty()) {
            if (System.nanoTime() > deadline) throw AssertionError("The app never opened the SSE stream")
            Thread.sleep(20)
        }
    }
}
```

Run: `./gradlew :examples:kotlin:test --tests '*LiveSetlistTest'`

Expected: FAIL to compile (`LiveSetlistService` is unresolved).

- [ ] **Step 4: Implement the client, the service and the property**

Append to `examples/kotlin/src/main/resources/application.properties`:

```properties
micronaut.http.services.live-setlist.url=https://live.example.com
```

`examples/kotlin/src/main/kotlin/example/LiveSetlistClient.kt`:

```kotlin
package example

import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.sse.Event
import org.reactivestreams.Publisher

/** Streams a gig's setlist as server-sent events: "song" per song, then "end". */
@Client("live-setlist")
interface LiveSetlistClient {

    @Get(value = "/gigs/{gigId}/live", processes = [MediaType.TEXT_EVENT_STREAM])
    fun live(gigId: String): Publisher<Event<String>>
}
```

`examples/kotlin/src/main/kotlin/example/LiveSetlistService.kt`:

```kotlin
package example

import jakarta.inject.Singleton
import reactor.core.publisher.Flux

@Singleton
class LiveSetlistService(private val client: LiveSetlistClient) {

    /** Song titles as they are played; completes when the gig ends. */
    fun songs(gigId: String): Flux<String> =
        Flux.from(client.live(gigId))
            .takeWhile { it.name != "end" }
            .filter { it.name == "song" }
            .map { it.data }
}
```

Match the existing `ArtistService.kt` style (for example `open`, or the
allopen setup) if it differs.

- [ ] **Step 5: Run the example suite**

Run: `./gradlew :examples:kotlin:test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApply
git add examples/kotlin
git commit --no-gpg-sign -m "test(examples): Stream a live setlist over SSE in Kotlin

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: README

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Update the attributes table rows**

Replace the two rows:

```markdown
| `filesUnderClasspath` | `""` | Classpath root with `mappings`/`__files`/`message-mappings`. |
| `filesUnderDirectory` | `{}` | Directories with `mappings`/`__files`/`message-mappings` (first existing wins; takes precedence over classpath). |
```

- [ ] **Step 2: Add the section**

Insert it after "## Several servers" and before "## Gotchas":

````markdown
## Message stubs and server-sent events

WireMock's message stubs and SSE need no extra configuration. Next to
`mappings`, put message stubs in `message-mappings`:

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
  then have no channel and are dropped. Wait until
  `server.listAllMessageChannels().getChannels()` is non-empty.
- To consume SSE with a Micronaut declarative client, use the Netty
  `io.micronaut:micronaut-http-client`. The JDK client does not support
  SSE.
- WebSockets work the same way, with `openWebsocketChannel` and
  `"channelType": "websocket"`.
````

- [ ] **Step 3: Update the examples text and table**

Change the paragraph above the examples table so it also mentions the live
setlist:

```markdown
[`examples/java`](examples/java) and [`examples/kotlin`](examples/kotlin) are
the same small Micronaut service: `GET /artists/{mbid}` combines an artist
from MusicBrainz (a `${musicbrainz.url}` client) with recent setlists from
setlist.fm (a `setlist-fm` service id client), and `LiveSetlistService`
follows a gig's setlist from an SSE feed (a `live-setlist` service id
client). Each test class shows one way to stub them:
```

Add a table row:

```markdown
| `LiveSetlistTest` | SSE: `mappings` opens the stream, `message-mappings` sends the songs when a trigger stub is hit, and `waitForMessageEvent` verifies what was sent. |
```

- [ ] **Step 4: Verify the whole build**

Run: `./gradlew build`

Expected: BUILD SUCCESSFUL. That covers spotless, the library tests and both
examples.

- [ ] **Step 5: Commit**

```bash
git add README.md
git commit --no-gpg-sign -m "docs: Describe message stubs and SSE

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
