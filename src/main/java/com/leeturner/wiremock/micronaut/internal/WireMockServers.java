package com.leeturner.wiremock.micronaut.internal;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Running servers per root test class. Shared by the property factory and the JUnit extension;
 * whichever calls {@link #getOrStart} first starts the servers.
 */
final class WireMockServers {
  private static final Logger LOG = LoggerFactory.getLogger(WireMockServers.class);
  private static final ConcurrentMap<String, Map<String, RunningServer>> SERVERS =
      new ConcurrentHashMap<>();

  private WireMockServers() {}

  public static Map<String, RunningServer> getOrStart(Class<?> testClass) {
    List<ConfigureWireMock> configs = ConfigurationResolver.resolve(testClass);
    if (configs.isEmpty()) {
      return Map.of();
    }
    String root = ConfigurationResolver.rootTestClass(testClass).getName();
    return SERVERS.computeIfAbsent(root, key -> startAll(key, configs));
  }

  public static WireMockServer get(String rootTestClassName, String serverName) {
    Map<String, RunningServer> servers = SERVERS.get(rootTestClassName);
    RunningServer running = servers == null ? null : servers.get(serverName);
    if (running == null) {
      throw new IllegalStateException(
          "No running WireMock server '%s' for test class %s"
              .formatted(serverName, rootTestClassName));
    }
    return running.server();
  }

  public static void stop(Class<?> testClass) {
    Map<String, RunningServer> servers =
        SERVERS.remove(ConfigurationResolver.rootTestClass(testClass).getName());
    if (servers != null) {
      stopAll(servers.values());
    }
  }

  public static Set<String> runningRootTestClasses() {
    return Set.copyOf(SERVERS.keySet());
  }

  private static Map<String, RunningServer> startAll(
      String rootTestClassName, List<ConfigureWireMock> configs) {
    Map<String, RunningServer> started = new LinkedHashMap<>();
    try {
      for (ConfigureWireMock options : configs) {
        WireMockServer server = WireMockServerCreator.create(options, rootTestClassName);
        started.put(options.name(), new RunningServer(options, server));
      }
    } catch (RuntimeException e) {
      stopAll(started.values());
      throw e;
    }
    return Collections.unmodifiableMap(started);
  }

  private static void stopAll(Collection<RunningServer> servers) {
    for (RunningServer running : servers) {
      try {
        if (running.server().isRunning()) {
          running.server().stop();
          LOG.info("WireMock '{}' stopped", running.options().name());
        }
      } catch (RuntimeException e) {
        LOG.warn("Failed to stop WireMock server '{}'", running.options().name(), e);
      }
    }
  }
}
