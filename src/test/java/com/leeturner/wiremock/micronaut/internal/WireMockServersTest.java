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
        .containsEntry("wiremock.micronaut.servers.b.registry-key", TwoServers.class.getName())
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
