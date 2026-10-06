package com.leeturner.wiremock.micronaut.internal;

import java.util.Set;

/** Test-only window onto the package-private registry, for tests outside this package. */
public final class RunningWireMockServers {
  private RunningWireMockServers() {}

  public static Set<String> rootTestClasses() {
    return WireMockServers.runningRootTestClasses();
  }
}
