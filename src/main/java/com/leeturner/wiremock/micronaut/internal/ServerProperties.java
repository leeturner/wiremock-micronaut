package com.leeturner.wiremock.micronaut.internal;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Micronaut properties published for a set of running servers. */
public final class ServerProperties {
  public static final String REGISTRY_KEY_PROPERTY = "wiremock.micronaut.servers.%s.registry-key";
  private static final Logger LOG = LoggerFactory.getLogger(ServerProperties.class);

  private ServerProperties() {}

  public static Map<String, String> of(
      String rootTestClassName, Collection<RunningServer> servers) {
    Map<String, List<String>> owners =
        ConfigurationResolver.propertyOwners(servers.stream().map(RunningServer::options).toList());
    Map<String, String> properties = new LinkedHashMap<>();
    for (RunningServer running : servers) {
      ConfigureWireMock options = running.options();
      WireMockServer server = running.server();
      if (server.isHttpEnabled()) {
        bind(properties, owners, options.baseUrlProperties(), "http://localhost:" + server.port());
        bind(properties, owners, options.portProperties(), String.valueOf(server.port()));
      }
      if (server.isHttpsEnabled()) {
        bind(
            properties,
            owners,
            options.httpsBaseUrlProperties(),
            "https://localhost:" + server.httpsPort());
        bind(properties, owners, options.httpsPortProperties(), String.valueOf(server.httpsPort()));
      }
      if (options.registerBean()) {
        properties.put(REGISTRY_KEY_PROPERTY.formatted(options.name()), rootTestClassName);
      }
    }
    return Collections.unmodifiableMap(properties);
  }

  private static void bind(
      Map<String, String> properties,
      Map<String, List<String>> owners,
      String[] names,
      String value) {
    for (String name : names) {
      if (name.isBlank()) {
        continue;
      }
      List<String> sharedBy = owners.getOrDefault(name, List.of());
      if (sharedBy.size() > 1) {
        LOG.debug("Not binding '{}': it is shared by WireMock servers {}", name, sharedBy);
        continue;
      }
      properties.put(name, value);
    }
  }
}
