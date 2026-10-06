package com.leeturner.wiremock.micronaut.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WireMockTestPropertyProviderFactoryTest {
  static class Plain {}

  @Test
  void unmanagedClassesGetNoPropertiesAndNoServers() {
    var provider = new WireMockTestPropertyProviderFactory().create(Map.of(), Plain.class);
    assertThat(provider.getProperties()).isEmpty();
    assertThat(WireMockServers.runningRootTestClasses()).doesNotContain(Plain.class.getName());
  }
}
