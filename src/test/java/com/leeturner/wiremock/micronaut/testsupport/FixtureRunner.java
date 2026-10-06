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
        .configurationParameter(
            "junit.jupiter.execution.parallel.mode.classes.default", "concurrent")
        // Fixed, so a 1-CPU runner still overlaps the classes.
        .configurationParameter("junit.jupiter.execution.parallel.config.strategy", "fixed")
        .configurationParameter("junit.jupiter.execution.parallel.config.fixed.parallelism", "2")
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
