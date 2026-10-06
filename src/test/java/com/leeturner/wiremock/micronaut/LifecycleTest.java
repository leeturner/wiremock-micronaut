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
