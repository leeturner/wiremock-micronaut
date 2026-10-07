# Better Examples Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task by task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the bare `@Client` examples with small, complete Micronaut
services (Java and Kotlin) that aggregate MusicBrainz and setlist.fm, tested
with programmatic stubs, per-server classpath mapping files, the default stub
directory, and `__files` response bodies.

**Architecture:** Each example module becomes a Micronaut application with
`GET /artists/{mbid}` → `ArtistService` → two declarative clients. Tests call
the real endpoint through an injected `@Client("/") HttpClient`, with both
upstreams replaced by named WireMock servers. One test class per stubbing
technique. The extension gets one new test for `bodyFileName`.

**Tech Stack:** Java 25, Kotlin 2.4.20 + KSP, Micronaut 5.2.1 (Gradle
plugins 5.0.2), Micronaut Serde, JDK HTTP client, Netty server, WireMock
4.0.0-beta.39, AssertJ, JUnit 6.

**Spec:** `docs/superpowers/specs/2026-10-07-better-examples-design.md`

## Global Constraints

- Work in the repo root (this worktree). Paths are relative to it.
- Commit with `git commit --no-gpg-sign`. End every commit message with
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Java and Kotlin examples are identical in behaviour, endpoints, tests and
  stub files; each is idiomatic in its language.
- Package `example` in both examples.
- The only API key anywhere is `dummy-setlist-fm-api-key`.
- MusicBrainz `User-Agent`: `wiremock-micronaut-example/1.0`.
- Test artist MBID: `a74b1b7f-71a5-4011-9441-d0b5e4122711` (Radiohead).
- MusicBrainz client: `@Client("${musicbrainz.url}")`, property
  `musicbrainz.url`. setlist.fm client: `@Client("setlist-fm")`, property
  `micronaut.http.services.setlist-fm.url`.
- Upstream 404 → our 404; any other upstream failure → our 502.
- Java sources are formatted by Spotless (google-java-format): run
  `./gradlew spotlessApply` before committing Java changes.
- No changes to the extension's `src/main`.

## Review Focus

Spec-implied inputs no other test covers. Each has a test in the owning task.

1. **Upstream connection fault** (setlist.fm resets the connection): expect
   502, not 500. Test: `connectionFaultBecomesBadGateway` (Tasks 2, 4).
2. **MusicBrainz artist without `country`** (common for real artists): expect
   200 with `country: null`, not a deserialization error. Test:
   `missingCountryIsNull` (Tasks 2, 4).
3. **setlist.fm page without a `setlist` array**: expect an empty
   `recentSetlists`, not an NPE. Test: `pageWithoutSetlistsIsEmpty`
   (Tasks 2, 4).
4. **Unknown upstream JSON fields** (`sort-name`, `life-span`, `tour`, ...):
   must be ignored. Every stub body includes such fields; if Serde rejects
   them, add `@JsonIgnoreProperties(ignoreUnknown = true)` to the upstream
   DTOs.
5. **Default directory resolved from the IDE**: `src/test/resources/wiremock`
   is relative to the working directory. Gradle uses the module directory;
   IntelliJ's Gradle runner does too. `DefaultDirectoryTest` asserts data that
   exists only in the default directory, so a wrong working directory fails
   loudly rather than silently passing.

Known product decision, not tested beyond spec: the real setlist.fm returns
404 when an artist has no setlists; per the spec this becomes our 404.

---

### Task 1: Extension test for `__files` response bodies

**Files:**
- Modify: `src/test/java/com/leeturner/wiremock/micronaut/FileStubsSurviveResetTest.java`
- Create: `src/test/resources/classpath-stubs/mappings/from-body-file.json`
- Create: `src/test/resources/classpath-stubs/__files/body.txt`

**Interfaces:** none.

- [ ] **Step 1: Write the failing test**

Add to `FileStubsSurviveResetTest` (after the `@Order(2)` test):

```java
  @Test
  @Order(3)
  void bodyFileNameServesTheBodyFromFilesDirectory() {
    assertThat(Http.get(server.baseUrl() + "/from-body-file").body().strip())
        .isEqualTo("from __files");
  }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew test --tests '*FileStubsSurviveResetTest'`
Expected: FAIL, the body is WireMock's 404 "Request was not matched" text.

- [ ] **Step 3: Add the mapping and body file**

`src/test/resources/classpath-stubs/mappings/from-body-file.json`:

```json
{"request": {"method": "GET", "url": "/from-body-file"}, "response": {"status": 200, "bodyFileName": "body.txt"}}
```

`src/test/resources/classpath-stubs/__files/body.txt`:

```
from __files
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew test`
Expected: PASS (all root tests; nothing else loads `classpath-stubs`
except `FileStubsSurviveResetTest` and `WireMockServerCreatorTest`, which
only checks `/from-classpath`).

- [ ] **Step 5: Commit**

```bash
./gradlew spotlessApply
git add src/test
git commit --no-gpg-sign -m "test: Cover bodyFileName responses from classpath __files

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Java example service with programmatic-stub tests

**Files:**
- Modify: `gradle/libs.versions.toml` (add `micronaut-application` plugin)
- Modify: `build.gradle.kts` (declare it `apply false`)
- Modify: `examples/java/build.gradle.kts`
- Modify: `examples/java/src/main/resources/application.properties`
- Delete: `examples/java/src/main/java/example/UsersClient.java`
- Delete: `examples/java/src/test/java/example/UsersClientTest.java`
- Delete: `examples/java/src/test/java/example/SetlistFmClientTest.java`
- Replace: `examples/java/src/main/java/example/SetlistFmClient.java`
- Create in `examples/java/src/main/java/example/`: `Application.java`,
  `ArtistController.java`, `ArtistService.java`, `MusicBrainzClient.java`,
  `Artist.java`, `MusicBrainzArtist.java`, `SetlistPage.java`
- Create: `examples/java/src/test/java/example/ProgrammaticStubsTest.java`
- Create: `examples/java/src/test/java/example/MicronautBomCompatibilityTest.java`

**Interfaces:**
- Produces (used by Task 3):
  - `example.Artist(String name, String country, String type, List<Artist.SetlistSummary> recentSetlists)`
  - `example.Artist.SetlistSummary(String eventDate, String venue, String city)`
  - `GET /artists/{mbid}` returning `Artist` JSON; 404/502 on upstream errors.
  - Gradle plugin alias `libs.plugins.micronaut.application`.

- [ ] **Step 1: Add the application plugin to the build**

`gradle/libs.versions.toml`, under `[plugins]` after `micronaut-library`:

```toml
micronaut-application = { id = "io.micronaut.application", version = "5.0.2" }
```

`build.gradle.kts`, in `plugins { }` after the `micronaut.library` line:

```kotlin
    alias(libs.plugins.micronaut.application) apply false
```

Replace `examples/java/build.gradle.kts` with:

```kotlin
plugins {
    alias(libs.plugins.micronaut.application)
}

repositories { mavenCentral() }

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

application {
    mainClass = "example.Application"
}

micronaut {
    version(providers.gradleProperty("micronautVersion").getOrElse(libs.versions.micronaut.get()))
    testRuntime("junit5")
}

dependencies {
    annotationProcessor("io.micronaut.serde:micronaut-serde-processor")
    implementation("io.micronaut:micronaut-http-server-netty")
    implementation("io.micronaut:micronaut-http-client-jdk")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    runtimeOnly("ch.qos.logback:logback-classic")
    testImplementation(project(":"))
    testImplementation("org.assertj:assertj-core")
}
```

Replace `examples/java/src/main/resources/application.properties` with:

```properties
musicbrainz.url=https://musicbrainz.org
micronaut.http.services.setlist-fm.url=https://api.setlist.fm
setlist-fm.api-key=dummy-setlist-fm-api-key
```

Delete the old example code:

```bash
git rm -q examples/java/src/main/java/example/UsersClient.java \
  examples/java/src/test/java/example/UsersClientTest.java \
  examples/java/src/test/java/example/SetlistFmClientTest.java
```

- [ ] **Step 2: Move the BOM compatibility check into its own test**

`examples/java/src/test/java/example/MicronautBomCompatibilityTest.java`:

```java
package example;

import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonSchema;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leeturner.wiremock.micronaut.EnableWireMock;
import com.leeturner.wiremock.micronaut.InjectWireMock;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

/**
 * Not an example: guards against the Micronaut platform BOM overriding WireMock's dependencies
 * (JSON schema matching broke under an unshaded WireMock).
 */
@EnableWireMock
class MicronautBomCompatibilityTest {

  @InjectWireMock WireMockServer wireMock;

  @Test
  void jsonSchemaMatchingWorksUnderTheMicronautBom() throws Exception {
    wireMock.stubFor(
        post("/schema")
            .withRequestBody(matchingJsonSchema("{\"type\":\"object\",\"required\":[\"name\"]}"))
            .willReturn(ok("valid")));
    try (HttpClient client = HttpClient.newHttpClient()) {
      HttpResponse<String> response =
          client.send(
              HttpRequest.newBuilder(URI.create(wireMock.baseUrl() + "/schema"))
                  .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"lee\"}"))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(response.body()).isEqualTo("valid");
    }
  }
}
```

- [ ] **Step 3: Write the failing endpoint tests**

`examples/java/src/test/java/example/ProgrammaticStubsTest.java`:

```java
package example;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import com.leeturner.wiremock.micronaut.EnableWireMock;
import com.leeturner.wiremock.micronaut.InjectWireMock;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Every stub is declared in the test. Neither server configures files, so both also load the
 * default directory (src/test/resources/wiremock); stubs added here win because WireMock prefers
 * the most recently added stub.
 */
@MicronautTest
@EnableWireMock({
  @ConfigureWireMock(name = "musicbrainz", baseUrlProperties = "musicbrainz.url"),
  @ConfigureWireMock(
      name = "setlist-fm",
      baseUrlProperties = "micronaut.http.services.setlist-fm.url")
})
class ProgrammaticStubsTest {

  static final String MBID = "a74b1b7f-71a5-4011-9441-d0b5e4122711";
  static final String ARTIST_PATH = "/ws/2/artist/" + MBID;
  static final String SETLISTS_PATH = "/rest/1.0/artist/" + MBID + "/setlists";

  @Inject
  @Client("/")
  HttpClient http;

  @InjectWireMock("musicbrainz")
  WireMockServer musicBrainz;

  @InjectWireMock("setlist-fm")
  WireMockServer setlistFm;

  @BeforeEach
  void stubHappyPath() {
    musicBrainz.stubFor(
        get(urlPathEqualTo(ARTIST_PATH))
            .withQueryParam("fmt", equalTo("json"))
            .willReturn(
                okJson(
                    """
                    {"id": "%s", "name": "Radiohead", "sort-name": "Radiohead",
                     "type": "Group", "country": "GB"}
                    """
                        .formatted(MBID))));
    setlistFm.stubFor(
        get(urlPathEqualTo(SETLISTS_PATH))
            .willReturn(
                okJson(
                    """
                    {"type": "setlists", "itemsPerPage": 20, "page": 1, "total": 1,
                     "setlist": [{"eventDate": "04-11-2025",
                                  "venue": {"name": "Movistar Arena",
                                            "city": {"name": "Madrid"}}}]}
                    """)));
  }

  @Test
  void combinesBothUpstreams() {
    assertThat(artist())
        .isEqualTo(
            new Artist(
                "Radiohead",
                "GB",
                "Group",
                List.of(
                    new Artist.SetlistSummary("04-11-2025", "Movistar Arena", "Madrid"))));
  }

  @Test
  void sendsTheApiKeyAndUserAgent() {
    artist();

    setlistFm.verify(
        getRequestedFor(urlPathEqualTo(SETLISTS_PATH))
            .withHeader("x-api-key", equalTo("dummy-setlist-fm-api-key")));
    musicBrainz.verify(
        getRequestedFor(urlPathEqualTo(ARTIST_PATH))
            .withHeader("User-Agent", equalTo("wiremock-micronaut-example/1.0")));
  }

  @Test
  void unknownArtistIsNotFound() {
    musicBrainz.stubFor(get(urlPathEqualTo(ARTIST_PATH)).willReturn(notFound()));

    assertStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void upstreamErrorBecomesBadGateway() {
    setlistFm.stubFor(get(urlPathEqualTo(SETLISTS_PATH)).willReturn(serverError()));

    assertStatus(HttpStatus.BAD_GATEWAY);
  }

  @Test
  void connectionFaultBecomesBadGateway() {
    setlistFm.stubFor(
        get(urlPathEqualTo(SETLISTS_PATH))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    assertStatus(HttpStatus.BAD_GATEWAY);
  }

  @Test
  void missingCountryIsNull() {
    musicBrainz.stubFor(
        get(urlPathEqualTo(ARTIST_PATH))
            .willReturn(okJson("{\"name\": \"Radiohead\", \"type\": \"Group\"}")));

    assertThat(artist().country()).isNull();
  }

  @Test
  void pageWithoutSetlistsIsEmpty() {
    setlistFm.stubFor(
        get(urlPathEqualTo(SETLISTS_PATH))
            .willReturn(okJson("{\"type\": \"setlists\", \"total\": 0}")));

    assertThat(artist().recentSetlists()).isEmpty();
  }

  private Artist artist() {
    return http.toBlocking().retrieve("/artists/" + MBID, Artist.class);
  }

  private void assertStatus(HttpStatus expected) {
    assertThatThrownBy(() -> http.toBlocking().retrieve("/artists/" + MBID))
        .isInstanceOfSatisfying(
            HttpClientResponseException.class,
            e -> assertThat(e.getStatus()).isEqualTo(expected));
  }
}
```

- [ ] **Step 4: Run it to verify it fails**

Run: `./gradlew :examples:java:test`
Expected: FAIL to compile: `Artist` cannot be found.

- [ ] **Step 5: Write the DTOs**

`Artist.java`:

```java
package example;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

/** Our response. */
@Serdeable
public record Artist(
    String name, String country, String type, List<SetlistSummary> recentSetlists) {

  @Serdeable
  public record SetlistSummary(String eventDate, String venue, String city) {}
}
```

`MusicBrainzArtist.java`:

```java
package example;

import io.micronaut.serde.annotation.Serdeable;

/** The fields we use from MusicBrainz's artist lookup. */
@Serdeable
public record MusicBrainzArtist(String name, String country, String type) {}
```

`SetlistPage.java`:

```java
package example;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

/** The fields we use from a setlist.fm page of setlists. */
@Serdeable
public record SetlistPage(List<Setlist> setlist) {

  @Serdeable
  public record Setlist(String eventDate, Venue venue) {}

  @Serdeable
  public record Venue(String name, City city) {}

  @Serdeable
  public record City(String name) {}
}
```

- [ ] **Step 6: Write the clients**

`MusicBrainzClient.java`:

```java
package example;

import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.client.annotation.Client;
import java.util.Optional;

/** MusicBrainz asks every client to identify itself with a User-Agent. */
@Client("${musicbrainz.url}")
@Header(name = "User-Agent", value = "wiremock-micronaut-example/1.0")
public interface MusicBrainzClient {

  /** Empty when MusicBrainz returns 404. */
  @Get("/ws/2/artist/{mbid}?fmt=json")
  Optional<MusicBrainzArtist> artist(String mbid);
}
```

`SetlistFmClient.java` (replace the file):

```java
package example;

import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.client.annotation.Client;
import java.util.Optional;

/** setlist.fm authenticates with an x-api-key header. */
@Client("setlist-fm")
@Header(name = "x-api-key", value = "${setlist-fm.api-key}")
public interface SetlistFmClient {

  /** Empty when setlist.fm returns 404. */
  @Get("/rest/1.0/artist/{mbid}/setlists")
  Optional<SetlistPage> setlists(String mbid);
}
```

- [ ] **Step 7: Write the service, controller and main class**

`ArtistService.java`:

```java
package example;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Singleton
public class ArtistService {

  private final MusicBrainzClient musicBrainz;
  private final SetlistFmClient setlistFm;

  ArtistService(MusicBrainzClient musicBrainz, SetlistFmClient setlistFm) {
    this.musicBrainz = musicBrainz;
    this.setlistFm = setlistFm;
  }

  public Artist artist(String mbid) {
    MusicBrainzArtist artist = call(() -> musicBrainz.artist(mbid));
    SetlistPage page = call(() -> setlistFm.setlists(mbid));
    List<Artist.SetlistSummary> setlists =
        page.setlist() == null
            ? List.of()
            : page.setlist().stream()
                .map(
                    s ->
                        new Artist.SetlistSummary(
                            s.eventDate(), s.venue().name(), s.venue().city().name()))
                .toList();
    return new Artist(artist.name(), artist.country(), artist.type(), setlists);
  }

  /** Upstream 404 becomes our 404; any other upstream failure becomes 502. */
  private static <T> T call(Supplier<Optional<T>> upstream) {
    try {
      return upstream.get().orElseThrow(ArtistService::notFound);
    } catch (HttpClientResponseException e) {
      throw e.getStatus() == HttpStatus.NOT_FOUND ? notFound() : badGateway();
    } catch (HttpClientException e) {
      throw badGateway();
    }
  }

  private static HttpStatusException notFound() {
    return new HttpStatusException(HttpStatus.NOT_FOUND, "Artist not found");
  }

  private static HttpStatusException badGateway() {
    return new HttpStatusException(HttpStatus.BAD_GATEWAY, "Upstream call failed");
  }
}
```

`ArtistController.java`:

```java
package example;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Controller("/artists")
public class ArtistController {

  private final ArtistService artists;

  ArtistController(ArtistService artists) {
    this.artists = artists;
  }

  @Get("/{mbid}")
  public Artist artist(String mbid) {
    return artists.artist(mbid);
  }
}
```

`Application.java`:

```java
package example;

import io.micronaut.runtime.Micronaut;

public class Application {
  public static void main(String[] args) {
    Micronaut.run(Application.class, args);
  }
}
```

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./gradlew spotlessApply :examples:java:test`
Expected: PASS, 8 tests (7 in `ProgrammaticStubsTest`, 1 in
`MicronautBomCompatibilityTest`).

If `unknownArtistIsNotFound` gets 502, Micronaut threw on 404 instead of
returning `Optional.empty()`; `call` already maps that to 404, so check the
exception type being thrown with `--info`. If deserialization fails on
unknown fields, see Review Focus item 4.

- [ ] **Step 9: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts examples/java
git commit --no-gpg-sign -m "feat(examples): Turn the Java example into an artist service

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Java mapping-file tests (classpath per server and default directory)

**Files:**
- Create under `examples/java/src/test/resources/`:
  - `wiremock/mappings/setlists.json`
  - `wiremock/__files/setlists.json`
  - `stubs-by-server/musicbrainz/mappings/artist.json`
  - `stubs-by-server/musicbrainz/__files/radiohead.json`
  - `stubs-by-server/setlist-fm/mappings/setlists.json`
  - `stubs-by-server/setlist-fm/__files/setlists.json`
- Create: `examples/java/src/test/java/example/ClasspathStubsTest.java`
- Create: `examples/java/src/test/java/example/DefaultDirectoryTest.java`

**Interfaces:**
- Consumes: `example.Artist`, `example.Artist.SetlistSummary`,
  `GET /artists/{mbid}` (Task 2).
- Produces (used by Task 5): the six stub files above, copied verbatim.

- [ ] **Step 1: Write the failing tests**

`ClasspathStubsTest.java`:

```java
package example;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import com.leeturner.wiremock.micronaut.EnableWireMock;
import com.leeturner.wiremock.micronaut.InjectWireMock;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Each server loads its own mappings from the classpath (src/test/resources/stubs-by-server/<name>).
 * Response bodies live in each folder's __files directory and are referenced with bodyFileName.
 */
@MicronautTest
@EnableWireMock({
  @ConfigureWireMock(
      name = "musicbrainz",
      baseUrlProperties = "musicbrainz.url",
      filesUnderClasspath = "stubs-by-server/musicbrainz"),
  @ConfigureWireMock(
      name = "setlist-fm",
      baseUrlProperties = "micronaut.http.services.setlist-fm.url",
      filesUnderClasspath = "stubs-by-server/setlist-fm")
})
class ClasspathStubsTest {

  static final String MBID = "a74b1b7f-71a5-4011-9441-d0b5e4122711";

  @Inject
  @Client("/")
  HttpClient http;

  @InjectWireMock("setlist-fm")
  WireMockServer setlistFm;

  @Test
  void servesTheArtistFromMappingFiles() {
    Artist artist = http.toBlocking().retrieve("/artists/" + MBID, Artist.class);

    assertThat(artist)
        .isEqualTo(
            new Artist(
                "Radiohead",
                "GB",
                "Group",
                List.of(
                    new Artist.SetlistSummary("14-11-2025", "Unipol Arena", "Bologna"),
                    new Artist.SetlistSummary("04-11-2025", "Movistar Arena", "Madrid"))));
  }

  @Test
  void aStubInTheTestOverridesAFileStub() {
    setlistFm.stubFor(
        get(urlPathEqualTo("/rest/1.0/artist/" + MBID + "/setlists"))
            .atPriority(1)
            .willReturn(serverError()));

    assertThatThrownBy(() -> http.toBlocking().retrieve("/artists/" + MBID))
        .isInstanceOfSatisfying(
            HttpClientResponseException.class,
            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
  }
}
```

`DefaultDirectoryTest.java`:

```java
package example;

import static org.assertj.core.api.Assertions.assertThat;

import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import com.leeturner.wiremock.micronaut.EnableWireMock;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * setlist-fm configures no files, so it loads the first default directory that exists:
 * src/test/resources/wiremock. Every server without files configuration shares that directory,
 * so musicbrainz points at its own classpath folder instead.
 */
@MicronautTest
@EnableWireMock({
  @ConfigureWireMock(
      name = "musicbrainz",
      baseUrlProperties = "musicbrainz.url",
      filesUnderClasspath = "stubs-by-server/musicbrainz"),
  @ConfigureWireMock(
      name = "setlist-fm",
      baseUrlProperties = "micronaut.http.services.setlist-fm.url")
})
class DefaultDirectoryTest {

  static final String MBID = "a74b1b7f-71a5-4011-9441-d0b5e4122711";

  @Inject
  @Client("/")
  HttpClient http;

  @Test
  void setlistsComeFromTheDefaultDirectory() {
    Artist artist = http.toBlocking().retrieve("/artists/" + MBID, Artist.class);

    assertThat(artist.name()).isEqualTo("Radiohead");
    assertThat(artist.recentSetlists())
        .containsExactly(new Artist.SetlistSummary("21-11-2025", "The O2 Arena", "London"));
  }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :examples:java:test --tests '*ClasspathStubsTest' --tests '*DefaultDirectoryTest'`
Expected: FAIL. `ClasspathStubsTest` fails at startup with
"filesUnderClasspath 'stubs-by-server/musicbrainz' ... was not found on the
classpath"; `DefaultDirectoryTest` fails the same way.

- [ ] **Step 3: Add the per-server classpath stubs**

`stubs-by-server/musicbrainz/mappings/artist.json`:

```json
{
  "request": {
    "method": "GET",
    "urlPath": "/ws/2/artist/a74b1b7f-71a5-4011-9441-d0b5e4122711",
    "queryParameters": { "fmt": { "equalTo": "json" } },
    "headers": { "User-Agent": { "equalTo": "wiremock-micronaut-example/1.0" } }
  },
  "response": {
    "status": 200,
    "headers": { "Content-Type": "application/json" },
    "bodyFileName": "radiohead.json"
  }
}
```

`stubs-by-server/musicbrainz/__files/radiohead.json`:

```json
{
  "id": "a74b1b7f-71a5-4011-9441-d0b5e4122711",
  "name": "Radiohead",
  "sort-name": "Radiohead",
  "type": "Group",
  "country": "GB",
  "disambiguation": "",
  "life-span": { "begin": "1991", "ended": false }
}
```

`stubs-by-server/setlist-fm/mappings/setlists.json`:

```json
{
  "request": {
    "method": "GET",
    "urlPath": "/rest/1.0/artist/a74b1b7f-71a5-4011-9441-d0b5e4122711/setlists",
    "headers": { "x-api-key": { "equalTo": "dummy-setlist-fm-api-key" } }
  },
  "response": {
    "status": 200,
    "headers": { "Content-Type": "application/json" },
    "bodyFileName": "setlists.json"
  }
}
```

`stubs-by-server/setlist-fm/__files/setlists.json`:

```json
{
  "type": "setlists",
  "itemsPerPage": 20,
  "page": 1,
  "total": 2,
  "setlist": [
    {
      "eventDate": "14-11-2025",
      "artist": { "mbid": "a74b1b7f-71a5-4011-9441-d0b5e4122711", "name": "Radiohead" },
      "venue": {
        "name": "Unipol Arena",
        "city": { "name": "Bologna", "country": { "code": "IT", "name": "Italy" } }
      },
      "tour": { "name": "2025 European Tour" }
    },
    {
      "eventDate": "04-11-2025",
      "artist": { "mbid": "a74b1b7f-71a5-4011-9441-d0b5e4122711", "name": "Radiohead" },
      "venue": {
        "name": "Movistar Arena",
        "city": { "name": "Madrid", "country": { "code": "ES", "name": "Spain" } }
      },
      "tour": { "name": "2025 European Tour" }
    }
  ]
}
```

- [ ] **Step 4: Add the default-directory stubs**

These deliberately differ from the classpath ones (a single London show) so
`DefaultDirectoryTest` proves which directory served them.

`wiremock/mappings/setlists.json`: identical to
`stubs-by-server/setlist-fm/mappings/setlists.json` above.

`wiremock/__files/setlists.json`:

```json
{
  "type": "setlists",
  "itemsPerPage": 20,
  "page": 1,
  "total": 1,
  "setlist": [
    {
      "eventDate": "21-11-2025",
      "artist": { "mbid": "a74b1b7f-71a5-4011-9441-d0b5e4122711", "name": "Radiohead" },
      "venue": {
        "name": "The O2 Arena",
        "city": { "name": "London", "country": { "code": "GB", "name": "United Kingdom" } }
      },
      "tour": { "name": "2025 European Tour" }
    }
  ]
}
```

- [ ] **Step 5: Run all the example's tests to verify they pass**

Run: `./gradlew spotlessApply :examples:java:test`
Expected: PASS, 11 tests. `ProgrammaticStubsTest` still passes now that the
default directory exists, because its own stubs are added after the file
stubs.

- [ ] **Step 6: Commit**

```bash
git add examples/java/src/test
git commit --no-gpg-sign -m "test(examples): Show classpath, default-directory and __files stubs in Java

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Kotlin example service with programmatic-stub tests

**Files:**
- Modify: `examples/kotlin/build.gradle.kts`
- Modify: `examples/kotlin/src/main/resources/application.properties`
- Delete: `examples/kotlin/src/main/kotlin/example/UsersClient.kt`
- Delete: `examples/kotlin/src/test/kotlin/example/UsersClientTest.kt`
- Delete: `examples/kotlin/src/test/kotlin/example/SetlistFmClientTest.kt`
- Replace: `examples/kotlin/src/main/kotlin/example/SetlistFmClient.kt`
- Create in `examples/kotlin/src/main/kotlin/example/`: `Application.kt`,
  `ArtistController.kt`, `ArtistService.kt`, `MusicBrainzClient.kt`,
  `Artist.kt`, `MusicBrainzArtist.kt`, `SetlistPage.kt`
- Create: `examples/kotlin/src/test/kotlin/example/ProgrammaticStubsTest.kt`

**Interfaces:**
- Consumes: `libs.plugins.micronaut.application` (Task 2).
- Produces (used by Task 5):
  - `example.Artist(name: String, country: String?, type: String?, recentSetlists: List<SetlistSummary>)`
  - `example.SetlistSummary(eventDate: String, venue: String, city: String)`
  - `GET /artists/{mbid}` with the same behaviour as the Java example.

- [ ] **Step 1: Switch the build to an application**

Replace `examples/kotlin/build.gradle.kts` with:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.ksp)
    alias(libs.plugins.micronaut.application)
}

repositories { mavenCentral() }

kotlin { jvmToolchain(25) }

application {
    mainClass = "example.ApplicationKt"
}

micronaut {
    version(providers.gradleProperty("micronautVersion").getOrElse(libs.versions.micronaut.get()))
    testRuntime("junit5")
}

dependencies {
    ksp("io.micronaut:micronaut-inject-kotlin")
    ksp("io.micronaut.serde:micronaut-serde-processor")
    kspTest("io.micronaut:micronaut-inject-kotlin")
    implementation("io.micronaut:micronaut-http-server-netty")
    implementation("io.micronaut:micronaut-http-client-jdk")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    runtimeOnly("ch.qos.logback:logback-classic")
    testImplementation(project(":"))
    testImplementation("org.assertj:assertj-core")
}
```

Replace `examples/kotlin/src/main/resources/application.properties` with:

```properties
musicbrainz.url=https://musicbrainz.org
micronaut.http.services.setlist-fm.url=https://api.setlist.fm
setlist-fm.api-key=dummy-setlist-fm-api-key
```

Delete the old example code:

```bash
git rm -q examples/kotlin/src/main/kotlin/example/UsersClient.kt \
  examples/kotlin/src/test/kotlin/example/UsersClientTest.kt \
  examples/kotlin/src/test/kotlin/example/SetlistFmClientTest.kt
```

- [ ] **Step 2: Write the failing endpoint tests**

`examples/kotlin/src/test/kotlin/example/ProgrammaticStubsTest.kt`:

```kotlin
package example

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.notFound
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.serverError
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.http.Fault
import com.leeturner.wiremock.micronaut.ConfigureWireMock
import com.leeturner.wiremock.micronaut.EnableWireMock
import com.leeturner.wiremock.micronaut.InjectWireMock
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val MBID = "a74b1b7f-71a5-4011-9441-d0b5e4122711"
private const val ARTIST_PATH = "/ws/2/artist/$MBID"
private const val SETLISTS_PATH = "/rest/1.0/artist/$MBID/setlists"

/**
 * Every stub is declared in the test. Neither server configures files, so both also load the
 * default directory (src/test/resources/wiremock); stubs added here win because WireMock prefers
 * the most recently added stub.
 */
@MicronautTest
@EnableWireMock(
    ConfigureWireMock(name = "musicbrainz", baseUrlProperties = ["musicbrainz.url"]),
    ConfigureWireMock(
        name = "setlist-fm",
        baseUrlProperties = ["micronaut.http.services.setlist-fm.url"],
    ),
)
class ProgrammaticStubsTest {

    @Inject
    @field:Client("/")
    lateinit var http: HttpClient

    @InjectWireMock("musicbrainz")
    lateinit var musicBrainz: WireMockServer

    @InjectWireMock("setlist-fm")
    lateinit var setlistFm: WireMockServer

    @BeforeEach
    fun `stub the happy path`() {
        musicBrainz.stubFor(
            get(urlPathEqualTo(ARTIST_PATH))
                .withQueryParam("fmt", equalTo("json"))
                .willReturn(
                    okJson(
                        """
                        {"id": "$MBID", "name": "Radiohead", "sort-name": "Radiohead",
                         "type": "Group", "country": "GB"}
                        """,
                    ),
                ),
        )
        setlistFm.stubFor(
            get(urlPathEqualTo(SETLISTS_PATH))
                .willReturn(
                    okJson(
                        """
                        {"type": "setlists", "itemsPerPage": 20, "page": 1, "total": 1,
                         "setlist": [{"eventDate": "04-11-2025",
                                      "venue": {"name": "Movistar Arena",
                                                "city": {"name": "Madrid"}}}]}
                        """,
                    ),
                ),
        )
    }

    @Test
    fun `combines both upstreams`() {
        assertThat(artist()).isEqualTo(
            Artist(
                name = "Radiohead",
                country = "GB",
                type = "Group",
                recentSetlists = listOf(SetlistSummary("04-11-2025", "Movistar Arena", "Madrid")),
            ),
        )
    }

    // The server is injected as a parameter here to show that parameter injection works too.
    @Test
    fun `sends the api key and user agent`(@InjectWireMock("setlist-fm") setlistFm: WireMockServer) {
        artist()

        setlistFm.verify(
            getRequestedFor(urlPathEqualTo(SETLISTS_PATH))
                .withHeader("x-api-key", equalTo("dummy-setlist-fm-api-key")),
        )
        musicBrainz.verify(
            getRequestedFor(urlPathEqualTo(ARTIST_PATH))
                .withHeader("User-Agent", equalTo("wiremock-micronaut-example/1.0")),
        )
    }

    @Test
    fun `unknown artist is not found`() {
        musicBrainz.stubFor(get(urlPathEqualTo(ARTIST_PATH)).willReturn(notFound()))

        assertStatus(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `upstream error becomes bad gateway`() {
        setlistFm.stubFor(get(urlPathEqualTo(SETLISTS_PATH)).willReturn(serverError()))

        assertStatus(HttpStatus.BAD_GATEWAY)
    }

    @Test
    fun `connection fault becomes bad gateway`() {
        setlistFm.stubFor(
            get(urlPathEqualTo(SETLISTS_PATH))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)),
        )

        assertStatus(HttpStatus.BAD_GATEWAY)
    }

    @Test
    fun `missing country is null`() {
        musicBrainz.stubFor(
            get(urlPathEqualTo(ARTIST_PATH)).willReturn(okJson("""{"name": "Radiohead", "type": "Group"}""")),
        )

        assertThat(artist().country).isNull()
    }

    @Test
    fun `page without setlists is empty`() {
        setlistFm.stubFor(
            get(urlPathEqualTo(SETLISTS_PATH)).willReturn(okJson("""{"type": "setlists", "total": 0}""")),
        )

        assertThat(artist().recentSetlists).isEmpty()
    }

    private fun artist(): Artist = http.toBlocking().retrieve("/artists/$MBID", Artist::class.java)

    private fun assertStatus(expected: HttpStatus) {
        assertThatThrownBy { http.toBlocking().retrieve("/artists/$MBID") }
            .isInstanceOfSatisfying(HttpClientResponseException::class.java) {
                assertThat(it.status).isEqualTo(expected)
            }
    }
}
```

The Review Focus tests use readable names here (`missing country is null`
and so on); they are the Kotlin forms of `missingCountryIsNull`,
`pageWithoutSetlistsIsEmpty` and `connectionFaultBecomesBadGateway`.

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew :examples:kotlin:test`
Expected: FAIL to compile: unresolved reference `Artist`.

- [ ] **Step 4: Write the DTOs**

`Artist.kt`:

```kotlin
package example

import io.micronaut.serde.annotation.Serdeable

/** Our response. */
@Serdeable
data class Artist(
    val name: String,
    val country: String?,
    val type: String?,
    val recentSetlists: List<SetlistSummary>,
)

@Serdeable
data class SetlistSummary(val eventDate: String, val venue: String, val city: String)
```

`MusicBrainzArtist.kt`:

```kotlin
package example

import io.micronaut.serde.annotation.Serdeable

/** The fields we use from MusicBrainz's artist lookup. */
@Serdeable
data class MusicBrainzArtist(val name: String, val country: String?, val type: String?)
```

`SetlistPage.kt`:

```kotlin
package example

import io.micronaut.serde.annotation.Serdeable

/** The fields we use from a setlist.fm page of setlists. */
@Serdeable
data class SetlistPage(val setlist: List<Setlist>?)

@Serdeable
data class Setlist(val eventDate: String, val venue: Venue)

@Serdeable
data class Venue(val name: String, val city: City)

@Serdeable
data class City(val name: String)
```

- [ ] **Step 5: Write the clients**

`MusicBrainzClient.kt`:

```kotlin
package example

import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.client.annotation.Client

/** MusicBrainz asks every client to identify itself with a User-Agent. */
@Client("\${musicbrainz.url}")
@Header(name = "User-Agent", value = "wiremock-micronaut-example/1.0")
interface MusicBrainzClient {

    /** Null when MusicBrainz returns 404. */
    @Get("/ws/2/artist/{mbid}?fmt=json")
    fun artist(mbid: String): MusicBrainzArtist?
}
```

`SetlistFmClient.kt` (replace the file):

```kotlin
package example

import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.client.annotation.Client

/** setlist.fm authenticates with an x-api-key header. */
@Client("setlist-fm")
@Header(name = "x-api-key", value = "\${setlist-fm.api-key}")
interface SetlistFmClient {

    /** Null when setlist.fm returns 404. */
    @Get("/rest/1.0/artist/{mbid}/setlists")
    fun setlists(mbid: String): SetlistPage?
}
```

- [ ] **Step 6: Write the service, controller and main function**

`ArtistService.kt`:

```kotlin
package example

import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.exceptions.HttpStatusException
import jakarta.inject.Singleton

@Singleton
class ArtistService(
    private val musicBrainz: MusicBrainzClient,
    private val setlistFm: SetlistFmClient,
) {

    fun artist(mbid: String): Artist {
        val artist = call { musicBrainz.artist(mbid) }
        val page = call { setlistFm.setlists(mbid) }
        return Artist(
            name = artist.name,
            country = artist.country,
            type = artist.type,
            recentSetlists = page.setlist.orEmpty().map { SetlistSummary(it.eventDate, it.venue.name, it.venue.city.name) },
        )
    }

    /** Upstream 404 becomes our 404; any other upstream failure becomes 502. */
    private fun <T : Any> call(upstream: () -> T?): T =
        try {
            upstream() ?: throw notFound()
        } catch (e: HttpClientResponseException) {
            throw if (e.status == HttpStatus.NOT_FOUND) notFound() else badGateway()
        } catch (e: HttpClientException) {
            throw badGateway()
        }

    private fun notFound() = HttpStatusException(HttpStatus.NOT_FOUND, "Artist not found")

    private fun badGateway() = HttpStatusException(HttpStatus.BAD_GATEWAY, "Upstream call failed")
}
```

`ArtistController.kt`:

```kotlin
package example

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Controller("/artists")
class ArtistController(private val artists: ArtistService) {

    @Get("/{mbid}")
    fun artist(mbid: String): Artist = artists.artist(mbid)
}
```

`Application.kt`:

```kotlin
package example

import io.micronaut.runtime.Micronaut.run

fun main(args: Array<String>) {
    run(*args)
}
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :examples:kotlin:test`
Expected: PASS, 7 tests.

- [ ] **Step 8: Commit**

```bash
git add examples/kotlin
git commit --no-gpg-sign -m "feat(examples): Turn the Kotlin example into an artist service

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Kotlin mapping-file tests

**Files:**
- Create: `examples/kotlin/src/test/resources/` (copied from the Java example)
- Create: `examples/kotlin/src/test/kotlin/example/ClasspathStubsTest.kt`
- Create: `examples/kotlin/src/test/kotlin/example/DefaultDirectoryTest.kt`

**Interfaces:**
- Consumes: `example.Artist`, `example.SetlistSummary` (Task 4); the six stub
  files from Task 3.

- [ ] **Step 1: Write the failing tests**

`ClasspathStubsTest.kt`:

```kotlin
package example

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.serverError
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.leeturner.wiremock.micronaut.ConfigureWireMock
import com.leeturner.wiremock.micronaut.EnableWireMock
import com.leeturner.wiremock.micronaut.InjectWireMock
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Each server loads its own mappings from the classpath (src/test/resources/stubs-by-server/<name>).
 * Response bodies live in each folder's __files directory and are referenced with bodyFileName.
 */
@MicronautTest
@EnableWireMock(
    ConfigureWireMock(
        name = "musicbrainz",
        baseUrlProperties = ["musicbrainz.url"],
        filesUnderClasspath = "stubs-by-server/musicbrainz",
    ),
    ConfigureWireMock(
        name = "setlist-fm",
        baseUrlProperties = ["micronaut.http.services.setlist-fm.url"],
        filesUnderClasspath = "stubs-by-server/setlist-fm",
    ),
)
class ClasspathStubsTest {

    private val mbid = "a74b1b7f-71a5-4011-9441-d0b5e4122711"

    @Inject
    @field:Client("/")
    lateinit var http: HttpClient

    @InjectWireMock("setlist-fm")
    lateinit var setlistFm: WireMockServer

    @Test
    fun `serves the artist from mapping files`() {
        val artist = http.toBlocking().retrieve("/artists/$mbid", Artist::class.java)

        assertThat(artist).isEqualTo(
            Artist(
                name = "Radiohead",
                country = "GB",
                type = "Group",
                recentSetlists = listOf(
                    SetlistSummary("14-11-2025", "Unipol Arena", "Bologna"),
                    SetlistSummary("04-11-2025", "Movistar Arena", "Madrid"),
                ),
            ),
        )
    }

    @Test
    fun `a stub in the test overrides a file stub`() {
        setlistFm.stubFor(
            get(urlPathEqualTo("/rest/1.0/artist/$mbid/setlists")).atPriority(1).willReturn(serverError()),
        )

        assertThatThrownBy { http.toBlocking().retrieve("/artists/$mbid") }
            .isInstanceOfSatisfying(HttpClientResponseException::class.java) {
                assertThat(it.status).isEqualTo(HttpStatus.BAD_GATEWAY)
            }
    }
}
```

`DefaultDirectoryTest.kt`:

```kotlin
package example

import com.leeturner.wiremock.micronaut.ConfigureWireMock
import com.leeturner.wiremock.micronaut.EnableWireMock
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * setlist-fm configures no files, so it loads the first default directory that exists:
 * src/test/resources/wiremock. Every server without files configuration shares that directory,
 * so musicbrainz points at its own classpath folder instead.
 */
@MicronautTest
@EnableWireMock(
    ConfigureWireMock(
        name = "musicbrainz",
        baseUrlProperties = ["musicbrainz.url"],
        filesUnderClasspath = "stubs-by-server/musicbrainz",
    ),
    ConfigureWireMock(
        name = "setlist-fm",
        baseUrlProperties = ["micronaut.http.services.setlist-fm.url"],
    ),
)
class DefaultDirectoryTest {

    @Inject
    @field:Client("/")
    lateinit var http: HttpClient

    @Test
    fun `setlists come from the default directory`() {
        val artist = http.toBlocking().retrieve("/artists/a74b1b7f-71a5-4011-9441-d0b5e4122711", Artist::class.java)

        assertThat(artist.name).isEqualTo("Radiohead")
        assertThat(artist.recentSetlists).containsExactly(SetlistSummary("21-11-2025", "The O2 Arena", "London"))
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :examples:kotlin:test --tests '*ClasspathStubsTest' --tests '*DefaultDirectoryTest'`
Expected: FAIL with "filesUnderClasspath 'stubs-by-server/musicbrainz' ...
was not found on the classpath".

- [ ] **Step 3: Copy the stub files from the Java example**

```bash
cp -R examples/java/src/test/resources examples/kotlin/src/test/
diff -r examples/java/src/test/resources examples/kotlin/src/test/resources
```

Expected: `diff` prints nothing.

- [ ] **Step 4: Run all tests to verify they pass**

Run: `./gradlew :examples:kotlin:test`
Expected: PASS, 10 tests.

- [ ] **Step 5: Commit**

```bash
git add examples/kotlin/src/test
git commit --no-gpg-sign -m "test(examples): Show classpath, default-directory and __files stubs in Kotlin

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: README examples section and full build

**Files:**
- Modify: `README.md` (new section before `## Migrating from ...`)

**Interfaces:** none.

- [ ] **Step 1: Add the Examples section**

Insert before `## Migrating from \`io.github.nahuel92:wiremock-micronaut\``:

```markdown
## Examples

[`examples/java`](examples/java) and [`examples/kotlin`](examples/kotlin) are
the same small Micronaut service: `GET /artists/{mbid}` combines an artist
from MusicBrainz (a `${musicbrainz.url}` client) with recent setlists from
setlist.fm (a `setlist-fm` service id client). Each test class shows one way
to stub them:

| Test | Technique |
|---|---|
| `ProgrammaticStubsTest` | Stubs in the test with `stubFor`, `verify` of request headers, upstream 404/500/connection faults. |
| `ClasspathStubsTest` | One classpath folder per server (`filesUnderClasspath`), bodies from `__files` via `bodyFileName`, and a test stub overriding a file stub. |
| `DefaultDirectoryTest` | No files configuration: stubs load from `src/test/resources/wiremock`. That directory is shared by every such server. |
```

- [ ] **Step 2: Run the full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL (root tests, both examples, Spotless check).

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit --no-gpg-sign -m "docs: Describe the examples in the README

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
