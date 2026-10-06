# wiremock-micronaut — Design

- **Date:** 2026-10-05
- **Status:** Approved design, pending spec review
- **Coordinates:** `com.leeturner:wiremock-micronaut`
- **Package:** `com.leeturner.wiremock.micronaut`

## 1. Intent

### What we want

A JUnit integration that starts WireMock HTTP servers for Micronaut tests,
binds their URLs/ports into Micronaut configuration before any bean is
created, and injects the servers into tests.

- **Audience:** public library for Java and Kotlin Micronaut users, released
  to Maven Central, of a quality that could later be offered as an official
  WireMock integration.
- **Immediate driver:** a project being upgraded to Micronaut 5.2.1 needs
  WireMock in its tests; the existing library
  (`io.github.nahuel92:wiremock-micronaut`) does not work with Micronaut 5.
- **Success:** a Micronaut 5 app adds one test dependency, writes
  `@MicronautTest` + `@EnableWireMock`, and its HTTP clients talk to WireMock
  — with no classpath conflicts against the Micronaut 5 platform BOM, and no
  breakage on routine Micronaut upgrades.

### Why a new library rather than updating the existing one

Findings from attempting the upgrade of the existing library:

1. **It is coupled to micronaut-test internals.** It subclasses
   `MicronautJunit5Extension` and constructs `MicronautTestValue` by hand,
   mirroring every `@MicronautTest` attribute. micronaut-test 5 added a
   `deduceEnvironment` constructor argument, which broke compilation.
2. **Its gRPC support cannot be made sound.**
   - `wiremock-grpc-extension-jetty` is built against *unshaded*
     `wiremock-jetty` and calls
     `Jetty12HttpServer(..., org.eclipse.jetty...ThreadPool, ...)`. Inside
     `wiremock-standalone`, Jetty is relocated to `wiremock.org.eclipse.jetty`,
     so that constructor does not exist (`NoSuchMethodError`). It only ever
     worked because classpath order happened to load the unshaded classes
     first (833 duplicated classes).
   - `wiremock-grpc-extension-standalone` relocates `com.google.protobuf`,
     which is part of its public API, so users' generated protobuf messages
     cannot be passed to `WireMockGrpc.message(...)`.
3. **Unshaded WireMock is unsafe under the Micronaut 5 BOM.** With
   `org.wiremock:wiremock` (unshaded), the BOM overrides 54 of WireMock's
   ~116 test-scope dependencies. `json-schema-validator` jumps 2.0.1 → 3.0.7
   (Jackson 3 based), and `matchingJsonSchema(...)` fails at runtime with
   `NoSuchMethodError`.

The new library is HTTP-only, uses `wiremock-standalone`, and integrates
through public micronaut-test APIs only.

### Scope

**In:**

- JUnit Jupiter (the version Micronaut 5 ships, currently 6.x).
- HTTP and HTTPS WireMock servers.
- Attribute parity with `wiremock-spring-boot`'s `@ConfigureWireMock`, except
  where Spring-specific.

**Out (for now):**

- gRPC.
- Kotest and Spock (the design keeps Kotest cheap to add later).
- An unshaded-WireMock variant.
- A repeatable `@EnableWireMock`.
- Micronaut 4.

## 2. Platform and naming

| Item | Value |
|---|---|
| Language | Java 25 (`--release 25`) |
| Micronaut | 5.x (Micronaut 5 classes are Java 25 bytecode) |
| WireMock | `org.wiremock:wiremock-standalone` 4.0.0-beta.39 (Java 17 bytecode) |
| Test framework | JUnit Jupiter via `micronaut-test-junit5` 5.x |
| Build | Gradle, Kotlin DSL build scripts, version catalog |
| Repo / directory | `wiremock-micronaut` (`~/dev/personal/wiremock-micronaut`) |
| groupId / artifactId | `com.leeturner` / `wiremock-micronaut` |
| Public package | `com.leeturner.wiremock.micronaut` |
| Internal package | `com.leeturner.wiremock.micronaut.internal` |

Naming mirrors `wiremock-spring-boot` (`org.wiremock.integrations:wiremock-spring-boot`),
so docs and muscle memory transfer between the two.

Java rather than Kotlin was chosen for these reasons:

- No `kotlin-stdlib` is forced onto consumers.
- Annotations are native Java.
- It is consistent with WireMock, Micronaut and wiremock-spring-boot.

Null-safety comes from JSpecify `@NullMarked` on packages.

## 3. Public API

All public types live in `com.leeturner.wiremock.micronaut`.

```java
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(WireMockMicronautExtension.class)
public @interface EnableWireMock {
  ConfigureWireMock[] value() default {};
}

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ConfigureWireMocks.class)
@ExtendWith(WireMockMicronautExtension.class)
public @interface ConfigureWireMock {
  List<String> DEFAULT_FILES_UNDER_DIRECTORY = List.of(
      "wiremock", "stubs", "mappings",
      "src/test/resources/wiremock", "src/test/resources/stubs", "src/test/resources/mappings",
      "src/integtest/resources/wiremock", "src/integtest/resources/stubs",
      "src/integtest/resources/mappings");

  String name() default "wiremock";
  int port() default 0;
  int httpsPort() default -1;
  String[] portProperties() default {"wiremock.server.port"};
  String[] httpsPortProperties() default {"wiremock.server.httpsPort"};
  String[] baseUrlProperties() default {"wiremock.server.baseUrl"};
  String[] httpsBaseUrlProperties() default {"wiremock.server.httpsBaseUrl"};
  String filesUnderClasspath() default "";
  String[] filesUnderDirectory() default {};
  Class<? extends Extension>[] extensions() default {};
  Class<? extends ExtensionFactory>[] extensionFactories() default {};
  Class<? extends WireMockConfigurationCustomizer>[] configurationCustomizers() default {};
  boolean resetWireMockServer() default true;
  boolean registerBean() default false;
  boolean globalTemplating() default false;
  String keystorePath() default "";
  String keystorePassword() default "";
  String keystoreType() default "";
  String keyManagerPassword() default "";
  String trustStorePath() default "";
  String trustStorePassword() default "";
  String trustStoreType() default "";
  boolean needClientAuth() default false;
}

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(WireMockMicronautExtension.class)
public @interface ConfigureWireMocks {
  ConfigureWireMock[] value();
}

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface InjectWireMock {
  String value() default "wiremock";
}

@FunctionalInterface
public interface WireMockConfigurationCustomizer {
  void customize(WireMockConfiguration configuration, ConfigureWireMock options);
}
```

`WireMockMicronautExtension` is referenced by the annotations but lives in
the internal package.

### Usage

```java
@MicronautTest
@EnableWireMock({@ConfigureWireMock(name = "users", baseUrlProperties = "users.url")})
class UserClientTest {
  @InjectWireMock("users") WireMockServer users;

  @Test
  void works(@InjectWireMock("users") WireMockServer same) { /* ... */ }
}
```

### Behaviour shared with wiremock-spring-boot

- **Default server.** A bare `@EnableWireMock` configures one server named
  `"wiremock"` with default attributes.
- **Standalone annotations.** `@ConfigureWireMock` may be placed directly on
  the test class, and may be repeated. Servers declared there are added to
  those from `@EnableWireMock`.
- **Default stub directory.** If both `filesUnderClasspath` and
  `filesUnderDirectory` are empty, stubs are loaded from the first existing
  directory in `DEFAULT_FILES_UNDER_DIRECTORY`. If none exists, this is
  logged at INFO.
- **Static DSL.** When exactly one server is configured for the test class,
  the static `WireMock` client is pointed at it before each test (HTTPS port
  if HTTPS is enabled, otherwise HTTP; host `localhost`) and reset with
  `WireMock.configureFor(-1)` after each test. With several servers, the
  static client is not touched and a DEBUG line says why.
- **Injection type.** `@InjectWireMock` supports `WireMockServer` only.

### Micronaut HTTP clients: both declaration styles

Apps declare Micronaut clients in two ways, and both must work:

1. **URL placeholder:** `@Client("${users.url}")` or
   `@Client("${micronaut.http.services.setlist-fm.url}")`. The client
   resolves the property when it is created.
2. **Service id:** `@Client("setlist-fm")`. Micronaut looks the URL up in
   the service configuration `micronaut.http.services.setlist-fm.url`
   (normally set in `application.yml`/`application.properties` to the real
   API).

The old library only handled style 1. Style 2 works here with no special
API: `baseUrlProperties = "micronaut.http.services.setlist-fm.url"`.

- The WireMock properties are in the test property source while the context
  is built, so Micronaut's `@EachProperty("micronaut.http.services")`
  service configuration is created with the WireMock URL.
- The test property source has higher precedence than the application's
  configuration files, so it overrides the real URL.

Both styles are covered by tests (§6) and documented in the README. If an
app configures `...urls` (a list) instead of `...url`, the test must bind
the same key the app uses.

### Deliberate differences from wiremock-spring-boot

- `registerBean` replaces `registerSpringBean`. It registers the server as a
  Micronaut singleton qualified `@Named(<name>)` (see §4.4).
- `staticPortDirtySpringContext` and `usePortFromPredefinedPropertyIfFound`
  are omitted. They exist only because of Spring's test-context caching.
  Micronaut test contexts are per test class.
- `@EnableWireMock` is not repeatable.

## 4. Architecture

### 4.1 Integration points (public APIs only)

- **`io.micronaut.test.support.TestPropertyProviderFactory`.** A
  `ServiceLoader` SPI in micronaut-test-core. It is invoked by
  `AbstractMicronautExtension` from the context builder's
  `PropertySourcesLocator`, i.e. while the application context is built and
  before beans are created. It receives the test class and returns properties
  that are added to the test property source. It is re-invoked when
  `rebuildContext = true` rebuilds the context. This is the same hook
  micronaut-test-resources uses.
- **JUnit Jupiter extension API** for injection, per-test hooks and cleanup.
- **Micronaut `@EachProperty` / `@Factory`** for `registerBean`.

### 4.2 Components

All components are internal, in `com.leeturner.wiremock.micronaut.internal`.

| Component | Responsibility |
|---|---|
| `ConfigurationResolver` | Collects the effective `ConfigureWireMock` list for a test class: `@EnableWireMock` (including via `@Inherited`, with the default server when empty) plus standalone `@ConfigureWireMock`. Validates unique names and unique property names. Resolves the registry key (outermost enclosing class for `@Nested`). |
| `WireMockServerCreator` | Turns one `ConfigureWireMock` into a started `WireMockServer`: ports, HTTPS/keystore/truststore, stub files, extensions, extension factories, global templating, customizers (applied last), and the SLF4J notifier. |
| `WireMockServers` | Static, thread-safe registry: `ConcurrentHashMap<Class<?>, Map<String, WireMockServer>>` keyed by root test class. Entry points: `getOrStart(Class<?> testClass)` and `stop(Class<?> rootTestClass)`. |
| `WireMockTestPropertyProviderFactory` | `TestPropertyProviderFactory`, registered in `META-INF/services`. If the test class has no WireMock annotations, it returns an empty provider. Otherwise it calls `getOrStart` and returns the port, HTTPS port, base URL and HTTPS base URL properties per server, plus the `registerBean` marker properties. |
| `WireMockMicronautExtension` | JUnit extension. Implements `TestInstancePostProcessor`, `BeforeEachCallback`, `AfterEachCallback`, `AfterAllCallback` and `ParameterResolver`. |
| `WireMockServerBeanFactory` | Micronaut `@Factory` producing `@Named` `WireMockServer` beans for `registerBean` servers. |
| `Slf4jNotifier` | WireMock `Notifier` that logs through SLF4J. |

### 4.3 Lifecycle for one root test class

1. **Context build (JUnit `beforeAll` of `MicronautJunit5Extension`).**
   - Micronaut calls our factory.
   - `getOrStart` starts all servers for the class; the first caller wins.
   - The returned properties go into the test property source, so beans see
     `users.url` and similar on creation.
2. **Test instance post-processing and before each test.**
   - Inject `@InjectWireMock` fields on the test instance and on any
     enclosing `@Nested` instances, calling `getOrStart`, which returns the
     already running servers.
   - Reset servers with `resetWireMockServer = true`.
   - Configure the static DSL as in §3.
3. **Parameter resolution.** Supports `@InjectWireMock WireMockServer` method
   parameters.
4. **After each test.** `WireMock.configureFor(-1)`, only for classes this
   library manages.
5. **After all (root class only, not `@Nested`).** `stop(rootClass)` stops
   every server and removes the registry entry.

### 4.4 `registerBean`

For each server with `registerBean = true`, the factory additionally emits
`wiremock.micronaut.servers.<name>.registry-key=<root test class FQN>`.
`WireMockServerBeanFactory` uses `@EachProperty("wiremock.micronaut.servers")`
to create one `@Singleton` `WireMockServer` per entry, looked up from
`WireMockServers`. The `@EachProperty` key provides the `@Named` qualifier.

Because this is ordinary bean creation:

- app beans and the test class can `@Inject @Named("users") WireMockServer`;
- eager (`@Context`) singletons work.

This requires `micronaut-inject-java` as an annotation processor at library
build time only. Consumers gain no dependency.

### 4.5 Edge cases

- **Call order independence.** Both the factory and the extension call
  `getOrStart`, so behaviour does not depend on extension ordering, on
  `PER_CLASS` lifecycle (where the instance exists before `beforeAll`), or on
  which hook runs first.
- **`rebuildContext = true`.** The factory is re-invoked, finds the running
  servers, and returns identical ports and URLs.
- **`@Nested`.**
  - The registry key is the outermost class, so nested tests share the
    parent's servers, as they share its Micronaut context.
  - Declaring `@EnableWireMock` or `@ConfigureWireMock` on a `@Nested` class
    fails (see §5), mirroring micronaut-test's rule for `@MicronautTest` and
    `@Property`.
- **No `@MicronautTest`.** The extension still starts and injects servers;
  only the property binding is missing. A WARN is logged once per class.
- **Parallel execution.** Registry entries are per root class, and ports are
  dynamic by default, so no clashes occur.
- **Competing parameter resolvers.** `MicronautJunit5Extension` is also a
  `ParameterResolver`. It claims any parameter for which
  `applicationContext.containsBean(type, qualifier)` is true, where the
  qualifier is derived from the parameter's annotations.
  - With `registerBean = true`, a `WireMockServer` bean exists. If
    `@InjectWireMock` contributes no qualifier, both extensions claim the
    parameter, and JUnit fails with "competing ParameterResolvers".
  - **Mitigation:** meta-annotate `@InjectWireMock` with
    `@jakarta.inject.Qualifier`. Micronaut then looks for a bean qualified by
    `@InjectWireMock`, finds none (beans are `@Named`), and declines, leaving
    the parameter to us.
  - This must be proven by a test (§6).
  - **Fallback if it does not hold:** our resolver declines parameters whose
    server has `registerBean = true`, and the docs direct users to
    `@Inject @Named("<name>") WireMockServer` in that case.
- **Detecting `@MicronautTest`.** Uses
  `AnnotationSupport.isAnnotated(testClass, MicronautTest.class)` (or the
  enclosing root class for `@Nested`). `MicronautTest` is a `compileOnly`
  type, so the check is guarded by a class-presence check.

## 5. Error handling and logging

Configuration errors throw JUnit's `ExtensionConfigurationException`, with
messages that name the test class, the server and the fix.

| Situation | Behaviour |
|---|---|
| `@InjectWireMock("x")` with no server `x` | Fail, listing configured names. |
| `@InjectWireMock` on a non-`WireMockServer` member | Fail, naming the member and its type. |
| Duplicate server `name` | Fail during resolution, before any server starts. |
| Two servers publishing the same property | Fail, naming both servers and the property. |
| `@EnableWireMock`/`@ConfigureWireMock` declared on a `@Nested` class | Fail, telling the user to move it to the enclosing class. |
| Extension, factory or customizer class lacks a public no-arg constructor | Fail, naming the class and the server. |
| Explicit `filesUnderClasspath` or `filesUnderDirectory` not found | Fail. |
| No default stub directory exists | INFO log only. |
| A server fails to start | Stop servers already started for that class, then rethrow with the server name and port. |
| `@EnableWireMock` without `@MicronautTest` | WARN once per class. |

### Cleanup

- Servers are stopped in `afterAll`, which JUnit runs even if `beforeAll` or
  context startup failed.
- `stop` is idempotent. A failure stopping one server is logged and does not
  prevent stopping the rest.
- There is no JVM shutdown hook.

### Logging (SLF4J)

- **INFO:** one line per server start, with name, port(s), base URL and
  bound properties (e.g. `WireMock 'users' started on http://localhost:53122 -> users.url`),
  plus one line per stop.
- **DEBUG:** chosen stub directory, customizers applied, static-DSL decisions.
- WireMock's internal output goes through `Slf4jNotifier`.

## 6. Testing

### Library tests

These use JUnit Jupiter, a small in-test Micronaut app (one declarative
`@Client` per server) and JUnit Platform `EngineTestKit`, which runs fixture
classes and asserts their outcomes (required for the failure cases).

| Area | Cases |
|---|---|
| Property binding | Visible before bean creation, including an eager `@Context` bean reading `users.url`. |
| Multiple servers | Multiple servers; custom port, base URL and HTTPS property names. |
| Injection | `@InjectWireMock` on fields, parameters and `@Nested` classes. |
| Lifecycle | `PER_CLASS` lifecycle; `rebuildContext = true` with stable ports. |
| `registerBean` | Including injection into an eager singleton; an `@InjectWireMock` method parameter on a `registerBean` server resolves without a competing-resolver error. |
| Static DSL | One server versus several. |
| Reset | Reset on and off. |
| Stub loading | Default directories, `filesUnderClasspath`, `filesUnderDirectory`. |
| Extensions | Extensions, extension factories, customizers. |
| HTTPS and templating | HTTPS with keystore and truststore; `globalTemplating`. |
| Without Micronaut | `@EnableWireMock` without `@MicronautTest`. |
| Service-id clients | `micronaut.http.services.<id>.url` bound by WireMock overrides the value in `application.properties` (library test). |
| Concurrency | Parallel class execution. |
| Failures | Every failure row in §5, via `EngineTestKit`. |

### Consumer smoke tests

The `examples/java` and `examples/kotlin` Gradle subprojects use the Micronaut
Gradle plugin and the Micronaut 5 platform BOM, as a real app would. They
prove Kotlin usability and catch BOM-driven dependency conflicts. They double
as README examples.

Each example has two declarative clients, one per style in §3, each with its
own test: a URL-placeholder client (`@Client("${users.url}")`) and a
service-id client (`@Client("setlist-fm")`). The service-id client's real URL
is configured in the example's `application.properties` and overridden in the
test via `baseUrlProperties`.

## 7. Build

- **Gradle.** Kotlin DSL build scripts, `gradle/libs.versions.toml`, Java 25
  toolchain, `options.release = 25`.
- **Dependencies:**

  | Configuration | Dependencies |
  |---|---|
  | `api` | `org.wiremock:wiremock-standalone` |
  | `compileOnly` | `io.micronaut.test:micronaut-test-junit5`, `io.micronaut:micronaut-inject`, `org.junit.jupiter:junit-jupiter-api`, `org.jspecify:jspecify` (consumers always have the first three in a Micronaut test setup, so the library never pins their versions) |
  | `annotationProcessor` | `io.micronaut:micronaut-inject-java` |

- **Formatting.** Spotless with google-java-format.
- **CI (GitHub Actions).**
  - Build and test on PRs and `main` with JDK 25.
  - A weekly job against the latest Micronaut 5.x to surface upstream breaks
    early.
- **Dependency updates.** Dependabot (Gradle and GitHub Actions ecosystems),
  as used across the WireMock org.

## 8. Release

- Maven Central via the Central Portal, using `com.vanniktech.maven.publish`
  (signing, sources and javadoc jars).
- Releases triggered by a git tag through a release workflow.
- **Versioning:** `0.x` while WireMock 4 is in beta; `1.0.0` aligned with
  WireMock 4 GA.
- **README:**
  - Java and Kotlin quick-starts.
  - Attribute reference mirroring the wiremock-spring-boot docs.
  - Migration notes from `io.github.nahuel92:wiremock-micronaut`:
    `@MicronautWireMockTest` → `@MicronautTest` + `@EnableWireMock`;
    `properties` (base URL) → `baseUrlProperties`;
    `portProperty` → `portProperties`;
    package `io.github.nahuel92.wiremock.micronaut` →
    `com.leeturner.wiremock.micronaut`;
    gRPC is not supported.
