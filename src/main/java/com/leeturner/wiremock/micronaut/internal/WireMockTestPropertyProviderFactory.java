package com.leeturner.wiremock.micronaut.internal;

import io.micronaut.test.support.TestPropertyProvider;
import io.micronaut.test.support.TestPropertyProviderFactory;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Called by micronaut-test while it builds the application context: starts the test class's
 * WireMock servers and returns their properties, so beans see them on creation.
 */
public final class WireMockTestPropertyProviderFactory implements TestPropertyProviderFactory {
  private static final Logger LOG =
      LoggerFactory.getLogger(WireMockTestPropertyProviderFactory.class);

  @Override
  public TestPropertyProvider create(Map<String, Object> availableProperties, Class<?> testClass) {
    if (!ConfigurationResolver.isManaged(testClass)) {
      return Map::of;
    }
    Class<?> root = ConfigurationResolver.rootTestClass(testClass);
    Map<String, String> properties =
        ServerProperties.of(root.getName(), WireMockServers.getOrStart(testClass).values());
    LOG.info("Binding WireMock properties for {}: {}", root.getSimpleName(), properties);
    return () -> properties;
  }
}
