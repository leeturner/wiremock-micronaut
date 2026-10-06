package com.leeturner.wiremock.micronaut.fixtures;

import com.leeturner.wiremock.micronaut.internal.WireMockServers;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import java.util.Set;

@Context
@Requires(property = "boom.enabled", value = "true")
public class Boom {
  /** What was running when the context blew up; null if the constructor never ran. */
  public static volatile Set<String> runningWhenFailing;

  public Boom() {
    runningWhenFailing = WireMockServers.runningRootTestClasses();
    throw new IllegalStateException("boom");
  }
}
