package com.leeturner.wiremock.micronaut.internal;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;

/** Exposes {@code registerBean} servers as {@code @Named} Micronaut beans. */
@Factory
final class WireMockServerBeanFactory {

  @EachBean(RegisteredWireMockServer.class)
  public WireMockServer wireMockServer(RegisteredWireMockServer registration) {
    return WireMockServers.get(registration.getRegistryKey(), registration.getName());
  }
}
