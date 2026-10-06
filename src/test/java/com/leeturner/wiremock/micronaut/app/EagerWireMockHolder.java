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
