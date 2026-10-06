# wiremock-micronaut Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task by task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `com.leeturner:wiremock-micronaut`, a Java 25 library that
starts WireMock HTTP servers for Micronaut 5 JUnit tests, binds their URLs
into Micronaut configuration before beans are created, and injects them into
tests, using the naming of wiremock-spring-boot.

**Architecture:**

- A service-loaded micronaut-test `TestPropertyProviderFactory` starts the
  servers for a test class (via a static registry) and returns their
  properties while the application context is built.
- A plain JUnit Jupiter extension, registered by `@EnableWireMock` and
  `@ConfigureWireMock`, injects `@InjectWireMock` fields and parameters,
  resets servers per test, points the static `WireMock` DSL at a single
  server, and stops servers after the class.
- Both sides call the same `getOrStart`, so call order never matters.

**Tech stack:**

- Java 25 and Gradle 9.8.0 (Kotlin DSL).
- Micronaut platform 5.2.1, micronaut-test-junit5 5.2.0 (JUnit 6.1.3).
- `org.wiremock:wiremock-standalone` 4.0.0-beta.39.
- AssertJ, JUnit Platform TestKit.

**Spec:** `docs/superpowers/specs/2026-10-05-wiremock-micronaut-design.md`

## Global Constraints

- Repo root: `~/dev/personal/wiremock-micronaut`. All paths below are
  relative to it.
- Coordinates: `com.leeturner:wiremock-micronaut`.
  - Public package: `com.leeturner.wiremock.micronaut`.
  - Internal package: `com.leeturner.wiremock.micronaut.internal`.
- Java 25: toolchain 25, `options.release = 25`.
- Micronaut 5.x only. The platform BOM version comes from the
  `micronautVersion` Gradle property, defaulting to `5.2.1`.
- Dependency configurations:

  | Configuration | Dependencies |
  |---|---|
  | `api` | `org.wiremock:wiremock-standalone:4.0.0-beta.39` (only this) |
  | `compileOnly` | `micronaut-inject`, `micronaut-test-junit5`, `junit-jupiter-api`, `slf4j-api`, `jakarta.inject-api`, `jspecify` |
  | `annotationProcessor` | `micronaut-inject-java` |

  Never add other runtime dependencies.
- HTTP only: no gRPC. JUnit Jupiter only: no Kotest or Spock.
- Annotation names, attribute names and defaults mirror
  wiremock-spring-boot exactly, except `registerBean` (Spring:
  `registerSpringBean`) and the omitted `staticPortDirtySpringContext` and
  `usePortFromPredefinedPropertyIfFound`.
- Default property names:

  | Property | Default name |
  |---|---|
  | Port | `wiremock.server.port` |
  | HTTPS port | `wiremock.server.httpsPort` |
  | Base URL | `wiremock.server.baseUrl` |
  | HTTPS base URL | `wiremock.server.httpsBaseUrl` |

  Base URLs are `http://localhost:<port>` and `https://localhost:<httpsPort>`.
- Port semantics (as Spring):

  | Attribute | `0` | `-1` | `>0` |
  |---|---|---|---|
  | `port` (default `0`) | dynamic | HTTP disabled | static |
  | `httpsPort` (default `-1`) | dynamic | disabled (default) | static |

- Property-name collisions: a property name used by more than one server
  fails, *unless* it is one of the four defaults. Shared defaults are not
  bound, with a DEBUG log. This refines spec §5: without it, every
  multi-server class would fail on the shared defaults.
- Stub directories (as Spring):
  - A directory counts as existing only if it contains `mappings` or
    `__files`.
  - `filesUnderDirectory` takes precedence over `filesUnderClasspath`.
  - With neither set, the first existing entry of
    `ConfigureWireMock.DEFAULT_FILES_UNDER_DIRECTORY` is used. If none
    exists, log at INFO.
- `registerBean` server names must match `[a-z0-9][a-z0-9-]*`, because they
  become a Micronaut property key segment.
- Configuration errors throw
  `org.junit.jupiter.api.extension.ExtensionConfigurationException`, with
  messages naming the test class, the server and the fix. Server start
  failures throw `IllegalStateException` naming the server.
- Code style: google-java-format via Spotless. `@NullMarked` (JSpecify) on
  both packages.
- Test fixture classes that are run through `EngineTestKit` live in package
  `com.leeturner.wiremock.micronaut.fixtures`. The Gradle `test` task
  excludes `**/fixtures/**`.
- The library's own project must never contain a default stub directory
  (`wiremock`, `stubs`, `mappings`, `src/test/resources/{wiremock,stubs,mappings}`,
  `src/integtest/resources/...`). Otherwise every test server would load
  those stubs.
- Always commit unsigned: `git commit --no-gpg-sign ...` (every commit
  step below already includes it).
- Both Micronaut client styles must work, with tests:
  - URL placeholder: `@Client("${users.url}")`.
  - Service id: `@Client("setlist-fm")`, whose URL comes from
    `micronaut.http.services.setlist-fm.url`, overridden via
    `baseUrlProperties`.

## Review Focus

These are the failure modes the spec implies but nothing else pins, with the
tasks that add tests for them. Row 6 was added for the service-id `@Client`
requirement.

| # | Failure mode | Expected behaviour | Pinned in |
|---|---|---|---|
| 1 | Unrelated `@MicronautTest` classes in a project that has this library on the test classpath | Completely unaffected: no servers, no properties, no errors. | Task 6: `UnrelatedMicronautTest`, `WireMockTestPropertyProviderFactoryTest` |
| 2 | The same test class run twice in one JVM (IDE re-run, suites) | After `stop`, `getOrStart` starts fresh, running servers. | Task 4: `stopStopsServersAndAllowsRestart` |
| 3 | Micronaut context startup fails after WireMock servers started | Servers are still stopped and forgotten (no leaked ports). | Task 8: `ContextFailureFixture` |
| 4 | `@InjectWireMock` on a supertype (`Stubbing`, `Object`) works; on an unrelated type (`String`) | The latter fails clearly. | Task 5: `PlainJUnitExtensionTest`; Task 8: `WrongTypeFixture` |
| 5 | File-backed stubs (`filesUnderClasspath`/`filesUnderDirectory`) with the default per-test reset | They survive the reset: only programmatic stubs are cleared. | Task 5: `FileStubsSurviveResetTest` |
| 6 | An app whose `application.properties` sets `micronaut.http.services.<id>.url` to the real API and uses `@Client("<id>")` | WireMock's value wins in tests; requests never reach the real API. | Task 6: `ServiceIdPropertyTest`; Task 9: `SetlistFmClientTest` (Java and Kotlin) |

---

## File structure

```
settings.gradle.kts                      # root project + examples (Task 9)
build.gradle.kts                         # library build
gradle.properties                        # group, version
gradle/libs.versions.toml                # versions/plugins
.gitignore
.github/workflows/build.yml              # Task 10
.github/workflows/weekly.yml             # Task 10
.github/workflows/release.yml            # Task 10
.github/dependabot.yml                   # Task 10
README.md, LICENSE                       # Task 10
src/main/java/com/leeturner/wiremock/micronaut/
  package-info.java                      # @NullMarked
  EnableWireMock.java                    # public API
  ConfigureWireMock.java
  ConfigureWireMocks.java
  InjectWireMock.java
  WireMockConfigurationCustomizer.java
src/main/java/com/leeturner/wiremock/micronaut/internal/
  package-info.java                      # @NullMarked
  ConfigurationResolver.java             # annotations -> validated List<ConfigureWireMock>
  WireMockServerCreator.java             # ConfigureWireMock -> started WireMockServer
  Slf4jNotifier.java                     # WireMock Notifier -> SLF4J
  RunningServer.java                     # record(options, server)
  WireMockServers.java                   # static registry: getOrStart/get/stop
  ServerProperties.java                  # running servers -> Micronaut properties
  WireMockMicronautExtension.java        # JUnit extension
  WireMockTestPropertyProviderFactory.java # micronaut-test SPI
  RegisteredWireMockServer.java          # @EachProperty for registerBean
  WireMockServerBeanFactory.java         # @Factory @EachBean WireMockServer
src/main/resources/META-INF/services/io.micronaut.test.support.TestPropertyProviderFactory
src/test/java/com/leeturner/wiremock/micronaut/...   # tests (per task)
src/test/java/com/leeturner/wiremock/micronaut/fixtures/... # EngineTestKit fixtures
src/test/resources/logback-test.xml
src/test/resources/classpath-stubs/mappings/from-classpath.json
src/test/directory-stubs/mappings/from-directory.json
examples/java/...                        # Task 9
examples/kotlin/...                      # Task 9
```

---

### Task 1: Project skeleton and public API

**Files:**

- Create: `.gitignore`, `settings.gradle.kts`, `gradle.properties`,
  `gradle/libs.versions.toml`, `build.gradle.kts`, Gradle wrapper files.
- Create: `src/main/java/com/leeturner/wiremock/micronaut/{package-info,EnableWireMock,ConfigureWireMock,ConfigureWireMocks,InjectWireMock,WireMockConfigurationCustomizer}.java`.
- Create: `src/main/java/com/leeturner/wiremock/micronaut/internal/{package-info,WireMockMicronautExtension}.java`.
  The extension is a stub here; Task 5 implements it.
- Create: `src/test/resources/logback-test.xml`.
- Test: `src/test/java/com/leeturner/wiremock/micronaut/PublicApiTest.java`.

**Interfaces:**

- Produces:
  - Public annotations `EnableWireMock`, `ConfigureWireMock`,
    `ConfigureWireMocks` and `InjectWireMock`, and the interface
    `WireMockConfigurationCustomizer`, exactly as below.
  - `ConfigureWireMock.DEFAULT_FILES_UNDER_DIRECTORY : List<String>`.
  - Class `com.leeturner.wiremock.micronaut.internal.WireMockMicronautExtension`.

- [ ] **Step 1: Commit the spec if it is not yet committed**

```bash
cd ~/dev/personal/wiremock-micronaut
git log --oneline -3
```

Expected: the spec and plan commits already exist. If they don't, run:

```bash
git add docs && git commit --no-gpg-sign -m "docs: Add design spec and implementation plan" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 2: Write build files**

`.gitignore`:

```
.gradle/
build/
.kotlin/
.idea/
*.iml
out/
```

`settings.gradle.kts`:

```kotlin
rootProject.name = "wiremock-micronaut"
```

`gradle.properties`:

```properties
group=com.leeturner
version=0.1.0-SNAPSHOT
org.gradle.caching=true
```

`gradle/libs.versions.toml`:

```toml
[versions]
micronaut = "5.2.1"
wiremock = "4.0.0-beta.39"
jspecify = "1.0.1"
google-java-format = "1.37.0"

[libraries]
wiremock-standalone = { module = "org.wiremock:wiremock-standalone", version.ref = "wiremock" }
jspecify = { module = "org.jspecify:jspecify", version.ref = "jspecify" }

[plugins]
spotless = { id = "com.diffplug.spotless", version = "8.10.3" }
maven-publish = { id = "com.vanniktech.maven.publish", version = "0.37.0" }
micronaut-library = { id = "io.micronaut.library", version = "5.0.2" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version = "2.4.10" }
kotlin-allopen = { id = "org.jetbrains.kotlin.plugin.allopen", version = "2.4.10" }
ksp = { id = "com.google.devtools.ksp", version = "2.3.10" }
```

`build.gradle.kts`:

```kotlin
plugins {
    `java-library`
    alias(libs.plugins.spotless)
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

repositories { mavenCentral() }

val micronautVersion: String =
    providers.gradleProperty("micronautVersion").getOrElse(libs.versions.micronaut.get())
val micronautPlatform = "io.micronaut.platform:micronaut-platform:$micronautVersion"

dependencies {
    api(libs.wiremock.standalone)

    compileOnly(platform(micronautPlatform))
    compileOnly("io.micronaut:micronaut-inject")
    compileOnly("io.micronaut.test:micronaut-test-junit5")
    compileOnly("org.junit.jupiter:junit-jupiter-api")
    compileOnly("org.slf4j:slf4j-api")
    compileOnly("jakarta.inject:jakarta.inject-api")
    compileOnly(libs.jspecify)

    annotationProcessor(platform(micronautPlatform))
    annotationProcessor("io.micronaut:micronaut-inject-java")

    testImplementation(platform(micronautPlatform))
    testImplementation("io.micronaut:micronaut-inject")
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.platform:junit-platform-testkit")
    testImplementation("org.assertj:assertj-core")
    testCompileOnly(libs.jspecify)
    testAnnotationProcessor(platform(micronautPlatform))
    testAnnotationProcessor("io.micronaut:micronaut-inject-java")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("ch.qos.logback:logback-classic")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}

tasks.test {
    useJUnitPlatform()
    exclude("**/fixtures/**")
}

spotless {
    java {
        target("src/**/*.java", "examples/**/*.java")
        googleJavaFormat(libs.versions.google.java.format.get())
    }
}
```

- [ ] **Step 3: Generate the Gradle wrapper**

Run (if `gradle` is not on `PATH`, first run `sdk install gradle 9.8.0`):

```bash
gradle wrapper --gradle-version 9.8.0
```

Expected: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` and
`gradle/wrapper/gradle-wrapper.properties` are created.

- [ ] **Step 4: Write the failing test**

`src/test/java/com/leeturner/wiremock/micronaut/PublicApiTest.java`:

```java
package com.leeturner.wiremock.micronaut;

import static org.assertj.core.api.Assertions.assertThat;

import com.leeturner.wiremock.micronaut.internal.WireMockMicronautExtension;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

class PublicApiTest {

  @ConfigureWireMock
  static class Defaults {}

  @EnableWireMock
  static class Enabled {}

  static class InheritsEnabled extends Enabled {}

  static class Injected {
    @InjectWireMock WireMockServer server;
  }

  @Test
  void configureWireMockDefaultsMatchWireMockSpringBoot() {
    ConfigureWireMock d = Defaults.class.getAnnotation(ConfigureWireMock.class);
    assertThat(d.name()).isEqualTo("wiremock");
    assertThat(d.port()).isZero();
    assertThat(d.httpsPort()).isEqualTo(-1);
    assertThat(d.portProperties()).containsExactly("wiremock.server.port");
    assertThat(d.httpsPortProperties()).containsExactly("wiremock.server.httpsPort");
    assertThat(d.baseUrlProperties()).containsExactly("wiremock.server.baseUrl");
    assertThat(d.httpsBaseUrlProperties()).containsExactly("wiremock.server.httpsBaseUrl");
    assertThat(d.filesUnderClasspath()).isEmpty();
    assertThat(d.filesUnderDirectory()).isEmpty();
    assertThat(d.extensions()).isEmpty();
    assertThat(d.extensionFactories()).isEmpty();
    assertThat(d.configurationCustomizers()).isEmpty();
    assertThat(d.resetWireMockServer()).isTrue();
    assertThat(d.registerBean()).isFalse();
    assertThat(d.globalTemplating()).isFalse();
    assertThat(d.keystorePath()).isEmpty();
    assertThat(d.needClientAuth()).isFalse();
    assertThat(ConfigureWireMock.DEFAULT_FILES_UNDER_DIRECTORY)
        .startsWith("wiremock", "stubs", "mappings", "src/test/resources/wiremock");
  }

  @Test
  void enableWireMockIsInheritedAndRegistersTheExtension() {
    assertThat(InheritsEnabled.class.isAnnotationPresent(EnableWireMock.class)).isTrue();
    assertThat(EnableWireMock.class.getAnnotation(ExtendWith.class).value())
        .containsExactly(WireMockMicronautExtension.class);
    assertThat(ConfigureWireMock.class.getAnnotation(ExtendWith.class).value())
        .containsExactly(WireMockMicronautExtension.class);
  }

  @Test
  void injectWireMockDefaultsToTheDefaultServerName() throws Exception {
    InjectWireMock inject =
        Injected.class.getDeclaredField("server").getAnnotation(InjectWireMock.class);
    assertThat(inject.value()).isEqualTo("wiremock");
  }
}
```

- [ ] **Step 5: Run the test to verify it fails**

Run: `./gradlew test --tests 'com.leeturner.wiremock.micronaut.PublicApiTest'`

Expected: compilation FAILS (`cannot find symbol ... ConfigureWireMock`).

- [ ] **Step 6: Write the API**

`src/main/java/com/leeturner/wiremock/micronaut/package-info.java`:

```java
/** WireMock integration for Micronaut tests. */
@NullMarked
package com.leeturner.wiremock.micronaut;

import org.jspecify.annotations.NullMarked;
```

`src/main/java/com/leeturner/wiremock/micronaut/internal/package-info.java`:

```java
/** Internal implementation. Not part of the public API. */
@NullMarked
package com.leeturner.wiremock.micronaut.internal;

import org.jspecify.annotations.NullMarked;
```

`src/main/java/com/leeturner/wiremock/micronaut/internal/WireMockMicronautExtension.java`
(a stub, replaced in Task 5):

```java
package com.leeturner.wiremock.micronaut.internal;

import org.junit.jupiter.api.extension.Extension;

/** JUnit extension behind {@code @EnableWireMock}. Implemented in a later task. */
public final class WireMockMicronautExtension implements Extension {}
```

`src/main/java/com/leeturner/wiremock/micronaut/EnableWireMock.java`:

```java
package com.leeturner.wiremock.micronaut;

import com.leeturner.wiremock.micronaut.internal.WireMockMicronautExtension;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Starts WireMock servers for a test class. Use alongside {@code @MicronautTest}. With no
 * {@link ConfigureWireMock} anywhere on the class, one server named {@code "wiremock"} is started.
 */
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(WireMockMicronautExtension.class)
public @interface EnableWireMock {

  /** The servers to start. */
  ConfigureWireMock[] value() default {};
}
```

`src/main/java/com/leeturner/wiremock/micronaut/ConfigureWireMock.java`:

```java
package com.leeturner.wiremock.micronaut;

import com.github.tomakehurst.wiremock.extension.Extension;
import com.github.tomakehurst.wiremock.extension.ExtensionFactory;
import com.leeturner.wiremock.micronaut.internal.WireMockMicronautExtension;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import org.junit.jupiter.api.extension.ExtendWith;

/** Configures one WireMock server. Attributes mirror wiremock-spring-boot. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ConfigureWireMocks.class)
@ExtendWith(WireMockMicronautExtension.class)
public @interface ConfigureWireMock {

  /** Directories searched, in order, when no files location is configured. */
  List<String> DEFAULT_FILES_UNDER_DIRECTORY =
      List.of(
          "wiremock",
          "stubs",
          "mappings",
          "src/test/resources/wiremock",
          "src/test/resources/stubs",
          "src/test/resources/mappings",
          "src/integtest/resources/wiremock",
          "src/integtest/resources/stubs",
          "src/integtest/resources/mappings");

  /** Server name, used by {@link InjectWireMock}. */
  String name() default "wiremock";

  /** HTTP port: {@code 0} dynamic, {@code -1} disabled, otherwise static. */
  int port() default 0;

  /** HTTPS port: {@code -1} disabled, {@code 0} dynamic, otherwise static. */
  int httpsPort() default -1;

  /** Properties set to the HTTP port. */
  String[] portProperties() default {"wiremock.server.port"};

  /** Properties set to the HTTPS port. */
  String[] httpsPortProperties() default {"wiremock.server.httpsPort"};

  /** Properties set to {@code http://localhost:<port>}. */
  String[] baseUrlProperties() default {"wiremock.server.baseUrl"};

  /** Properties set to {@code https://localhost:<httpsPort>}. */
  String[] httpsBaseUrlProperties() default {"wiremock.server.httpsBaseUrl"};

  /** Classpath root holding {@code mappings}/{@code __files}. */
  String filesUnderClasspath() default "";

  /** Directories holding {@code mappings}/{@code __files}; the first existing one is used. */
  String[] filesUnderDirectory() default {};

  /** WireMock extensions; each needs a public no-arg constructor. */
  Class<? extends Extension>[] extensions() default {};

  /** WireMock extension factories; each needs a public no-arg constructor. */
  Class<? extends ExtensionFactory>[] extensionFactories() default {};

  /** Customizers applied last; each needs a public no-arg constructor. */
  Class<? extends WireMockConfigurationCustomizer>[] configurationCustomizers() default {};

  /** Reset the server before each test. */
  boolean resetWireMockServer() default true;

  /** Register the server as a Micronaut bean qualified {@code @Named(name)}. */
  boolean registerBean() default false;

  /** Apply response templating to every stub. */
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
```

`src/main/java/com/leeturner/wiremock/micronaut/ConfigureWireMocks.java`:

```java
package com.leeturner.wiremock.micronaut;

import com.leeturner.wiremock.micronaut.internal.WireMockMicronautExtension;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/** Container for repeated {@link ConfigureWireMock}. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(WireMockMicronautExtension.class)
public @interface ConfigureWireMocks {
  ConfigureWireMock[] value();
}
```

`src/main/java/com/leeturner/wiremock/micronaut/InjectWireMock.java`:

```java
package com.leeturner.wiremock.micronaut;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Injects the named {@code WireMockServer} into a test field or test method parameter. */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface InjectWireMock {

  /** The server name. */
  String value() default "wiremock";
}
```

`src/main/java/com/leeturner/wiremock/micronaut/WireMockConfigurationCustomizer.java`:

```java
package com.leeturner.wiremock.micronaut;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/** Last-step customization of a server's configuration. Needs a public no-arg constructor. */
@FunctionalInterface
public interface WireMockConfigurationCustomizer {
  void customize(WireMockConfiguration configuration, ConfigureWireMock options);
}
```

`src/test/resources/logback-test.xml`:

```xml
<configuration>
  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <encoder><pattern>%d{HH:mm:ss.SSS} %-5level %logger{36} - %msg%n</pattern></encoder>
  </appender>
  <logger name="com.leeturner.wiremock.micronaut" level="DEBUG"/>
  <root level="WARN"><appender-ref ref="STDOUT"/></root>
</configuration>
```

- [ ] **Step 7: Run the test and the full build**

Run: `./gradlew spotlessApply build`

Expected: BUILD SUCCESSFUL, with `PublicApiTest` passing (3 tests).

- [ ] **Step 8: Commit**

```bash
git add -A
git commit --no-gpg-sign -m "feat: Add Gradle build and public annotations" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: ConfigurationResolver

**Files:**

- Create: `src/main/java/com/leeturner/wiremock/micronaut/internal/ConfigurationResolver.java`
- Test: `src/test/java/com/leeturner/wiremock/micronaut/internal/ConfigurationResolverTest.java`

**Interfaces:**

- Consumes: the Task 1 annotations.
- Produces (`public final class ConfigurationResolver`, all static):
  - `Set<String> DEFAULT_PROPERTY_NAMES`
  - `boolean isNested(Class<?> testClass)`: whether it is annotated
    `@Nested`.
  - `Class<?> rootTestClass(Class<?> testClass)`: the outermost enclosing
    class across `@Nested`.
  - `boolean isManaged(Class<?> testClass)`: whether the root class has
    `@EnableWireMock` or any `@ConfigureWireMock` (including inherited and
    meta-annotations).
  - `List<ConfigureWireMock> resolve(Class<?> testClass)`: validated
    configuration for the root class.
    - Returns an empty list if it is not managed.
    - Returns `[default "wiremock"]` if `@EnableWireMock` is present but no
      `@ConfigureWireMock` exists anywhere.
    - Throws `ExtensionConfigurationException` on invalid configuration or on
      declarations on a `@Nested` class.
  - `Map<String, List<String>> propertyOwners(List<ConfigureWireMock> configs)`:
    maps each non-blank property name to the names of the servers using it.

- [ ] **Step 1: Write the failing test**

```java
package com.leeturner.wiremock.micronaut.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import com.leeturner.wiremock.micronaut.EnableWireMock;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;

class ConfigurationResolverTest {

  static class Plain {}

  @EnableWireMock
  static class Bare {}

  @EnableWireMock({@ConfigureWireMock(name = "a"), @ConfigureWireMock(name = "b")})
  static class Two {}

  @ConfigureWireMock(name = "a")
  @ConfigureWireMock(name = "b")
  static class Standalone {}

  @EnableWireMock
  @ConfigureWireMock(name = "users")
  static class BarePlusStandalone {}

  static class InheritsBare extends Bare {}

  @EnableWireMock
  static class Outer {
    @Nested
    class Inner {
      @Nested
      class Deeper {}
    }

    @Nested
    @ConfigureWireMock(name = "x")
    class BadInner {}
  }

  @EnableWireMock({@ConfigureWireMock(name = "a"), @ConfigureWireMock(name = "a")})
  static class DuplicateNames {}

  @EnableWireMock(@ConfigureWireMock(name = "off", port = -1, httpsPort = -1))
  static class BothDisabled {}

  @EnableWireMock(@ConfigureWireMock(name = "same", port = 18080, httpsPort = 18080))
  static class SamePort {}

  @EnableWireMock({@ConfigureWireMock(name = "a", port = 18081), @ConfigureWireMock(name = "b", port = 18081)})
  static class ReusedPort {}

  @EnableWireMock(@ConfigureWireMock(name = "Users", registerBean = true))
  static class BadBeanName {}

  @EnableWireMock({
    @ConfigureWireMock(name = "a", baseUrlProperties = "shared.url"),
    @ConfigureWireMock(name = "b", baseUrlProperties = "shared.url")
  })
  static class ExplicitClash {}

  @Test
  void unannotatedClassIsNotManaged() {
    assertThat(ConfigurationResolver.isManaged(Plain.class)).isFalse();
    assertThat(ConfigurationResolver.resolve(Plain.class)).isEmpty();
  }

  @Test
  void bareEnableWireMockGivesTheDefaultServer() {
    assertThat(ConfigurationResolver.resolve(Bare.class))
        .singleElement()
        .satisfies(c -> assertThat(c.name()).isEqualTo("wiremock"));
  }

  @Test
  void enableAndStandaloneAnnotationsAreCollected() {
    assertThat(ConfigurationResolver.resolve(Two.class))
        .extracting(ConfigureWireMock::name)
        .containsExactly("a", "b");
    assertThat(ConfigurationResolver.resolve(Standalone.class))
        .extracting(ConfigureWireMock::name)
        .containsExactly("a", "b");
    assertThat(ConfigurationResolver.isManaged(Standalone.class)).isTrue();
  }

  @Test
  void defaultServerIsOnlyAddedWhenNothingIsConfigured() {
    assertThat(ConfigurationResolver.resolve(BarePlusStandalone.class))
        .extracting(ConfigureWireMock::name)
        .containsExactly("users");
  }

  @Test
  void enableWireMockIsInherited() {
    assertThat(ConfigurationResolver.resolve(InheritsBare.class)).hasSize(1);
  }

  @Test
  void nestedClassesResolveToTheirOutermostClass() {
    assertThat(ConfigurationResolver.isNested(Outer.Inner.class)).isTrue();
    assertThat(ConfigurationResolver.rootTestClass(Outer.Inner.Deeper.class)).isEqualTo(Outer.class);
    assertThat(ConfigurationResolver.resolve(Outer.Inner.Deeper.class))
        .extracting(ConfigureWireMock::name)
        .containsExactly("wiremock");
  }

  @Test
  void declarationsOnNestedClassesAreRejected() {
    assertThatThrownBy(() -> ConfigurationResolver.resolve(Outer.BadInner.class))
        .isInstanceOf(ExtensionConfigurationException.class)
        .hasMessageContaining("@ConfigureWireMock cannot be declared on the @Nested class")
        .hasMessageContaining("Move it to Outer");
  }

  @Test
  void duplicateNamesFail() {
    assertThatThrownBy(() -> ConfigurationResolver.resolve(DuplicateNames.class))
        .isInstanceOf(ExtensionConfigurationException.class)
        .hasMessageContaining("Duplicate WireMock server name(s) [a]");
  }

  @Test
  void invalidPortsFail() {
    assertThatThrownBy(() -> ConfigurationResolver.resolve(BothDisabled.class))
        .hasMessageContaining("'off'")
        .hasMessageContaining("both HTTP (port = -1) and HTTPS (httpsPort = -1) disabled");
    assertThatThrownBy(() -> ConfigurationResolver.resolve(SamePort.class))
        .hasMessageContaining("uses port 18080 for both HTTP and HTTPS");
    assertThatThrownBy(() -> ConfigurationResolver.resolve(ReusedPort.class))
        .hasMessageContaining("Static port(s) [18081]");
  }

  @Test
  void registerBeanNamesMustBePropertyKeySafe() {
    assertThatThrownBy(() -> ConfigurationResolver.resolve(BadBeanName.class))
        .isInstanceOf(ExtensionConfigurationException.class)
        .hasMessageContaining("'Users'")
        .hasMessageContaining("[a-z0-9][a-z0-9-]*");
  }

  @Test
  void explicitPropertyClashFailsButSharedDefaultsDoNot() {
    assertThatThrownBy(() -> ConfigurationResolver.resolve(ExplicitClash.class))
        .isInstanceOf(ExtensionConfigurationException.class)
        .hasMessageContaining("Property 'shared.url' is bound by more than one WireMock server")
        .hasMessageContaining("[a, b]");
    // Two servers share every default property name; that is allowed.
    assertThat(ConfigurationResolver.resolve(Two.class)).hasSize(2);
    assertThat(ConfigurationResolver.propertyOwners(ConfigurationResolver.resolve(Two.class)))
        .containsEntry("wiremock.server.port", java.util.List.of("a", "b"));
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests 'com.leeturner.wiremock.micronaut.internal.ConfigurationResolverTest'`

Expected: compilation FAILS (`cannot find symbol ... ConfigurationResolver`).

- [ ] **Step 3: Implement**

```java
package com.leeturner.wiremock.micronaut.internal;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import com.leeturner.wiremock.micronaut.EnableWireMock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.platform.commons.support.AnnotationSupport;

/** Turns WireMock annotations on a test class into a validated server list. */
public final class ConfigurationResolver {

  public static final Set<String> DEFAULT_PROPERTY_NAMES =
      Set.of(
          "wiremock.server.port",
          "wiremock.server.httpsPort",
          "wiremock.server.baseUrl",
          "wiremock.server.httpsBaseUrl");

  private static final Pattern BEAN_NAME = Pattern.compile("[a-z0-9][a-z0-9-]*");

  private static final String NESTED_DECLARATION =
      "%s cannot be declared on the @Nested class %s. A nested class shares the WireMock servers"
          + " of its enclosing class %s, so this configuration would be ignored. Move it to %s.";

  @ConfigureWireMock
  private static final class DefaultServer {}

  private static final ConfigureWireMock DEFAULT_SERVER =
      DefaultServer.class.getAnnotation(ConfigureWireMock.class);

  private ConfigurationResolver() {}

  public static boolean isNested(Class<?> testClass) {
    return AnnotationSupport.isAnnotated(testClass, Nested.class);
  }

  public static Class<?> rootTestClass(Class<?> testClass) {
    Class<?> current = testClass;
    while (isNested(current) && current.getEnclosingClass() != null) {
      current = current.getEnclosingClass();
    }
    return current;
  }

  public static boolean isManaged(Class<?> testClass) {
    Class<?> root = rootTestClass(testClass);
    return AnnotationSupport.isAnnotated(root, EnableWireMock.class)
        || !AnnotationSupport.findRepeatableAnnotations(root, ConfigureWireMock.class).isEmpty();
  }

  public static List<ConfigureWireMock> resolve(Class<?> testClass) {
    rejectNestedDeclarations(testClass);
    Class<?> root = rootTestClass(testClass);
    if (!isManaged(root)) {
      return List.of();
    }
    List<ConfigureWireMock> configs = new ArrayList<>();
    AnnotationSupport.findAnnotation(root, EnableWireMock.class)
        .ifPresent(enable -> configs.addAll(Arrays.asList(enable.value())));
    configs.addAll(AnnotationSupport.findRepeatableAnnotations(root, ConfigureWireMock.class));
    if (configs.isEmpty()) {
      configs.add(DEFAULT_SERVER);
    }
    validate(root, configs);
    return List.copyOf(configs);
  }

  public static Map<String, List<String>> propertyOwners(List<ConfigureWireMock> configs) {
    Map<String, List<String>> owners = new LinkedHashMap<>();
    for (ConfigureWireMock config : configs) {
      Set<String> names = new LinkedHashSet<>();
      Stream.of(
              config.portProperties(),
              config.httpsPortProperties(),
              config.baseUrlProperties(),
              config.httpsBaseUrlProperties())
          .flatMap(Arrays::stream)
          .filter(name -> !name.isBlank())
          .forEach(names::add);
      names.forEach(name -> owners.computeIfAbsent(name, k -> new ArrayList<>()).add(config.name()));
    }
    return owners;
  }

  private static void rejectNestedDeclarations(Class<?> testClass) {
    for (Class<?> c = testClass;
        isNested(c) && c.getEnclosingClass() != null;
        c = c.getEnclosingClass()) {
      String annotation = null;
      if (c.getDeclaredAnnotation(EnableWireMock.class) != null) {
        annotation = "@EnableWireMock";
      } else if (c.getDeclaredAnnotationsByType(ConfigureWireMock.class).length > 0) {
        annotation = "@ConfigureWireMock";
      }
      if (annotation != null) {
        Class<?> root = rootTestClass(c);
        throw fail(
            NESTED_DECLARATION, annotation, c.getName(), root.getName(), root.getSimpleName());
      }
    }
  }

  private static void validate(Class<?> root, List<ConfigureWireMock> configs) {
    String testClass = root.getName();
    List<String> duplicateNames =
        configs.stream()
            .collect(groupingBy(ConfigureWireMock::name, LinkedHashMap::new, counting()))
            .entrySet()
            .stream()
            .filter(e -> e.getValue() > 1)
            .map(Map.Entry::getKey)
            .toList();
    if (!duplicateNames.isEmpty()) {
      throw fail(
          "Duplicate WireMock server name(s) %s on %s. Give each @ConfigureWireMock a unique name.",
          duplicateNames, testClass);
    }
    for (ConfigureWireMock c : configs) {
      if (c.port() == -1 && c.httpsPort() == -1) {
        throw fail(
            "WireMock server '%s' on %s has both HTTP (port = -1) and HTTPS (httpsPort = -1)"
                + " disabled.",
            c.name(), testClass);
      }
      if (c.port() > 0 && c.port() == c.httpsPort()) {
        throw fail(
            "WireMock server '%s' on %s uses port %d for both HTTP and HTTPS.",
            c.name(), testClass, c.port());
      }
      if (c.registerBean() && !BEAN_NAME.matcher(c.name()).matches()) {
        throw fail(
            "WireMock server '%s' on %s has registerBean = true, so its name must match %s (it"
                + " becomes part of a Micronaut property key).",
            c.name(), testClass, BEAN_NAME.pattern());
      }
    }
    List<Integer> reusedPorts =
        configs.stream()
            .flatMap(c -> Stream.of(c.port(), c.httpsPort()))
            .filter(port -> port > 0)
            .collect(groupingBy(identity(), TreeMap::new, counting()))
            .entrySet()
            .stream()
            .filter(e -> e.getValue() > 1)
            .map(Map.Entry::getKey)
            .toList();
    if (!reusedPorts.isEmpty()) {
      throw fail(
          "Static port(s) %s are used by more than one WireMock server on %s.",
          reusedPorts, testClass);
    }
    propertyOwners(configs)
        .forEach(
            (property, owners) -> {
              if (owners.size() > 1 && !DEFAULT_PROPERTY_NAMES.contains(property)) {
                throw fail(
                    "Property '%s' is bound by more than one WireMock server on %s: %s. Give each"
                        + " server its own property name.",
                    property, testClass, owners);
              }
            });
  }

  private static ExtensionConfigurationException fail(String format, Object... args) {
    return new ExtensionConfigurationException(format.formatted(args));
  }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew spotlessApply test --tests 'com.leeturner.wiremock.micronaut.internal.ConfigurationResolverTest'`

Expected: PASS (11 tests).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit --no-gpg-sign -m "feat: Resolve and validate WireMock configuration from test classes" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: WireMockServerCreator and Slf4jNotifier

**Files:**

- Create: `src/main/java/com/leeturner/wiremock/micronaut/internal/WireMockServerCreator.java`
- Create: `src/main/java/com/leeturner/wiremock/micronaut/internal/Slf4jNotifier.java`
- Create: `src/test/java/com/leeturner/wiremock/micronaut/testsupport/Http.java`
- Create: `src/test/resources/classpath-stubs/mappings/from-classpath.json`
- Create: `src/test/directory-stubs/mappings/from-directory.json`
- Test: `src/test/java/com/leeturner/wiremock/micronaut/internal/WireMockServerCreatorTest.java`

**Interfaces:**

- Consumes: `ConfigureWireMock`, `WireMockConfigurationCustomizer`.
- Produces:
  - `static WireMockServer WireMockServerCreator.create(ConfigureWireMock options, String rootTestClassName)`,
    which returns a *started* server.
    - Configuration errors throw `ExtensionConfigurationException`.
    - Start failures throw `IllegalStateException` whose message contains
      `'<name>'`.
  - Test helper `com.leeturner.wiremock.micronaut.testsupport.Http`:
    `static HttpResponse<String> get(String url)` and
    `static HttpResponse<String> post(String url, String body)`.

- [ ] **Step 1: Write the test helper and stub files**

`src/test/java/com/leeturner/wiremock/micronaut/testsupport/Http.java`:

```java
package com.leeturner.wiremock.micronaut.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public final class Http {
  private static final HttpClient CLIENT = HttpClient.newHttpClient();

  private Http() {}

  public static HttpResponse<String> get(String url) {
    return send(HttpRequest.newBuilder(URI.create(url)).GET().build());
  }

  public static HttpResponse<String> post(String url, String body) {
    return send(
        HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build());
  }

  private static HttpResponse<String> send(HttpRequest request) {
    try {
      return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
```

`src/test/resources/classpath-stubs/mappings/from-classpath.json`:

```json
{"request": {"method": "GET", "url": "/from-classpath"}, "response": {"status": 200, "body": "classpath"}}
```

`src/test/directory-stubs/mappings/from-directory.json`:

```json
{"request": {"method": "GET", "url": "/from-directory"}, "response": {"status": 200, "body": "directory"}}
```

- [ ] **Step 2: Write the failing test**

```java
package com.leeturner.wiremock.micronaut.internal;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import com.leeturner.wiremock.micronaut.WireMockConfigurationCustomizer;
import com.leeturner.wiremock.micronaut.testsupport.Http;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;

class WireMockServerCreatorTest {

  public static class UppercaseTransformer implements ResponseTransformerV2 {
    @Override
    public Response transform(Response response, ServeEvent serveEvent) {
      return Response.Builder.like(response).body(response.getBodyAsString().toUpperCase()).build();
    }

    @Override
    public String getName() {
      return "uppercase";
    }
  }

  public static class NeedsArgs implements ResponseTransformerV2 {
    public NeedsArgs(String unused) {}

    @Override
    public Response transform(Response response, ServeEvent serveEvent) {
      return response;
    }

    @Override
    public String getName() {
      return "needs-args";
    }
  }

  public static class RecordingCustomizer implements WireMockConfigurationCustomizer {
    static final AtomicReference<String> SEEN = new AtomicReference<>();

    @Override
    public void customize(WireMockConfiguration configuration, ConfigureWireMock options) {
      SEEN.set(options.name());
      configuration.extensions(new UppercaseTransformer());
    }
  }

  @ConfigureWireMock static class Defaults {}

  @ConfigureWireMock(port = -1, httpsPort = 0)
  static class HttpsOnly {}

  @ConfigureWireMock(filesUnderClasspath = "classpath-stubs")
  static class ClasspathStubs {}

  @ConfigureWireMock(filesUnderClasspath = "missing-stubs")
  static class MissingClasspath {}

  @ConfigureWireMock(filesUnderDirectory = {"does-not-exist", "src/test/directory-stubs"})
  static class DirectoryStubs {}

  @ConfigureWireMock(filesUnderDirectory = "does-not-exist")
  static class MissingDirectory {}

  @ConfigureWireMock(extensions = UppercaseTransformer.class)
  static class WithExtension {}

  @ConfigureWireMock(extensions = NeedsArgs.class)
  static class WithBadExtension {}

  @ConfigureWireMock(name = "custom", configurationCustomizers = RecordingCustomizer.class)
  static class WithCustomizer {}

  @ConfigureWireMock(globalTemplating = true)
  static class Templated {}

  @ConfigureWireMock(name = "tls", port = -1, httpsPort = 0, keystorePath = "does-not-exist.jks")
  static class BadKeystore {}

  private final List<WireMockServer> started = new ArrayList<>();

  @AfterEach
  void stopServers() {
    started.forEach(WireMockServer::stop);
  }

  private WireMockServer create(Class<?> fixture) {
    WireMockServer server =
        WireMockServerCreator.create(
            fixture.getAnnotation(ConfigureWireMock.class), fixture.getName());
    started.add(server);
    return server;
  }

  @Test
  void defaultsGiveARunningDynamicHttpServer() {
    WireMockServer server = create(Defaults.class);
    assertThat(server.isRunning()).isTrue();
    assertThat(server.port()).isPositive();
    assertThat(server.isHttpsEnabled()).isFalse();
  }

  @Test
  void httpCanBeDisabledAndHttpsMadeDynamic() {
    WireMockServer server = create(HttpsOnly.class);
    assertThat(server.isHttpEnabled()).isFalse();
    assertThat(server.isHttpsEnabled()).isTrue();
    assertThat(server.httpsPort()).isPositive();
  }

  @Test
  void loadsStubsFromTheClasspath() {
    WireMockServer server = create(ClasspathStubs.class);
    assertThat(Http.get(server.baseUrl() + "/from-classpath").body()).isEqualTo("classpath");
  }

  @Test
  void missingClasspathLocationFails() {
    assertThatThrownBy(() -> create(MissingClasspath.class))
        .isInstanceOf(ExtensionConfigurationException.class)
        .hasMessageContaining("missing-stubs")
        .hasMessageContaining("'wiremock'");
  }

  @Test
  void usesTheFirstExistingConfiguredDirectory() {
    WireMockServer server = create(DirectoryStubs.class);
    assertThat(Http.get(server.baseUrl() + "/from-directory").body()).isEqualTo("directory");
  }

  @Test
  void missingDirectoryFails() {
    assertThatThrownBy(() -> create(MissingDirectory.class))
        .isInstanceOf(ExtensionConfigurationException.class)
        .hasMessageContaining("None of filesUnderDirectory [does-not-exist]");
  }

  @Test
  void appliesExtensions() {
    WireMockServer server = create(WithExtension.class);
    server.stubFor(get("/shout").willReturn(ok("hello")));
    assertThat(Http.get(server.baseUrl() + "/shout").body()).isEqualTo("HELLO");
  }

  @Test
  void extensionWithoutNoArgConstructorFails() {
    assertThatThrownBy(() -> create(WithBadExtension.class))
        .isInstanceOf(ExtensionConfigurationException.class)
        .hasMessageContaining(NeedsArgs.class.getName())
        .hasMessageContaining("'wiremock'")
        .hasMessageContaining("public no-arg constructor");
  }

  @Test
  void appliesCustomizersLast() {
    WireMockServer server = create(WithCustomizer.class);
    server.stubFor(get("/shout").willReturn(ok("hello")));
    assertThat(RecordingCustomizer.SEEN.get()).isEqualTo("custom");
    assertThat(Http.get(server.baseUrl() + "/shout").body()).isEqualTo("HELLO");
  }

  @Test
  void globalTemplatingRendersEveryStub() {
    WireMockServer server = create(Templated.class);
    server.stubFor(get("/templated").willReturn(ok("{{request.path}}")));
    assertThat(Http.get(server.baseUrl() + "/templated").body()).isEqualTo("/templated");
  }

  @Test
  void startFailureNamesTheServer() {
    assertThatThrownBy(() -> create(BadKeystore.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("'tls'");
  }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew test --tests 'com.leeturner.wiremock.micronaut.internal.WireMockServerCreatorTest'`

Expected: compilation FAILS (`cannot find symbol ... WireMockServerCreator`).

- [ ] **Step 4: Implement**

`Slf4jNotifier.java`:

```java
package com.leeturner.wiremock.micronaut.internal;

import com.github.tomakehurst.wiremock.common.Notifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Routes WireMock's own output through SLF4J, one logger per server. */
final class Slf4jNotifier implements Notifier {
  private final Logger logger;

  Slf4jNotifier(String serverName) {
    this.logger = LoggerFactory.getLogger("WireMock." + serverName);
  }

  @Override
  public void info(String message) {
    logger.info(message);
  }

  @Override
  public void error(String message) {
    logger.error(message);
  }

  @Override
  public void error(String message, Throwable t) {
    logger.error(message, t);
  }
}
```

`WireMockServerCreator.java`:

```java
package com.leeturner.wiremock.micronaut.internal;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.Extension;
import com.github.tomakehurst.wiremock.extension.ExtensionFactory;
import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import com.leeturner.wiremock.micronaut.WireMockConfigurationCustomizer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Builds and starts one WireMock server from its annotation. */
final class WireMockServerCreator {
  private static final Logger LOG = LoggerFactory.getLogger(WireMockServerCreator.class);

  private WireMockServerCreator() {}

  static WireMockServer create(ConfigureWireMock options, String rootTestClassName) {
    WireMockConfiguration config =
        WireMockConfiguration.options().notifier(new Slf4jNotifier(options.name()));
    configurePorts(config, options);
    configureTls(config, options);
    configureFiles(config, options, rootTestClassName);
    if (options.extensions().length > 0) {
      config.extensions(
          Arrays.stream(options.extensions())
              .map(type -> instantiate(type, options))
              .toArray(Extension[]::new));
    }
    if (options.extensionFactories().length > 0) {
      config.extensionFactories(
          Arrays.stream(options.extensionFactories())
              .map(type -> instantiate(type, options))
              .toArray(ExtensionFactory[]::new));
    }
    if (options.globalTemplating()) {
      config.globalTemplating(true);
    }
    for (Class<? extends WireMockConfigurationCustomizer> type :
        options.configurationCustomizers()) {
      WireMockConfigurationCustomizer customizer = instantiate(type, options);
      LOG.debug("Applying {} to WireMock server '{}'", type.getName(), options.name());
      customizer.customize(config, options);
    }
    WireMockServer server;
    try {
      server = new WireMockServer(config);
      server.start();
    } catch (RuntimeException e) {
      throw new IllegalStateException(
          "Failed to start WireMock server '%s' (port = %d, httpsPort = %d) for %s"
              .formatted(options.name(), options.port(), options.httpsPort(), rootTestClassName),
          e);
    }
    LOG.info(
        "WireMock '{}' started on {}",
        options.name(),
        server.isHttpEnabled()
            ? "http://localhost:" + server.port()
            : "https://localhost:" + server.httpsPort());
    return server;
  }

  private static void configurePorts(WireMockConfiguration config, ConfigureWireMock options) {
    if (options.port() == -1) {
      config.httpDisabled(true);
    } else if (options.port() == 0) {
      config.dynamicPort();
    } else {
      config.port(options.port());
    }
    if (options.httpsPort() == 0) {
      config.dynamicHttpsPort();
    } else if (options.httpsPort() > 0) {
      config.httpsPort(options.httpsPort());
    }
  }

  private static void configureTls(WireMockConfiguration config, ConfigureWireMock options) {
    setIfPresent(options.keystorePath(), config::keystorePath);
    setIfPresent(options.keystorePassword(), config::keystorePassword);
    setIfPresent(options.keystoreType(), config::keystoreType);
    setIfPresent(options.keyManagerPassword(), config::keyManagerPassword);
    setIfPresent(options.trustStorePath(), config::trustStorePath);
    setIfPresent(options.trustStorePassword(), config::trustStorePassword);
    setIfPresent(options.trustStoreType(), config::trustStoreType);
    if (options.needClientAuth()) {
      config.needClientAuth(true);
    }
  }

  private static void configureFiles(
      WireMockConfiguration config, ConfigureWireMock options, String rootTestClassName) {
    if (options.filesUnderDirectory().length > 0) {
      String dir =
          firstExistingStubDirectory(Arrays.asList(options.filesUnderDirectory()))
              .orElseThrow(
                  () ->
                      new ExtensionConfigurationException(
                          ("None of filesUnderDirectory %s for WireMock server '%s' on %s contains"
                                  + " a 'mappings' or '__files' directory.")
                              .formatted(
                                  Arrays.toString(options.filesUnderDirectory()),
                                  options.name(),
                                  rootTestClassName)));
      LOG.debug("WireMock '{}' serves stubs from directory {}", options.name(), dir);
      config.usingFilesUnderDirectory(dir);
    } else if (!options.filesUnderClasspath().isBlank()) {
      String resource = options.filesUnderClasspath();
      if (Thread.currentThread().getContextClassLoader().getResource(resource) == null) {
        throw new ExtensionConfigurationException(
            ("filesUnderClasspath '%s' for WireMock server '%s' on %s was not found on the"
                    + " classpath.")
                .formatted(resource, options.name(), rootTestClassName));
      }
      LOG.debug("WireMock '{}' serves stubs from classpath {}", options.name(), resource);
      config.usingFilesUnderClasspath(resource);
    } else {
      firstExistingStubDirectory(ConfigureWireMock.DEFAULT_FILES_UNDER_DIRECTORY)
          .ifPresentOrElse(
              dir -> {
                LOG.debug("WireMock '{}' serves stubs from directory {}", options.name(), dir);
                config.usingFilesUnderDirectory(dir);
              },
              () ->
                  LOG.info(
                      "No stub directory found for WireMock server '{}' (looked in {})",
                      options.name(),
                      ConfigureWireMock.DEFAULT_FILES_UNDER_DIRECTORY));
    }
  }

  private static Optional<String> firstExistingStubDirectory(List<String> candidates) {
    return candidates.stream()
        .filter(
            dir ->
                Files.isDirectory(Path.of(dir, "mappings"))
                    || Files.isDirectory(Path.of(dir, "__files")))
        .findFirst();
  }

  private static void setIfPresent(String value, Consumer<String> setter) {
    if (!value.isBlank()) {
      setter.accept(value);
    }
  }

  private static <T> T instantiate(Class<? extends T> type, ConfigureWireMock options) {
    try {
      return type.getConstructor().newInstance();
    } catch (NoSuchMethodException e) {
      throw new ExtensionConfigurationException(
          "%s configured on WireMock server '%s' must have a public no-arg constructor."
              .formatted(type.getName(), options.name()),
          e);
    } catch (ReflectiveOperationException e) {
      throw new ExtensionConfigurationException(
          "Could not create %s for WireMock server '%s'."
              .formatted(type.getName(), options.name()),
          e);
    }
  }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew spotlessApply test --tests 'com.leeturner.wiremock.micronaut.internal.WireMockServerCreatorTest'`

Expected: PASS (11 tests).

If `startFailureNamesTheServer` fails because WireMock accepts a missing
keystore lazily, change the fixture to `keystoreType = "NOT-A-TYPE"` with
`keystorePath = "does-not-exist.jks"` and re-run. The assertion stays the
same.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit --no-gpg-sign -m "feat: Create WireMock servers from @ConfigureWireMock" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Server registry and Micronaut properties

**Files:**

- Create: `src/main/java/com/leeturner/wiremock/micronaut/internal/RunningServer.java`
- Create: `src/main/java/com/leeturner/wiremock/micronaut/internal/WireMockServers.java`
- Create: `src/main/java/com/leeturner/wiremock/micronaut/internal/ServerProperties.java`
- Test: `src/test/java/com/leeturner/wiremock/micronaut/internal/WireMockServersTest.java`

**Interfaces:**

- Consumes: `ConfigurationResolver.resolve/rootTestClass/propertyOwners`
  and `WireMockServerCreator.create`.
- Produces:
  - `public record RunningServer(ConfigureWireMock options, WireMockServer server)`.
  - `WireMockServers` (all `public static`):
    - `Map<String, RunningServer> getOrStart(Class<?> testClass)`: keyed by
      server name in declaration order, unmodifiable. Empty if the class is
      not managed.
    - `WireMockServer get(String rootTestClassName, String serverName)`:
      throws `IllegalStateException` if the server is not running.
    - `void stop(Class<?> testClass)`: stops the root class's servers.
    - `Set<String> runningRootTestClasses()`.
  - `ServerProperties`:
    - `public static final String REGISTRY_KEY_PROPERTY = "wiremock.micronaut.servers.%s.registry-key"`.
    - `public static Map<String, String> of(String rootTestClassName, Collection<RunningServer> servers)`.

- [ ] **Step 1: Write the failing test**

```java
package com.leeturner.wiremock.micronaut.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import com.leeturner.wiremock.micronaut.EnableWireMock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WireMockServersTest {

  static class Plain {}

  @EnableWireMock({
    @ConfigureWireMock(name = "a", baseUrlProperties = "a.url", portProperties = "a.port"),
    @ConfigureWireMock(
        name = "b",
        baseUrlProperties = "b.url",
        httpsPort = 0,
        httpsBaseUrlProperties = "b.https",
        registerBean = true)
  })
  static class TwoServers {}

  @EnableWireMock({
    @ConfigureWireMock(name = "ok"),
    @ConfigureWireMock(name = "bad", port = -1, httpsPort = 0, keystorePath = "does-not-exist.jks")
  })
  static class FailsToStart {}

  @EnableWireMock
  static class Outer {
    @Nested
    class Inner {}
  }

  @EnableWireMock
  static class Concurrent {}

  @AfterEach
  void stopAll() {
    List.of(TwoServers.class, FailsToStart.class, Outer.class, Concurrent.class)
        .forEach(WireMockServers::stop);
  }

  @Test
  void getOrStartIsIdempotent() {
    Map<String, RunningServer> first = WireMockServers.getOrStart(TwoServers.class);
    assertThat(WireMockServers.getOrStart(TwoServers.class)).isSameAs(first);
    assertThat(first).containsOnlyKeys("a", "b");
    assertThat(first.values()).allSatisfy(s -> assertThat(s.server().isRunning()).isTrue());
  }

  @Test
  void unmanagedClassesGetNoServers() {
    assertThat(WireMockServers.getOrStart(Plain.class)).isEmpty();
    assertThat(WireMockServers.runningRootTestClasses()).doesNotContain(Plain.class.getName());
  }

  @Test
  void propertiesAreBoundPerServerAndSharedDefaultsAreSkipped() {
    Map<String, RunningServer> servers = WireMockServers.getOrStart(TwoServers.class);
    WireMockServer a = servers.get("a").server();
    WireMockServer b = servers.get("b").server();

    Map<String, String> props = ServerProperties.of(TwoServers.class.getName(), servers.values());

    assertThat(props)
        .containsEntry("a.url", "http://localhost:" + a.port())
        .containsEntry("a.port", String.valueOf(a.port()))
        .containsEntry("b.url", "http://localhost:" + b.port())
        .containsEntry("b.https", "https://localhost:" + b.httpsPort())
        // only "b" uses the default port property, so it is bound
        .containsEntry("wiremock.server.port", String.valueOf(b.port()))
        // both use the default HTTPS port property, so it is skipped
        .doesNotContainKey("wiremock.server.httpsPort")
        .containsEntry(
            "wiremock.micronaut.servers.b.registry-key", TwoServers.class.getName())
        .doesNotContainKey("wiremock.micronaut.servers.a.registry-key");
  }

  @Test
  void getFindsRunningServersAndFailsClearlyOtherwise() {
    Map<String, RunningServer> servers = WireMockServers.getOrStart(TwoServers.class);
    assertThat(WireMockServers.get(TwoServers.class.getName(), "a"))
        .isSameAs(servers.get("a").server());
    assertThatThrownBy(() -> WireMockServers.get(TwoServers.class.getName(), "zzz"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("'zzz'");
  }

  @Test
  void stopStopsServersAndAllowsRestart() {
    Map<String, RunningServer> first = WireMockServers.getOrStart(TwoServers.class);
    WireMockServers.stop(TwoServers.class);

    assertThat(first.values()).allSatisfy(s -> assertThat(s.server().isRunning()).isFalse());
    assertThat(WireMockServers.runningRootTestClasses()).doesNotContain(TwoServers.class.getName());

    Map<String, RunningServer> second = WireMockServers.getOrStart(TwoServers.class);
    assertThat(second).isNotSameAs(first);
    assertThat(second.values()).allSatisfy(s -> assertThat(s.server().isRunning()).isTrue());
  }

  @Test
  void failedStartLeavesNothingRegistered() {
    assertThatThrownBy(() -> WireMockServers.getOrStart(FailsToStart.class))
        .hasMessageContaining("'bad'");
    assertThat(WireMockServers.runningRootTestClasses())
        .doesNotContain(FailsToStart.class.getName());
  }

  @Test
  void nestedClassesShareTheirRootServers() {
    assertThat(WireMockServers.getOrStart(Outer.Inner.class))
        .isSameAs(WireMockServers.getOrStart(Outer.class));
  }

  @Test
  void concurrentCallersShareOneStart() throws Exception {
    try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
      List<Callable<Map<String, RunningServer>>> calls =
          IntStream.range(0, 8)
              .<Callable<Map<String, RunningServer>>>mapToObj(
                  i -> () -> WireMockServers.getOrStart(Concurrent.class))
              .toList();
      List<Future<Map<String, RunningServer>>> results = pool.invokeAll(calls);
      Map<String, RunningServer> first = results.getFirst().get();
      for (Future<Map<String, RunningServer>> result : results) {
        assertThat(result.get()).isSameAs(first);
      }
    }
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests 'com.leeturner.wiremock.micronaut.internal.WireMockServersTest'`

Expected: compilation FAILS (`cannot find symbol ... WireMockServers`).

- [ ] **Step 3: Implement**

`RunningServer.java`:

```java
package com.leeturner.wiremock.micronaut.internal;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leeturner.wiremock.micronaut.ConfigureWireMock;

/** A started server together with the annotation that configured it. */
public record RunningServer(ConfigureWireMock options, WireMockServer server) {}
```

`WireMockServers.java`:

```java
package com.leeturner.wiremock.micronaut.internal;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Running servers per root test class. Shared by the property factory and the JUnit extension;
 * whichever calls {@link #getOrStart} first starts the servers.
 */
public final class WireMockServers {
  private static final Logger LOG = LoggerFactory.getLogger(WireMockServers.class);
  private static final ConcurrentMap<String, Map<String, RunningServer>> SERVERS =
      new ConcurrentHashMap<>();

  private WireMockServers() {}

  public static Map<String, RunningServer> getOrStart(Class<?> testClass) {
    List<ConfigureWireMock> configs = ConfigurationResolver.resolve(testClass);
    if (configs.isEmpty()) {
      return Map.of();
    }
    String root = ConfigurationResolver.rootTestClass(testClass).getName();
    return SERVERS.computeIfAbsent(root, key -> startAll(key, configs));
  }

  public static WireMockServer get(String rootTestClassName, String serverName) {
    Map<String, RunningServer> servers = SERVERS.get(rootTestClassName);
    RunningServer running = servers == null ? null : servers.get(serverName);
    if (running == null) {
      throw new IllegalStateException(
          "No running WireMock server '%s' for test class %s"
              .formatted(serverName, rootTestClassName));
    }
    return running.server();
  }

  public static void stop(Class<?> testClass) {
    Map<String, RunningServer> servers =
        SERVERS.remove(ConfigurationResolver.rootTestClass(testClass).getName());
    if (servers != null) {
      stopAll(servers.values());
    }
  }

  public static Set<String> runningRootTestClasses() {
    return Set.copyOf(SERVERS.keySet());
  }

  private static Map<String, RunningServer> startAll(
      String rootTestClassName, List<ConfigureWireMock> configs) {
    Map<String, RunningServer> started = new LinkedHashMap<>();
    try {
      for (ConfigureWireMock options : configs) {
        WireMockServer server = WireMockServerCreator.create(options, rootTestClassName);
        started.put(options.name(), new RunningServer(options, server));
      }
    } catch (RuntimeException e) {
      stopAll(started.values());
      throw e;
    }
    return Collections.unmodifiableMap(started);
  }

  private static void stopAll(Collection<RunningServer> servers) {
    for (RunningServer running : servers) {
      try {
        if (running.server().isRunning()) {
          running.server().stop();
          LOG.info("WireMock '{}' stopped", running.options().name());
        }
      } catch (RuntimeException e) {
        LOG.warn("Failed to stop WireMock server '{}'", running.options().name(), e);
      }
    }
  }
}
```

`ServerProperties.java`:

```java
package com.leeturner.wiremock.micronaut.internal;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Micronaut properties published for a set of running servers. */
public final class ServerProperties {
  public static final String REGISTRY_KEY_PROPERTY = "wiremock.micronaut.servers.%s.registry-key";
  private static final Logger LOG = LoggerFactory.getLogger(ServerProperties.class);

  private ServerProperties() {}

  public static Map<String, String> of(
      String rootTestClassName, Collection<RunningServer> servers) {
    Map<String, List<String>> owners =
        ConfigurationResolver.propertyOwners(
            servers.stream().map(RunningServer::options).toList());
    Map<String, String> properties = new LinkedHashMap<>();
    for (RunningServer running : servers) {
      ConfigureWireMock options = running.options();
      WireMockServer server = running.server();
      if (server.isHttpEnabled()) {
        bind(properties, owners, options.baseUrlProperties(), "http://localhost:" + server.port());
        bind(properties, owners, options.portProperties(), String.valueOf(server.port()));
      }
      if (server.isHttpsEnabled()) {
        bind(
            properties,
            owners,
            options.httpsBaseUrlProperties(),
            "https://localhost:" + server.httpsPort());
        bind(properties, owners, options.httpsPortProperties(), String.valueOf(server.httpsPort()));
      }
      if (options.registerBean()) {
        properties.put(REGISTRY_KEY_PROPERTY.formatted(options.name()), rootTestClassName);
      }
    }
    return Collections.unmodifiableMap(properties);
  }

  private static void bind(
      Map<String, String> properties,
      Map<String, List<String>> owners,
      String[] names,
      String value) {
    for (String name : names) {
      if (name.isBlank()) {
        continue;
      }
      List<String> sharedBy = owners.getOrDefault(name, List.of());
      if (sharedBy.size() > 1) {
        LOG.debug("Not binding '{}': it is shared by WireMock servers {}", name, sharedBy);
        continue;
      }
      properties.put(name, value);
    }
  }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew spotlessApply test --tests 'com.leeturner.wiremock.micronaut.internal.WireMockServersTest'`

Expected: PASS (8 tests). If `failedStartLeavesNothingRegistered` does not
fail to start, apply the same keystore fixture change as Task 3 Step 5.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit --no-gpg-sign -m "feat: Add per-test-class server registry and property mapping" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: JUnit extension (injection, reset, static DSL, cleanup)

**Files:**

- Modify: `src/main/java/com/leeturner/wiremock/micronaut/internal/WireMockMicronautExtension.java`
  (replace the stub entirely).
- Test: `src/test/java/com/leeturner/wiremock/micronaut/PlainJUnitExtensionTest.java`
- Test: `src/test/java/com/leeturner/wiremock/micronaut/ResetTest.java`
- Test: `src/test/java/com/leeturner/wiremock/micronaut/FileStubsSurviveResetTest.java`
- Test: `src/test/java/com/leeturner/wiremock/micronaut/StaticDslWithSeveralServersTest.java`

**Interfaces:**

- Consumes: `WireMockServers.getOrStart/stop`,
  `ConfigurationResolver.resolve/rootTestClass/isNested` and
  `RunningServer`.
- Produces: `public final class WireMockMicronautExtension implements
  BeforeAllCallback, TestInstancePostProcessor, BeforeEachCallback,
  AfterEachCallback, AfterAllCallback, ParameterResolver`.

- [ ] **Step 1: Write the failing tests**

`PlainJUnitExtensionTest.java` (no `@MicronautTest`: the extension works on
its own):

```java
package com.leeturner.wiremock.micronaut;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.junit.Stubbing;
import com.leeturner.wiremock.micronaut.testsupport.Http;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@EnableWireMock(@ConfigureWireMock(name = "users"))
class PlainJUnitExtensionTest {

  @InjectWireMock("users")
  WireMockServer users;

  @InjectWireMock("users")
  Stubbing usersAsStubbing;

  @Test
  void injectsFieldsAndParameters(@InjectWireMock("users") WireMockServer parameter) {
    assertThat(users.isRunning()).isTrue();
    assertThat(parameter).isSameAs(users);
    assertThat(usersAsStubbing).isSameAs(users);
  }

  @Test
  void staticDslPointsAtTheOnlyServer() {
    stubFor(get("/hello").willReturn(ok("hi")));
    assertThat(Http.get(users.baseUrl() + "/hello").body()).isEqualTo("hi");
  }

  @Nested
  class Inner {
    @InjectWireMock("users")
    WireMockServer inner;

    @Test
    void nestedTestsShareTheEnclosingServer() {
      assertThat(inner).isSameAs(users);
    }
  }
}
```

`ResetTest.java`:

```java
package com.leeturner.wiremock.micronaut;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@EnableWireMock({
  @ConfigureWireMock(name = "reset"),
  @ConfigureWireMock(name = "keep", resetWireMockServer = false)
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ResetTest {

  @InjectWireMock("reset")
  WireMockServer reset;

  @InjectWireMock("keep")
  WireMockServer keep;

  @Test
  @Order(1)
  void stubBoth() {
    reset.stubFor(get("/a").willReturn(ok()));
    keep.stubFor(get("/a").willReturn(ok()));
  }

  @Test
  @Order(2)
  void onlyTheResettingServerWasCleared() {
    assertThat(reset.getStubMappings()).isEmpty();
    assertThat(keep.getStubMappings()).hasSize(1);
  }
}
```

`FileStubsSurviveResetTest.java` (Review Focus 5):

```java
package com.leeturner.wiremock.micronaut;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leeturner.wiremock.micronaut.testsupport.Http;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@EnableWireMock(@ConfigureWireMock(filesUnderClasspath = "classpath-stubs"))
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FileStubsSurviveResetTest {

  @InjectWireMock WireMockServer server;

  @Test
  @Order(1)
  void addAProgrammaticStub() {
    server.stubFor(get("/programmatic").willReturn(ok("p")));
    assertThat(Http.get(server.baseUrl() + "/from-classpath").body()).isEqualTo("classpath");
  }

  @Test
  @Order(2)
  void fileStubSurvivesButProgrammaticStubIsGone() {
    assertThat(Http.get(server.baseUrl() + "/from-classpath").body()).isEqualTo("classpath");
    assertThat(Http.get(server.baseUrl() + "/programmatic").statusCode()).isEqualTo(404);
  }
}
```

`StaticDslWithSeveralServersTest.java`:

```java
package com.leeturner.wiremock.micronaut;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@EnableWireMock({@ConfigureWireMock(name = "a"), @ConfigureWireMock(name = "b")})
class StaticDslWithSeveralServersTest {

  @BeforeAll
  static void pointStaticClientNowhere() {
    // Runs after the extension's beforeAll and before its beforeEach.
    WireMock.configureFor(-1);
  }

  @Test
  void staticClientIsNotPointedAtEitherServer() {
    // beforeEach must leave the static client alone when several servers exist.
    assertThatThrownBy(WireMock::listAllStubMappings).isInstanceOf(Exception.class);
  }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew test --tests 'com.leeturner.wiremock.micronaut.*Test'`

Expected: FAIL. The injected fields are `null` and the parameter isn't
resolved (`No ParameterResolver registered for parameter`).

- [ ] **Step 3: Implement**

`WireMockMicronautExtension.java`:

```java
package com.leeturner.wiremock.micronaut.internal;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.leeturner.wiremock.micronaut.InjectWireMock;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.util.Map;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.junit.platform.commons.support.AnnotationSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Injection, per-test reset, static DSL and cleanup for {@code @EnableWireMock} tests. */
public final class WireMockMicronautExtension
    implements BeforeAllCallback,
        TestInstancePostProcessor,
        BeforeEachCallback,
        AfterEachCallback,
        AfterAllCallback,
        ParameterResolver {

  private static final Logger LOG = LoggerFactory.getLogger(WireMockMicronautExtension.class);
  private static final String MICRONAUT_TEST =
      "io.micronaut.test.extensions.junit5.annotation.MicronautTest";

  @Override
  public void beforeAll(ExtensionContext context) {
    Class<?> testClass = context.getRequiredTestClass();
    ConfigurationResolver.resolve(testClass); // fails fast on invalid or @Nested declarations
    Class<?> root = ConfigurationResolver.rootTestClass(testClass);
    if (root == testClass && !hasMicronautTest(root)) {
      LOG.warn(
          "{} uses WireMock without @MicronautTest: servers run, but their properties are not"
              + " bound into Micronaut",
          root.getName());
    }
    WireMockServers.getOrStart(testClass);
  }

  @Override
  public void postProcessTestInstance(Object testInstance, ExtensionContext context)
      throws IllegalAccessException {
    Class<?> testClass = testInstance.getClass();
    Map<String, RunningServer> servers = WireMockServers.getOrStart(testClass);
    for (Field field : AnnotationSupport.findAnnotatedFields(testClass, InjectWireMock.class)) {
      requireServerType(
          field.getType(), "field '%s' of %s".formatted(field.getName(), testClass.getName()));
      field.setAccessible(true);
      field.set(
          testInstance,
          lookup(servers, field.getAnnotation(InjectWireMock.class).value(), testClass));
    }
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    Class<?> testClass = context.getRequiredTestClass();
    Map<String, RunningServer> servers = WireMockServers.getOrStart(testClass);
    servers.values().stream()
        .filter(running -> running.options().resetWireMockServer())
        .forEach(running -> running.server().resetAll());
    if (servers.size() == 1) {
      WireMockServer server = servers.values().iterator().next().server();
      WireMock.configureFor(
          server.isHttpsEnabled()
              ? WireMock.create().https().host("localhost").port(server.httpsPort()).build()
              : WireMock.create().http().host("localhost").port(server.port()).build());
    } else {
      LOG.debug(
          "{} WireMock servers configured for {}; the static WireMock client is not configured",
          servers.size(),
          testClass.getName());
    }
  }

  @Override
  public void afterEach(ExtensionContext context) {
    WireMock.configureFor(-1);
  }

  @Override
  public void afterAll(ExtensionContext context) {
    Class<?> testClass = context.getRequiredTestClass();
    if (!ConfigurationResolver.isNested(testClass)) {
      WireMockServers.stop(testClass);
    }
  }

  @Override
  public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext context) {
    return parameterContext.isAnnotated(InjectWireMock.class);
  }

  @Override
  public Object resolveParameter(ParameterContext parameterContext, ExtensionContext context) {
    Parameter parameter = parameterContext.getParameter();
    requireServerType(
        parameter.getType(),
        "parameter '%s' of %s"
            .formatted(parameter.getName(), parameterContext.getDeclaringExecutable()));
    Class<?> testClass = context.getRequiredTestClass();
    String name = parameterContext.findAnnotation(InjectWireMock.class).orElseThrow().value();
    return lookup(WireMockServers.getOrStart(testClass), name, testClass);
  }

  private static WireMockServer lookup(
      Map<String, RunningServer> servers, String name, Class<?> testClass) {
    RunningServer running = servers.get(name);
    if (running == null) {
      throw new ExtensionConfigurationException(
          "No WireMock server named '%s' is configured for %s. Configured servers: %s"
              .formatted(
                  name,
                  ConfigurationResolver.rootTestClass(testClass).getName(),
                  servers.keySet()));
    }
    return running.server();
  }

  private static void requireServerType(Class<?> type, String member) {
    if (!type.isAssignableFrom(WireMockServer.class)) {
      throw new ExtensionConfigurationException(
          "@InjectWireMock %s has type %s; only WireMockServer (or a supertype) is supported"
              .formatted(member, type.getName()));
    }
  }

  private static boolean hasMicronautTest(Class<?> root) {
    try {
      Class<? extends Annotation> micronautTest =
          Class.forName(MICRONAUT_TEST, false, root.getClassLoader()).asSubclass(Annotation.class);
      return AnnotationSupport.isAnnotated(root, micronautTest);
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew spotlessApply test`

Expected: BUILD SUCCESSFUL. All Task 1–5 tests pass, including the 4 new
classes (7 new tests).

If `FileStubsSurviveResetTest` fails because `resetAll()` drops file-backed
stubs, replace `running.server().resetAll()` with:

```java
running.server().resetToDefaultMappings();
running.server().resetRequests();
running.server().resetScenarios();
```

Then re-run.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit --no-gpg-sign -m "feat: Inject, reset and clean up WireMock servers via JUnit extension" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Micronaut property binding (TestPropertyProviderFactory)

**Files:**

- Create: `src/main/java/com/leeturner/wiremock/micronaut/internal/WireMockTestPropertyProviderFactory.java`
- Create: `src/main/resources/META-INF/services/io.micronaut.test.support.TestPropertyProviderFactory`
- Create: `src/test/java/com/leeturner/wiremock/micronaut/app/UsersGateway.java`
- Create: `src/test/java/com/leeturner/wiremock/micronaut/app/EagerClient.java`
- Test: `src/test/java/com/leeturner/wiremock/micronaut/MicronautPropertyBindingTest.java`
- Test: `src/test/java/com/leeturner/wiremock/micronaut/DefaultServerMicronautTest.java`
- Test: `src/test/java/com/leeturner/wiremock/micronaut/RebuildContextTest.java`
- Test: `src/test/java/com/leeturner/wiremock/micronaut/UnrelatedMicronautTest.java`
- Test: `src/test/java/com/leeturner/wiremock/micronaut/internal/WireMockTestPropertyProviderFactoryTest.java`
- Create: `src/test/resources/application.properties` (simulates an app's real
  service URL).
- Test: `src/test/java/com/leeturner/wiremock/micronaut/ServiceIdPropertyTest.java`

**Interfaces:**

- Consumes: `ConfigurationResolver.isManaged/rootTestClass`,
  `WireMockServers.getOrStart`, `ServerProperties.of`.
- Produces: `public final class WireMockTestPropertyProviderFactory implements io.micronaut.test.support.TestPropertyProviderFactory`.

- [ ] **Step 1: Write the test app beans**

`app/UsersGateway.java`:

```java
package com.leeturner.wiremock.micronaut.app;

import com.leeturner.wiremock.micronaut.testsupport.Http;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

@Singleton
public class UsersGateway {
  private final String baseUrl;

  public UsersGateway(@Value("${users.url}") String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String fetch(String path) {
    return Http.get(baseUrl + path).body();
  }
}
```

`app/EagerClient.java` (an eager bean that must see its property at
startup):

```java
package com.leeturner.wiremock.micronaut.app;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;

@Context
@Requires(property = "eager.url")
public class EagerClient {
  private final String url;

  public EagerClient(@Value("${eager.url}") String url) {
    this.url = url;
  }

  public String url() {
    return url;
  }
}
```

- [ ] **Step 2: Write the failing tests**

`MicronautPropertyBindingTest.java`:

```java
package com.leeturner.wiremock.micronaut;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leeturner.wiremock.micronaut.app.EagerClient;
import com.leeturner.wiremock.micronaut.app.UsersGateway;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@MicronautTest
@EnableWireMock({
  @ConfigureWireMock(name = "users", baseUrlProperties = "users.url", portProperties = "users.port"),
  @ConfigureWireMock(name = "eager", baseUrlProperties = "eager.url")
})
class MicronautPropertyBindingTest {

  @Inject ApplicationContext context;
  @Inject UsersGateway users;
  @Inject EagerClient eager;

  @InjectWireMock("users")
  WireMockServer usersServer;

  @InjectWireMock("eager")
  WireMockServer eagerServer;

  @Test
  void bindsBaseUrlAndPort() {
    assertThat(context.getRequiredProperty("users.url", String.class))
        .isEqualTo("http://localhost:" + usersServer.port());
    assertThat(context.getRequiredProperty("users.port", Integer.class))
        .isEqualTo(usersServer.port());
  }

  @Test
  void beansTalkToWireMock() {
    usersServer.stubFor(get("/users").willReturn(ok("alice")));
    assertThat(users.fetch("/users")).isEqualTo("alice");
  }

  @Test
  void eagerBeansSeeThePropertyAtStartup() {
    assertThat(eager.url()).isEqualTo("http://localhost:" + eagerServer.port());
  }

  @Test
  void sharedDefaultPropertiesAreNotBound() {
    // only "eager" leaves portProperties at its default, so that one is bound...
    assertThat(context.getProperty("wiremock.server.port", Integer.class))
        .contains(eagerServer.port());
    // ...while both leave the default HTTPS base URL, and neither has HTTPS anyway
    assertThat(context.getProperty("wiremock.server.httpsBaseUrl", String.class)).isEmpty();
  }
}
```

`DefaultServerMicronautTest.java`:

```java
package com.leeturner.wiremock.micronaut;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leeturner.wiremock.micronaut.testsupport.Http;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@MicronautTest
@EnableWireMock
class DefaultServerMicronautTest {

  @Inject ApplicationContext context;

  @InjectWireMock WireMockServer wiremock;

  @Test
  void defaultServerBindsTheDefaultProperties() {
    assertThat(context.getRequiredProperty("wiremock.server.baseUrl", String.class))
        .isEqualTo("http://localhost:" + wiremock.port());
    assertThat(context.getRequiredProperty("wiremock.server.port", Integer.class))
        .isEqualTo(wiremock.port());
  }

  @Test
  void staticDslWorksInsideMicronautTests() {
    stubFor(get("/hello").willReturn(ok("hi")));
    assertThat(Http.get(wiremock.baseUrl() + "/hello").body()).isEqualTo("hi");
  }
}
```

`RebuildContextTest.java`:

```java
package com.leeturner.wiremock.micronaut;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@MicronautTest(rebuildContext = true)
@EnableWireMock(@ConfigureWireMock(name = "users", baseUrlProperties = "users.url"))
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RebuildContextTest {
  static final List<String> SEEN = new CopyOnWriteArrayList<>();

  @Inject ApplicationContext context;

  @Test
  @Order(1)
  void first() {
    SEEN.add(context.getRequiredProperty("users.url", String.class));
  }

  @Test
  @Order(2)
  void rebuiltContextKeepsTheSameServer() {
    SEEN.add(context.getRequiredProperty("users.url", String.class));
    assertThat(SEEN).hasSize(2);
    assertThat(SEEN.get(1)).isEqualTo(SEEN.get(0));
  }
}
```

`src/test/resources/application.properties` (an app's real service
configuration, as used by `@Client("setlist-fm")`):

```properties
micronaut.http.services.setlist-fm.url=https://api.setlist.fm
```

`ServiceIdPropertyTest.java` (Review Focus 6). The library build has no HTTP
client module, so this pins the configuration override. The full
`@Client("setlist-fm")` round trip is tested in the Task 9 examples.

```java
package com.leeturner.wiremock.micronaut;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@MicronautTest
@EnableWireMock(
    @ConfigureWireMock(
        name = "setlist-fm",
        baseUrlProperties = "micronaut.http.services.setlist-fm.url"))
class ServiceIdPropertyTest {

  @Inject ApplicationContext context;

  @InjectWireMock("setlist-fm")
  WireMockServer setlistFm;

  @Test
  void wireMockOverridesTheServiceUrlFromApplicationConfiguration() {
    assertThat(context.getRequiredProperty("micronaut.http.services.setlist-fm.url", String.class))
        .isEqualTo("http://localhost:" + setlistFm.port());
  }
}
```

`UnrelatedMicronautTest.java` (Review Focus 1):

```java
package com.leeturner.wiremock.micronaut;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@MicronautTest
class UnrelatedMicronautTest {
  @Inject ApplicationContext context;

  @Test
  void contextHasNoWireMockProperties() {
    assertThat(context.getProperty("wiremock.server.baseUrl", String.class)).isEmpty();
    // the app's real service URL is untouched when WireMock isn't in use
    assertThat(context.getRequiredProperty("micronaut.http.services.setlist-fm.url", String.class))
        .isEqualTo("https://api.setlist.fm");
  }
}
```

`internal/WireMockTestPropertyProviderFactoryTest.java` (Review Focus 1):

```java
package com.leeturner.wiremock.micronaut.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WireMockTestPropertyProviderFactoryTest {
  static class Plain {}

  @Test
  void unmanagedClassesGetNoPropertiesAndNoServers() {
    var provider = new WireMockTestPropertyProviderFactory().create(Map.of(), Plain.class);
    assertThat(provider.getProperties()).isEmpty();
    assertThat(WireMockServers.runningRootTestClasses()).doesNotContain(Plain.class.getName());
  }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew test --tests 'com.leeturner.wiremock.micronaut.*'`

Expected: FAIL.
- `WireMockTestPropertyProviderFactoryTest` does not compile (missing
  class).
- If you comment it out temporarily, the Micronaut tests fail with
  `Could not resolve placeholder ${users.url}` or a missing-property
  assertion.

- [ ] **Step 4: Implement**

`WireMockTestPropertyProviderFactory.java`:

```java
package com.leeturner.wiremock.micronaut.internal;

import io.micronaut.test.support.TestPropertyProvider;
import io.micronaut.test.support.TestPropertyProviderFactory;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Called by micronaut-test while it builds the application context: starts the test class's
 * WireMock servers and returns their properties, so beans see them on creation.
 */
public final class WireMockTestPropertyProviderFactory implements TestPropertyProviderFactory {
  private static final Logger LOG =
      LoggerFactory.getLogger(WireMockTestPropertyProviderFactory.class);

  @Override
  public TestPropertyProvider create(Map<String, Object> availableProperties, Class<?> testClass) {
    if (!ConfigurationResolver.isManaged(testClass)) {
      return Map::of;
    }
    Class<?> root = ConfigurationResolver.rootTestClass(testClass);
    Map<String, String> properties =
        ServerProperties.of(root.getName(), WireMockServers.getOrStart(testClass).values());
    LOG.info("Binding WireMock properties for {}: {}", root.getSimpleName(), properties);
    return () -> properties;
  }
}
```

`src/main/resources/META-INF/services/io.micronaut.test.support.TestPropertyProviderFactory`:

```
com.leeturner.wiremock.micronaut.internal.WireMockTestPropertyProviderFactory
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew spotlessApply test`

Expected: BUILD SUCCESSFUL. All tests pass, including 6 new classes
(10 new tests).

- [ ] **Step 6: Commit**

```bash
git add -A
git commit --no-gpg-sign -m "feat: Bind WireMock properties into Micronaut test contexts" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: registerBean

**Files:**

- Create: `src/main/java/com/leeturner/wiremock/micronaut/internal/RegisteredWireMockServer.java`
- Create: `src/main/java/com/leeturner/wiremock/micronaut/internal/WireMockServerBeanFactory.java`
- Modify: `src/main/java/com/leeturner/wiremock/micronaut/InjectWireMock.java`
  (add `@jakarta.inject.Qualifier`).
- Create: `src/test/java/com/leeturner/wiremock/micronaut/app/EagerWireMockHolder.java`
- Test: `src/test/java/com/leeturner/wiremock/micronaut/RegisterBeanTest.java`

**Interfaces:**

- Consumes: `ServerProperties.REGISTRY_KEY_PROPERTY` (format
  `wiremock.micronaut.servers.<name>.registry-key`) and
  `WireMockServers.get(String, String)`.
- Produces: `WireMockServer` beans qualified `@Named("<name>")` for every
  server with `registerBean = true`.

- [ ] **Step 1: Write the failing test**

`app/EagerWireMockHolder.java`:

```java
package com.leeturner.wiremock.micronaut.app;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Named;

@Context
@Requires(property = "wiremock.micronaut.servers.users.registry-key")
public class EagerWireMockHolder {
  private final WireMockServer server;

  public EagerWireMockHolder(@Named("users") WireMockServer server) {
    this.server = server;
  }

  public WireMockServer server() {
    return server;
  }
}
```

`RegisterBeanTest.java`:

```java
package com.leeturner.wiremock.micronaut;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leeturner.wiremock.micronaut.app.EagerWireMockHolder;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

@MicronautTest
@EnableWireMock(
    @ConfigureWireMock(name = "users", registerBean = true, baseUrlProperties = "users.url"))
class RegisterBeanTest {

  @Inject
  @Named("users")
  WireMockServer bean;

  @Inject EagerWireMockHolder eager;

  @InjectWireMock("users")
  WireMockServer injected;

  @Test
  void beanIsTheRunningServer() {
    assertThat(bean).isSameAs(injected);
    assertThat(bean.isRunning()).isTrue();
  }

  @Test
  void eagerSingletonsReceiveTheServer() {
    assertThat(eager.server()).isSameAs(injected);
  }

  @Test
  void injectWireMockParameterDoesNotCompeteWithMicronaut(
      @InjectWireMock("users") WireMockServer parameter) {
    assertThat(parameter).isSameAs(injected);
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests 'com.leeturner.wiremock.micronaut.RegisterBeanTest'`

Expected: FAIL with `NoSuchBeanException` for
`WireMockServer` / `@Named("users")`.

- [ ] **Step 3: Implement**

`RegisteredWireMockServer.java`:

```java
package com.leeturner.wiremock.micronaut.internal;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;

/** One entry per {@code registerBean} server, driven by {@link ServerProperties}. */
@EachProperty("wiremock.micronaut.servers")
public final class RegisteredWireMockServer {
  private final String name;
  private String registryKey = "";

  public RegisteredWireMockServer(@Parameter String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public String getRegistryKey() {
    return registryKey;
  }

  public void setRegistryKey(String registryKey) {
    this.registryKey = registryKey;
  }
}
```

`WireMockServerBeanFactory.java`:

```java
package com.leeturner.wiremock.micronaut.internal;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;

/** Exposes {@code registerBean} servers as {@code @Named} Micronaut beans. */
@Factory
public final class WireMockServerBeanFactory {

  @EachBean(RegisteredWireMockServer.class)
  public WireMockServer wireMockServer(RegisteredWireMockServer registration) {
    return WireMockServers.get(registration.getRegistryKey(), registration.getName());
  }
}
```

Modify `InjectWireMock.java`: add the import and the meta-annotation.
Micronaut then derives a qualifier from `@InjectWireMock` on test-method
parameters, finds no bean qualified by it (the beans are `@Named`), and
leaves the parameter to our resolver.

```java
import jakarta.inject.Qualifier;
// ...
@Qualifier
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface InjectWireMock {
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew spotlessApply test`

Expected: BUILD SUCCESSFUL, with `RegisterBeanTest` passing (3 tests) and
every earlier test still green.

**Fallback.** Use this only if
`injectWireMockParameterDoesNotCompeteWithMicronaut` fails with
"competing ParameterResolvers", or if the `@Qualifier` breaks compilation of
test classes.

1. Remove `@Qualifier` from `InjectWireMock`.
2. Make `supportsParameter` return `false` when the named server has
   `registerBean = true`:

   ```java
   @Override
   public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext context) {
     return parameterContext
         .findAnnotation(InjectWireMock.class)
         .map(inject -> {
           RunningServer running =
               WireMockServers.getOrStart(context.getRequiredTestClass()).get(inject.value());
           return running == null || !running.options().registerBean();
         })
         .orElse(false);
   }
   ```

3. Change that test to use
   `@Named("users") WireMockServer parameter` (Micronaut resolves it).
4. Add a README note (Task 10): "for `registerBean` servers, inject method
   parameters with `@Named`".
5. Re-run.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit --no-gpg-sign -m "feat: Register WireMock servers as named Micronaut beans" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Failure modes and lifecycle edge cases (EngineTestKit)

**Files:**

- Create: `src/test/java/com/leeturner/wiremock/micronaut/testsupport/FixtureRunner.java`
- Create (fixtures, all in `src/test/java/com/leeturner/wiremock/micronaut/fixtures/`):
  - `UnknownServerFixture.java`
  - `WrongTypeFixture.java`
  - `DuplicateNameFixture.java`
  - `PropertyClashFixture.java`
  - `NestedDeclarationFixture.java`
  - `NoNoArgConstructorFixture.java`
  - `MissingDirectoryFixture.java`
  - `StartFailureFixture.java`
  - `ContextFailureFixture.java` and `Boom.java`
  - `PerClassFixture.java`
  - `ParallelFixtureA.java` and `ParallelFixtureB.java`
- Test: `src/test/java/com/leeturner/wiremock/micronaut/FailureModesTest.java`
- Test: `src/test/java/com/leeturner/wiremock/micronaut/LifecycleTest.java`

**Interfaces:**

- Consumes: everything above, plus
  `WireMockServers.runningRootTestClasses()`.
- Produces: `FixtureRunner.run(Class<?>...)`,
  `FixtureRunner.runInParallel(Class<?>...)` and
  `FixtureRunner.failureMessages(Class<?>)`.

- [ ] **Step 1: Write the runner and fixtures**

`testsupport/FixtureRunner.java`:

```java
package com.leeturner.wiremock.micronaut.testsupport;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;

public final class FixtureRunner {
  private FixtureRunner() {}

  public static EngineExecutionResults run(Class<?>... fixtures) {
    return EngineTestKit.engine("junit-jupiter").selectors(selectors(fixtures)).execute();
  }

  public static EngineExecutionResults runInParallel(Class<?>... fixtures) {
    return EngineTestKit.engine("junit-jupiter")
        .configurationParameter("junit.jupiter.execution.parallel.enabled", "true")
        .configurationParameter("junit.jupiter.execution.parallel.mode.classes.default", "concurrent")
        .selectors(selectors(fixtures))
        .execute();
  }

  /** All messages in the cause chain of the first failure, joined; fails if nothing failed. */
  public static String failureMessages(Class<?> fixture) {
    Throwable failure =
        run(fixture).allEvents().failed().stream()
            .map(e -> e.getRequiredPayload(TestExecutionResult.class).getThrowable())
            .flatMap(java.util.Optional::stream)
            .findFirst()
            .orElseThrow(() -> new AssertionError(fixture.getSimpleName() + " did not fail"));
    return Stream.iterate(failure, t -> t != null, Throwable::getCause)
        .map(t -> t.getClass().getSimpleName() + ": " + t.getMessage())
        .collect(Collectors.joining(" <- "));
  }

  private static DiscoverySelector[] selectors(Class<?>... fixtures) {
    return Arrays.stream(fixtures).map(c -> selectClass(c)).toArray(DiscoverySelector[]::new);
  }
}
```

Fixtures (each a top-level class in package
`com.leeturner.wiremock.micronaut.fixtures`; the Gradle `test` task
excludes them, so they only run through `FixtureRunner`).

`UnknownServerFixture.java`:

```java
package com.leeturner.wiremock.micronaut.fixtures;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leeturner.wiremock.micronaut.EnableWireMock;
import com.leeturner.wiremock.micronaut.InjectWireMock;
import org.junit.jupiter.api.Test;

@EnableWireMock
public class UnknownServerFixture {
  @InjectWireMock("nope")
  WireMockServer server;

  @Test
  void test() {}
}
```

`WrongTypeFixture.java`:

```java
package com.leeturner.wiremock.micronaut.fixtures;

import com.leeturner.wiremock.micronaut.EnableWireMock;
import com.leeturner.wiremock.micronaut.InjectWireMock;
import org.junit.jupiter.api.Test;

@EnableWireMock
public class WrongTypeFixture {
  @InjectWireMock String server;

  @Test
  void test() {}
}
```

`DuplicateNameFixture.java`:

```java
package com.leeturner.wiremock.micronaut.fixtures;

import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import com.leeturner.wiremock.micronaut.EnableWireMock;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

@MicronautTest
@EnableWireMock({@ConfigureWireMock(name = "a"), @ConfigureWireMock(name = "a")})
public class DuplicateNameFixture {
  @Test
  void test() {}
}
```

`PropertyClashFixture.java`:

```java
package com.leeturner.wiremock.micronaut.fixtures;

import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import com.leeturner.wiremock.micronaut.EnableWireMock;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

@MicronautTest
@EnableWireMock({
  @ConfigureWireMock(name = "a", baseUrlProperties = "x.url"),
  @ConfigureWireMock(name = "b", baseUrlProperties = "x.url")
})
public class PropertyClashFixture {
  @Test
  void test() {}
}
```

`NestedDeclarationFixture.java`:

```java
package com.leeturner.wiremock.micronaut.fixtures;

import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import com.leeturner.wiremock.micronaut.EnableWireMock;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@EnableWireMock
public class NestedDeclarationFixture {
  @Nested
  @ConfigureWireMock(name = "x")
  class Inner {
    @Test
    void test() {}
  }
}
```

`NoNoArgConstructorFixture.java`:

```java
package com.leeturner.wiremock.micronaut.fixtures;

import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import com.leeturner.wiremock.micronaut.EnableWireMock;
import org.junit.jupiter.api.Test;

@EnableWireMock(@ConfigureWireMock(name = "ext", extensions = NoNoArgConstructorFixture.NeedsArgs.class))
public class NoNoArgConstructorFixture {
  public static class NeedsArgs implements ResponseTransformerV2 {
    public NeedsArgs(String unused) {}

    @Override
    public Response transform(Response response, ServeEvent serveEvent) {
      return response;
    }

    @Override
    public String getName() {
      return "needs-args";
    }
  }

  @Test
  void test() {}
}
```

`MissingDirectoryFixture.java`:

```java
package com.leeturner.wiremock.micronaut.fixtures;

import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import com.leeturner.wiremock.micronaut.EnableWireMock;
import org.junit.jupiter.api.Test;

@EnableWireMock(@ConfigureWireMock(name = "files", filesUnderDirectory = "does-not-exist"))
public class MissingDirectoryFixture {
  @Test
  void test() {}
}
```

`StartFailureFixture.java` (use the same keystore settings that made Task 3
Step 5 fail):

```java
package com.leeturner.wiremock.micronaut.fixtures;

import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import com.leeturner.wiremock.micronaut.EnableWireMock;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

@MicronautTest
@EnableWireMock({
  @ConfigureWireMock(name = "ok"),
  @ConfigureWireMock(name = "bad", port = -1, httpsPort = 0, keystorePath = "does-not-exist.jks")
})
public class StartFailureFixture {
  @Test
  void test() {}
}
```

`Boom.java` and `ContextFailureFixture.java` (Review Focus 3):

```java
package com.leeturner.wiremock.micronaut.fixtures;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;

@Context
@Requires(property = "boom.enabled", value = "true")
public class Boom {
  public Boom() {
    throw new IllegalStateException("boom");
  }
}
```

```java
package com.leeturner.wiremock.micronaut.fixtures;

import com.leeturner.wiremock.micronaut.EnableWireMock;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

@MicronautTest
@Property(name = "boom.enabled", value = "true")
@EnableWireMock
public class ContextFailureFixture {
  @Test
  void test() {}
}
```

`PerClassFixture.java`:

```java
package com.leeturner.wiremock.micronaut.fixtures;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import com.leeturner.wiremock.micronaut.EnableWireMock;
import com.leeturner.wiremock.micronaut.InjectWireMock;
import io.micronaut.context.annotation.Value;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnableWireMock(@ConfigureWireMock(name = "users", baseUrlProperties = "users.url"))
public class PerClassFixture {
  @InjectWireMock("users")
  WireMockServer users;

  @Value("${users.url}")
  String usersUrl;

  @Test
  void fieldAndPropertyAgree() {
    assertThat(usersUrl).isEqualTo("http://localhost:" + users.port());
  }
}
```

`ParallelFixtureA.java` (and `ParallelFixtureB.java`, identical apart from
the class name and server name `"b"`):

```java
package com.leeturner.wiremock.micronaut.fixtures;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import com.leeturner.wiremock.micronaut.EnableWireMock;
import com.leeturner.wiremock.micronaut.InjectWireMock;
import com.leeturner.wiremock.micronaut.testsupport.Http;
import org.junit.jupiter.api.RepeatedTest;

@EnableWireMock(@ConfigureWireMock(name = "a"))
public class ParallelFixtureA {
  @InjectWireMock("a")
  WireMockServer server;

  @RepeatedTest(5)
  void servesItsOwnStub() {
    server.stubFor(get("/who").willReturn(ok("a")));
    assertThat(Http.get(server.baseUrl() + "/who").body()).isEqualTo("a");
  }
}
```

- [ ] **Step 2: Write the tests**

`FailureModesTest.java`:

```java
package com.leeturner.wiremock.micronaut;

import static com.leeturner.wiremock.micronaut.testsupport.FixtureRunner.failureMessages;
import static org.assertj.core.api.Assertions.assertThat;

import com.leeturner.wiremock.micronaut.fixtures.ContextFailureFixture;
import com.leeturner.wiremock.micronaut.fixtures.DuplicateNameFixture;
import com.leeturner.wiremock.micronaut.fixtures.MissingDirectoryFixture;
import com.leeturner.wiremock.micronaut.fixtures.NestedDeclarationFixture;
import com.leeturner.wiremock.micronaut.fixtures.NoNoArgConstructorFixture;
import com.leeturner.wiremock.micronaut.fixtures.PropertyClashFixture;
import com.leeturner.wiremock.micronaut.fixtures.StartFailureFixture;
import com.leeturner.wiremock.micronaut.fixtures.UnknownServerFixture;
import com.leeturner.wiremock.micronaut.fixtures.WrongTypeFixture;
import com.leeturner.wiremock.micronaut.internal.WireMockServers;
import org.junit.jupiter.api.Test;

class FailureModesTest {

  @Test
  void unknownServerName() {
    assertThat(failureMessages(UnknownServerFixture.class))
        .contains("No WireMock server named 'nope'")
        .contains("Configured servers: [wiremock]");
  }

  @Test
  void wrongInjectionType() {
    assertThat(failureMessages(WrongTypeFixture.class))
        .contains("field 'server'")
        .contains("java.lang.String")
        .contains("only WireMockServer");
  }

  @Test
  void duplicateNamesInAMicronautTest() {
    assertThat(failureMessages(DuplicateNameFixture.class))
        .contains("Duplicate WireMock server name(s) [a]");
  }

  @Test
  void explicitPropertyClash() {
    assertThat(failureMessages(PropertyClashFixture.class))
        .contains("Property 'x.url' is bound by more than one WireMock server");
  }

  @Test
  void declarationOnNestedClass() {
    assertThat(failureMessages(NestedDeclarationFixture.class))
        .contains("@ConfigureWireMock cannot be declared on the @Nested class")
        .contains("Move it to NestedDeclarationFixture");
  }

  @Test
  void extensionWithoutNoArgConstructor() {
    assertThat(failureMessages(NoNoArgConstructorFixture.class))
        .contains("public no-arg constructor")
        .contains("'ext'");
  }

  @Test
  void missingStubDirectory() {
    assertThat(failureMessages(MissingDirectoryFixture.class))
        .contains("None of filesUnderDirectory [does-not-exist]");
  }

  @Test
  void serverStartFailureLeavesNothingRunning() {
    assertThat(failureMessages(StartFailureFixture.class)).contains("'bad'");
    assertThat(WireMockServers.runningRootTestClasses())
        .doesNotContain(StartFailureFixture.class.getName());
  }

  @Test
  void contextStartupFailureStillStopsServers() {
    assertThat(failureMessages(ContextFailureFixture.class)).contains("boom");
    assertThat(WireMockServers.runningRootTestClasses())
        .doesNotContain(ContextFailureFixture.class.getName());
  }
}
```

`LifecycleTest.java`:

```java
package com.leeturner.wiremock.micronaut;

import static com.leeturner.wiremock.micronaut.testsupport.FixtureRunner.run;
import static com.leeturner.wiremock.micronaut.testsupport.FixtureRunner.runInParallel;
import static org.assertj.core.api.Assertions.assertThat;

import com.leeturner.wiremock.micronaut.fixtures.ParallelFixtureA;
import com.leeturner.wiremock.micronaut.fixtures.ParallelFixtureB;
import com.leeturner.wiremock.micronaut.fixtures.PerClassFixture;
import com.leeturner.wiremock.micronaut.internal.WireMockServers;
import org.junit.jupiter.api.Test;

class LifecycleTest {

  @Test
  void perClassLifecycleWorks() {
    run(PerClassFixture.class).testEvents().assertStatistics(s -> s.succeeded(1).failed(0));
  }

  @Test
  void parallelClassesGetTheirOwnServers() {
    runInParallel(ParallelFixtureA.class, ParallelFixtureB.class)
        .testEvents()
        .assertStatistics(s -> s.succeeded(10).failed(0));
  }

  @Test
  void serversAreStoppedAfterTheClass() {
    run(PerClassFixture.class);
    assertThat(WireMockServers.runningRootTestClasses())
        .doesNotContain(PerClassFixture.class.getName());
  }
}
```

- [ ] **Step 3: Run the tests**

Run: `./gradlew spotlessApply test --tests 'com.leeturner.wiremock.micronaut.FailureModesTest' --tests 'com.leeturner.wiremock.micronaut.LifecycleTest'`

Expected: PASS (12 tests).

These tests pin behaviour already implemented in Tasks 2–7. Any failure here
is a real bug: fix the production code, not the assertion, unless the
assertion contradicts the spec. To see the full chain when a message
assertion fails, temporarily print `failureMessages(...)`.

- [ ] **Step 4: Run the full build**

Run: `./gradlew build`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit --no-gpg-sign -m "test: Pin failure messages and lifecycle edge cases" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Consumer smoke-test examples (Java and Kotlin)

**Files:**

- Modify: `settings.gradle.kts`
- Create: `examples/java/build.gradle.kts`
- Create: `examples/java/src/main/java/example/UsersClient.java` (URL-placeholder style)
- Create: `examples/java/src/main/java/example/SetlistFmClient.java` (service-id style)
- Create: `examples/java/src/main/resources/application.properties`
- Create: `examples/java/src/test/java/example/UsersClientTest.java`
- Create: `examples/java/src/test/java/example/SetlistFmClientTest.java`
- Create: `examples/kotlin/build.gradle.kts`
- Create: `examples/kotlin/src/main/kotlin/example/UsersClient.kt`
- Create: `examples/kotlin/src/main/kotlin/example/SetlistFmClient.kt`
- Create: `examples/kotlin/src/main/resources/application.properties`
- Create: `examples/kotlin/src/test/kotlin/example/UsersClientTest.kt`
- Create: `examples/kotlin/src/test/kotlin/example/SetlistFmClientTest.kt`

**Interfaces:**

- Consumes: the library as `project(":")`. These projects use the Micronaut
  Gradle plugin and its 5.x platform BOM, the way a real app does.

- [ ] **Step 1: Wire the subprojects**

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "wiremock-micronaut"

include("examples:java", "examples:kotlin")
```

`examples/java/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.micronaut.library)
}

repositories { mavenCentral() }

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

micronaut {
    version(providers.gradleProperty("micronautVersion").getOrElse(libs.versions.micronaut.get()))
    testRuntime("junit5")
}

dependencies {
    implementation("io.micronaut:micronaut-http-client-jdk")
    runtimeOnly("ch.qos.logback:logback-classic")
    testImplementation(project(":"))
    testImplementation("org.assertj:assertj-core")
}
```

`examples/kotlin/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.allopen)
    alias(libs.plugins.ksp)
    alias(libs.plugins.micronaut.library)
}

repositories { mavenCentral() }

kotlin { jvmToolchain(25) }

micronaut {
    version(providers.gradleProperty("micronautVersion").getOrElse(libs.versions.micronaut.get()))
    testRuntime("junit5")
}

dependencies {
    ksp("io.micronaut:micronaut-inject-kotlin")
    kspTest("io.micronaut:micronaut-inject-kotlin")
    implementation("io.micronaut:micronaut-http-client-jdk")
    runtimeOnly("ch.qos.logback:logback-classic")
    testImplementation(project(":"))
    testImplementation("org.assertj:assertj-core")
}
```

- [ ] **Step 2: Write the Java examples and their tests**

`UsersClient` uses the URL-placeholder style and `SetlistFmClient` the
service-id style. Both must work.

`examples/java/src/main/java/example/UsersClient.java`:

```java
package example;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;

@Client("${users.url}")
public interface UsersClient {
  @Get(value = "/users/{id}", consumes = MediaType.TEXT_PLAIN)
  String user(String id);
}
```

`examples/java/src/test/java/example/UsersClientTest.java`:

```java
package example;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonSchema;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
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
import org.junit.jupiter.api.Test;

@MicronautTest
@EnableWireMock(@ConfigureWireMock(name = "users", baseUrlProperties = "users.url"))
class UsersClientTest {

  @Inject UsersClient client;

  @InjectWireMock("users")
  WireMockServer users;

  @Test
  void fetchesAUserThroughTheDeclarativeClient() {
    users.stubFor(
        get("/users/1").willReturn(ok("alice").withHeader("Content-Type", "text/plain")));
    assertThat(client.user("1")).isEqualTo("alice");
  }

  @Test
  void jsonSchemaMatchingWorksUnderTheMicronautBom() throws Exception {
    users.stubFor(
        post("/schema")
            .withRequestBody(matchingJsonSchema("{\"type\":\"object\",\"required\":[\"name\"]}"))
            .willReturn(ok("valid")));
    HttpResponse<String> response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create(users.baseUrl() + "/schema"))
                    .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"lee\"}"))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
    assertThat(response.body()).isEqualTo("valid");
  }
}
```

`examples/java/src/main/resources/application.properties` (the real API, as
in a production app):

```properties
micronaut.http.services.setlist-fm.url=https://api.setlist.fm
```

`examples/java/src/main/java/example/SetlistFmClient.java` (service-id
style: the URL comes from `micronaut.http.services.setlist-fm.url`):

```java
package example;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;

@Client("setlist-fm")
public interface SetlistFmClient {
  @Get(value = "/rest/1.0/artist/{mbid}", consumes = MediaType.TEXT_PLAIN)
  String artist(String mbid);
}
```

`examples/java/src/test/java/example/SetlistFmClientTest.java`:

```java
package example;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import com.leeturner.wiremock.micronaut.EnableWireMock;
import com.leeturner.wiremock.micronaut.InjectWireMock;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@MicronautTest
@EnableWireMock(
    @ConfigureWireMock(
        name = "setlist-fm",
        baseUrlProperties = "micronaut.http.services.setlist-fm.url"))
class SetlistFmClientTest {

  @Inject SetlistFmClient client;

  @InjectWireMock("setlist-fm")
  WireMockServer setlistFm;

  @Test
  void serviceIdClientIsRedirectedToWireMock() {
    setlistFm.stubFor(
        get("/rest/1.0/artist/abc")
            .willReturn(ok("Radiohead").withHeader("Content-Type", "text/plain")));

    assertThat(client.artist("abc")).isEqualTo("Radiohead");
    setlistFm.verify(getRequestedFor(urlEqualTo("/rest/1.0/artist/abc")));
  }
}
```

- [ ] **Step 3: Write the Kotlin example and its tests**

`examples/kotlin/src/main/kotlin/example/UsersClient.kt`:

```kotlin
package example

import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client

@Client("\${users.url}")
interface UsersClient {
    @Get(value = "/users/{id}", consumes = [MediaType.TEXT_PLAIN])
    fun user(id: String): String
}
```

`examples/kotlin/src/test/kotlin/example/UsersClientTest.kt`:

```kotlin
package example

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.leeturner.wiremock.micronaut.ConfigureWireMock
import com.leeturner.wiremock.micronaut.EnableWireMock
import com.leeturner.wiremock.micronaut.InjectWireMock
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MicronautTest
@EnableWireMock(ConfigureWireMock(name = "users", baseUrlProperties = ["users.url"]))
class UsersClientTest {

    @Inject
    lateinit var client: UsersClient

    @InjectWireMock("users")
    lateinit var users: WireMockServer

    @Test
    fun `fetches a user through the declarative client`() {
        users.stubFor(get("/users/1").willReturn(ok("alice").withHeader("Content-Type", "text/plain")))
        assertThat(client.user("1")).isEqualTo("alice")
    }

    @Test
    fun `parameter injection works from Kotlin`(@InjectWireMock("users") server: WireMockServer) {
        assertThat(server).isSameAs(users)
    }
}
```

`examples/kotlin/src/main/resources/application.properties`:

```properties
micronaut.http.services.setlist-fm.url=https://api.setlist.fm
```

`examples/kotlin/src/main/kotlin/example/SetlistFmClient.kt`:

```kotlin
package example

import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client

@Client("setlist-fm")
interface SetlistFmClient {
    @Get(value = "/rest/1.0/artist/{mbid}", consumes = [MediaType.TEXT_PLAIN])
    fun artist(mbid: String): String
}
```

`examples/kotlin/src/test/kotlin/example/SetlistFmClientTest.kt`:

```kotlin
package example

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.leeturner.wiremock.micronaut.ConfigureWireMock
import com.leeturner.wiremock.micronaut.EnableWireMock
import com.leeturner.wiremock.micronaut.InjectWireMock
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MicronautTest
@EnableWireMock(
    ConfigureWireMock(
        name = "setlist-fm",
        baseUrlProperties = ["micronaut.http.services.setlist-fm.url"],
    ),
)
class SetlistFmClientTest {

    @Inject
    lateinit var client: SetlistFmClient

    @InjectWireMock("setlist-fm")
    lateinit var setlistFm: WireMockServer

    @Test
    fun `service id client is redirected to WireMock`() {
        setlistFm.stubFor(
            get("/rest/1.0/artist/abc").willReturn(ok("Radiohead").withHeader("Content-Type", "text/plain")),
        )

        assertThat(client.artist("abc")).isEqualTo("Radiohead")
        setlistFm.verify(getRequestedFor(urlEqualTo("/rest/1.0/artist/abc")))
    }
}
```

- [ ] **Step 4: Run the examples**

Run: `./gradlew spotlessApply :examples:java:test :examples:kotlin:test`

Expected: BUILD SUCCESSFUL, with 3 tests passing in each example (2 in
`UsersClientTest`, 1 in `SetlistFmClientTest`).

If `SetlistFmClientTest` reaches `api.setlist.fm` or fails to resolve the
client, that's a real bug in property precedence or timing. Debug it with
superpowers:systematic-debugging. Don't work around it in the test.

If the Kotlin `@InjectWireMock` on `lateinit var` is applied to the property
instead of the field, write `@field:InjectWireMock("users")` and add a
README note (Task 10).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit --no-gpg-sign -m "test: Add Java and Kotlin examples for URL and service-id clients" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: CI, publishing and README

**Files:**

- Modify: `build.gradle.kts` (publishing).
- Create: `.github/workflows/build.yml`, `.github/workflows/weekly.yml`,
  `.github/workflows/release.yml`, `.github/dependabot.yml`.
- Create: `LICENSE`, `README.md`.

**Interfaces:**

- Consumes: the whole library.
- Produces: `./gradlew publishToMavenLocal` and CI workflows.

- [ ] **Step 1: Add publishing to `build.gradle.kts`**

Add `alias(libs.plugins.maven.publish)` to the `plugins` block, then append:

```kotlin
mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
    coordinates("com.leeturner", "wiremock-micronaut", version.toString())
    pom {
        name = "WireMock Micronaut"
        description = "WireMock integration for Micronaut tests"
        url = "https://github.com/leeturner/wiremock-micronaut"
        inceptionYear = "2026"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "leeturner"
                name = "Lee Turner"
                url = "https://leeturner.me"
            }
        }
        scm {
            url = "https://github.com/leeturner/wiremock-micronaut"
            connection = "scm:git:git://github.com/leeturner/wiremock-micronaut.git"
            developerConnection = "scm:git:ssh://git@github.com/leeturner/wiremock-micronaut.git"
        }
    }
}
```

- [ ] **Step 2: Verify the publication**

Run: `./gradlew publishToMavenLocal -PRELEASE_SIGNING_ENABLED=false`

(If the plugin rejects that property, run `./gradlew publishToMavenLocal`
with `signAllPublications()` temporarily commented out, and restore it
afterwards.)

Expected: BUILD SUCCESSFUL. Then check the published POM:

```bash
cat ~/.m2/repository/com/leeturner/wiremock-micronaut/0.1.0-SNAPSHOT/wiremock-micronaut-0.1.0-SNAPSHOT.pom | grep -A3 '<dependency>'
```

Expected: exactly one dependency, `org.wiremock:wiremock-standalone`,
scope `compile`. No Micronaut, JUnit or SLF4J dependencies.

- [ ] **Step 3: Add the workflows**

`.github/workflows/build.yml`:

```yaml
name: Build
on:
  push:
    branches: [main]
  pull_request:
permissions:
  contents: read
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '25'
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew build
```

`.github/workflows/weekly.yml`:

```yaml
name: Latest Micronaut 5.x
on:
  schedule:
    - cron: '0 6 * * 1'
  workflow_dispatch:
permissions:
  contents: read
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '25'
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew build -PmicronautVersion=5.+ --refresh-dependencies
```

`.github/workflows/release.yml`:

```yaml
name: Release
on:
  push:
    tags: ['v*']
permissions:
  contents: read
jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '25'
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew build publishToMavenCentral -Pversion="${GITHUB_REF_NAME#v}"
        env:
          ORG_GRADLE_PROJECT_mavenCentralUsername: ${{ secrets.MAVEN_CENTRAL_USERNAME }}
          ORG_GRADLE_PROJECT_mavenCentralPassword: ${{ secrets.MAVEN_CENTRAL_PASSWORD }}
          ORG_GRADLE_PROJECT_signingInMemoryKey: ${{ secrets.SIGNING_KEY }}
          ORG_GRADLE_PROJECT_signingInMemoryKeyPassword: ${{ secrets.SIGNING_PASSWORD }}
```

`.github/dependabot.yml`:

```yaml
version: 2
updates:
  - package-ecosystem: gradle
    directory: /
    schedule:
      interval: weekly
  - package-ecosystem: github-actions
    directory: /
    schedule:
      interval: weekly
```

- [ ] **Step 4: Add the LICENSE and README**

```bash
curl -sL https://www.apache.org/licenses/LICENSE-2.0.txt -o LICENSE
```

`README.md` (if Task 7 or Task 9 used a fallback, add its note under
"Gotchas"):

````markdown
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
````

- [ ] **Step 5: Run the full build**

Run: `./gradlew spotlessCheck build`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit --no-gpg-sign -m "build: Add CI, Maven Central publishing and README" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

Creating the GitHub repo, adding the secrets and tagging `v0.1.0` are
human steps, done after review. Do not push or tag.
