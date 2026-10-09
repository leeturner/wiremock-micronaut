package com.leeturner.wiremock.micronaut.internal;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.ClasspathFileSource;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.Extension;
import com.github.tomakehurst.wiremock.extension.ExtensionFactory;
import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import com.leeturner.wiremock.micronaut.WireMockConfigurationCustomizer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Builds and starts one WireMock server from its annotation. */
final class WireMockServerCreator {
  private static final Logger LOG = LoggerFactory.getLogger(WireMockServerCreator.class);

  private WireMockServerCreator() {}

  static WireMockServer create(ConfigureWireMock options, String rootTestClassName) {
    WireMockConfiguration config =
        WireMockConfiguration.options().notifier(new Slf4jNotifier(options.name()));
    configurePorts(config, options);
    configureTls(config, options);
    configureFiles(config, options, rootTestClassName);
    if (options.extensions().length > 0) {
      config.extensions(
          Arrays.stream(options.extensions())
              .map(type -> instantiate(type, options))
              .toArray(Extension[]::new));
    }
    if (options.extensionFactories().length > 0) {
      config.extensionFactories(
          Arrays.stream(options.extensionFactories())
              .map(type -> instantiate(type, options))
              .toArray(ExtensionFactory[]::new));
    }
    if (options.globalTemplating()) {
      config.globalTemplating(true);
    }
    for (Class<? extends WireMockConfigurationCustomizer> type :
        options.configurationCustomizers()) {
      WireMockConfigurationCustomizer customizer = instantiate(type, options);
      LOG.debug("Applying {} to WireMock server '{}'", type.getName(), options.name());
      customizer.customize(config, options);
    }
    WireMockServer server;
    try {
      server = new WireMockServer(config);
      server.start();
    } catch (RuntimeException e) {
      throw new IllegalStateException(
          "Failed to start WireMock server '%s' (port = %d, httpsPort = %d) for %s"
              .formatted(options.name(), options.port(), options.httpsPort(), rootTestClassName),
          e);
    }
    LOG.info(
        "WireMock '{}' started on {}",
        options.name(),
        server.isHttpEnabled()
            ? "http://localhost:" + server.port()
            : "https://localhost:" + server.httpsPort());
    return server;
  }

  private static void configurePorts(WireMockConfiguration config, ConfigureWireMock options) {
    if (options.port() == -1) {
      config.httpDisabled(true);
    } else if (options.port() == 0) {
      config.dynamicPort();
    } else {
      config.port(options.port());
    }
    if (options.httpsPort() == 0) {
      config.dynamicHttpsPort();
    } else if (options.httpsPort() > 0) {
      config.httpsPort(options.httpsPort());
    }
  }

  private static void configureTls(WireMockConfiguration config, ConfigureWireMock options) {
    setIfPresent(options.keystorePath(), config::keystorePath);
    setIfPresent(options.keystorePassword(), config::keystorePassword);
    setIfPresent(options.keystoreType(), config::keystoreType);
    setIfPresent(options.keyManagerPassword(), config::keyManagerPassword);
    setIfPresent(options.trustStorePath(), config::trustStorePath);
    setIfPresent(options.trustStorePassword(), config::trustStorePassword);
    setIfPresent(options.trustStoreType(), config::trustStoreType);
    if (options.needClientAuth()) {
      config.needClientAuth(true);
    }
  }

  private static void configureFiles(
      WireMockConfiguration config, ConfigureWireMock options, String rootTestClassName) {
    if (options.filesUnderDirectory().length > 0) {
      String dir =
          firstExistingStubDirectory(Arrays.asList(options.filesUnderDirectory()))
              .orElseThrow(
                  () ->
                      new ExtensionConfigurationException(
                          ("None of filesUnderDirectory %s for WireMock server '%s' on %s contains"
                                  + " a 'mappings', '__files' or 'message-mappings' directory.")
                              .formatted(
                                  Arrays.toString(options.filesUnderDirectory()),
                                  options.name(),
                                  rootTestClassName)));
      LOG.debug("WireMock '{}' serves stubs from directory {}", options.name(), dir);
      config.usingFilesUnderDirectory(dir);
    } else if (!options.filesUnderClasspath().isBlank()) {
      String resource = options.filesUnderClasspath();
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      if (loader.getResource(resource) == null) {
        throw new ExtensionConfigurationException(
            ("filesUnderClasspath '%s' for WireMock server '%s' on %s was not found on the"
                    + " classpath.")
                .formatted(resource, options.name(), rootTestClassName));
      }
      LOG.debug("WireMock '{}' serves stubs from classpath {}", options.name(), resource);
      // WireMock's usingFilesUnderClasspath prefers its own class loader, which can't see test
      // resources when they are only on the context class loader (as under Pyronaut).
      config.fileSource(new ClasspathFileSource(loader, resource));
    } else {
      firstExistingStubDirectory(ConfigureWireMock.DEFAULT_FILES_UNDER_DIRECTORY)
          .ifPresentOrElse(
              dir -> {
                LOG.debug("WireMock '{}' serves stubs from directory {}", options.name(), dir);
                config.usingFilesUnderDirectory(dir);
              },
              () ->
                  LOG.info(
                      "No stub directory found for WireMock server '{}' (looked in {})",
                      options.name(),
                      ConfigureWireMock.DEFAULT_FILES_UNDER_DIRECTORY));
    }
  }

  static Optional<String> firstExistingStubDirectory(List<String> candidates) {
    return candidates.stream()
        .filter(
            dir ->
                Files.isDirectory(Path.of(dir, "mappings"))
                    || Files.isDirectory(Path.of(dir, "__files"))
                    || Files.isDirectory(Path.of(dir, "message-mappings")))
        .findFirst();
  }

  private static void setIfPresent(String value, Consumer<String> setter) {
    if (!value.isBlank()) {
      setter.accept(value);
    }
  }

  private static <T> T instantiate(Class<? extends T> type, ConfigureWireMock options) {
    try {
      return type.getConstructor().newInstance();
    } catch (NoSuchMethodException e) {
      throw new ExtensionConfigurationException(
          "%s configured on WireMock server '%s' must have a public no-arg constructor."
              .formatted(type.getName(), options.name()),
          e);
    } catch (ReflectiveOperationException e) {
      throw new ExtensionConfigurationException(
          "Could not create %s for WireMock server '%s'.".formatted(type.getName(), options.name()),
          e);
    }
  }
}
