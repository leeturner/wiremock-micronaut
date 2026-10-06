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
