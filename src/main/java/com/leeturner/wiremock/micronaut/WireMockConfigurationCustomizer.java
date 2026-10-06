package com.leeturner.wiremock.micronaut;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/** Last-step customization of a server's configuration. Needs a public no-arg constructor. */
@FunctionalInterface
public interface WireMockConfigurationCustomizer {
  void customize(WireMockConfiguration configuration, ConfigureWireMock options);
}
