# Better examples — design

## Goal

Replace the bare `@Client` examples with small but complete Micronaut services
that call two external APIs, and whose tests show every way of supplying
WireMock stubs. The examples are documentation: readable in one sitting and
safe to copy.

Success: a reader of either example sees a realistic controller → service →
client flow and one test class per stubbing technique; CI (`./gradlew build`,
which already runs both example modules) proves every file-loading path works
end to end.

## Scope

- `examples/java` and `examples/kotlin` are rewritten as identical services
  (same endpoints, same tests, same stub files), each idiomatic in its language.
- The extension gains one test for `__files`/`bodyFileName`.
- README gains a short "Examples" section.
- No changes to the extension's main code.

## The service

`GET /artists/{mbid}` returns:

```json
{
  "name": "Radiohead",
  "country": "GB",
  "type": "Group",
  "recentSetlists": [
    { "eventDate": "21-07-2025", "venue": "...", "city": "..." }
  ]
}
```

It is assembled from two upstream calls:

| Upstream    | Client                     | Request                                                                                       |
|-------------|----------------------------|-----------------------------------------------------------------------------------------------|
| MusicBrainz | `@Client("musicbrainz")`   | `GET /ws/2/artist/{mbid}?fmt=json`, header `User-Agent: wiremock-micronaut-example/1.0`      |
| setlist.fm  | `@Client("setlist-fm")`    | `GET /rest/1.0/artist/{mbid}/setlists`, headers `x-api-key: ${setlist-fm.api-key}`, `Accept: application/json` |

Paths, headers and JSON shapes follow the real APIs; DTOs are trimmed to the
fields above (`@Serdeable`, unknown properties ignored).

### Components (package `example`)

- `Application`: main class.
- `ArtistController`: `GET /artists/{mbid}` → `ArtistService`.
- `ArtistService`: calls both clients, maps to `Artist`, translates errors.
- `MusicBrainzClient`, `SetlistFmClient`: declarative clients.
- DTOs: `Artist`, `SetlistSummary` (our response); `MusicBrainzArtist`,
  `SetlistPage`, `Setlist` (upstream, trimmed).

### Error handling

- Upstream 404 from either API → our 404.
- Any other upstream failure (5xx, connection error) → our 502.

Implemented by catching `HttpClientResponseException` in the service and throwing
`HttpStatusException`; no custom exception handlers.

### Configuration (`src/main/resources/application.properties`)

```properties
micronaut.http.services.musicbrainz.url=https://musicbrainz.org
micronaut.http.services.setlist-fm.url=https://api.setlist.fm
setlist-fm.api-key=dummy-setlist-fm-api-key
```

The API key is an obvious dummy; tests verify it is sent.

### Dependencies

Each example module becomes a Micronaut application: add
`io.micronaut.application` to the version catalog (same version as
`micronaut-library`), declare it `apply false` in the root build (as done for
the other shared plugins), and apply it in both examples with
`mainClass = "example.Application"`. Each module gets an `Application` main
class. It adds `micronaut-http-server-netty` and
`micronaut-serde-jackson` (now `implementation`, since DTOs use `@Serdeable`),
and keeps `micronaut-http-client-jdk`. Tests call our endpoint via an injected
`@Client("/") HttpClient`.

## Tests (identical set per language)

All tests use `@MicronautTest` and `@EnableWireMock` with two servers named
`musicbrainz` and `setlist-fm`, each redirecting its service URL with
`baseUrlProperties = "micronaut.http.services.<name>.url"`.

### `ProgrammaticStubsTest`: stubs in code

- Happy path with `stubFor` on both servers.
- `verify` that `x-api-key: dummy-setlist-fm-api-key` and the `User-Agent`
  header are sent.
- MusicBrainz 404 → our 404.
- setlist.fm 500 → our 502.

Neither server configures files, so both also load the default directory
(below). A class comment points this out: stubs added in code win because
WireMock prefers the most recently added stub, but readers should know the
default folder is picked up.

### `ClasspathStubsTest`: per-server classpath folders

- Each server: `filesUnderClasspath = "stubs-by-server/<name>"`.
- Happy path served entirely from mapping files, with bodies from `__files`
  via `bodyFileName`.
- One test overrides a file stub with a code stub (`atPriority(1)`) to show
  mixing the two; the reset before each test removes it again.

### `DefaultDirectoryTest`: default directory

- `setlist-fm` configures no files and loads `src/test/resources/wiremock`
  (resolved against the module directory, which is Gradle's test working
  directory).
- `musicbrainz` uses `filesUnderClasspath = "stubs-by-server/musicbrainz"`.
- A class comment explains that the default directory is shared by every
  server without files config (same as wiremock-spring-boot), which is why only
  one server relies on it.

## Stub file layout (per example module)

```
src/test/resources/
  wiremock/                         # default directory
    mappings/setlists.json          # bodyFileName: setlists.json
    __files/setlists.json
  stubs-by-server/
    musicbrainz/
      mappings/artist.json          # bodyFileName: radiohead.json
      __files/radiohead.json
    setlist-fm/
      mappings/setlists.json        # bodyFileName: setlists.json
      __files/setlists.json
```

Per-server folders live under `stubs-by-server/`, not `wiremock/<name>`, so
they are not confused with the default directory. JSON bodies are trimmed
copies of real responses for one artist MBID
(`a74b1b7f-71a5-4011-9441-d0b5e4122711`, Radiohead), and mappings match on the
`x-api-key` / `User-Agent` headers so a missing header fails the test.

## Extension test addition

- Add `src/test/resources/classpath-stubs/__files/body.json` and
  `classpath-stubs/mappings/from-body-file.json` (`bodyFileName: body.json`).
- Add one assertion to `FileStubsSurviveResetTest` that `/from-body-file`
  returns the file's content.

The default-directory path is not tested in the root module: adding
`src/test/resources/wiremock` there would load it into every existing test.
The examples' `DefaultDirectoryTest` covers it end to end in CI.

## README

Add an "Examples" section: one line per test class naming the technique it
demonstrates, linking the Java and Kotlin versions.

## Out of scope

- `filesUnderDirectory` in the examples (covered by extension tests).
- HTTPS, extensions, customizers, `registerBean` in the examples.
- Changing the default directory search to be per-server.
