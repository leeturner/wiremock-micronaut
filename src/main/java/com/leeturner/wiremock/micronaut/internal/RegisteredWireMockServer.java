package com.leeturner.wiremock.micronaut.internal;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;

/** One entry per {@code registerBean} server, driven by {@link ServerProperties}. */
@EachProperty("wiremock.micronaut.servers")
final class RegisteredWireMockServer {
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
