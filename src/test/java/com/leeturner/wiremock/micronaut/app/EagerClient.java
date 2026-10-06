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
