package com.leeturner.wiremock.micronaut.internal;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.leeturner.wiremock.micronaut.InjectWireMock;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ReflectionSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Injection, per-test reset, static DSL and cleanup for {@code @EnableWireMock} tests. */
public final class WireMockMicronautExtension
    implements BeforeAllCallback,
        TestInstancePostProcessor,
        BeforeEachCallback,
        AfterEachCallback,
        AfterAllCallback,
        ParameterResolver {

  private static final Logger LOG = LoggerFactory.getLogger(WireMockMicronautExtension.class);
  private static final String MICRONAUT_TEST =
      "io.micronaut.test.extensions.junit5.annotation.MicronautTest";

  @Override
  public void beforeAll(ExtensionContext context) {
    Class<?> testClass = context.getRequiredTestClass();
    ConfigurationResolver.resolve(testClass); // fails fast on invalid or @Nested declarations
    Class<?> root = ConfigurationResolver.rootTestClass(testClass);
    if (root == testClass && !hasMicronautTest(root)) {
      LOG.warn(
          "{} uses WireMock without @MicronautTest: servers run, but their properties are not"
              + " bound into Micronaut",
          root.getName());
    }
    rejectInjectParameters(testClass);
    WireMockServers.getOrStart(testClass);
  }

  /** micronaut-test claims every unqualified WireMockServer parameter once a bean exists. */
  private static void rejectInjectParameters(Class<?> testClass) {
    if (ConfigurationResolver.resolve(testClass).stream().noneMatch(c -> c.registerBean())) {
      return;
    }
    for (Method method :
        ReflectionSupport.findMethods(testClass, m -> true, HierarchyTraversalMode.BOTTOM_UP)) {
      for (Parameter parameter : method.getParameters()) {
        if (parameter.isAnnotationPresent(InjectWireMock.class)) {
          throw new ExtensionConfigurationException(
              ("@InjectWireMock method parameters are not supported in test classes with a"
                      + " registerBean server; inject the server with @Named(\"<name>\") or use an"
                      + " @InjectWireMock field (%s, parameter '%s' of method %s)")
                  .formatted(testClass.getName(), parameter.getName(), method.getName()));
        }
      }
    }
  }

  @Override
  public void postProcessTestInstance(Object testInstance, ExtensionContext context)
      throws IllegalAccessException {
    Class<?> testClass = testInstance.getClass();
    Map<String, RunningServer> servers = WireMockServers.getOrStart(testClass);
    for (Field field : AnnotationSupport.findAnnotatedFields(testClass, InjectWireMock.class)) {
      requireServerType(
          field.getType(), "field '%s' of %s".formatted(field.getName(), testClass.getName()));
      field.setAccessible(true);
      field.set(
          testInstance,
          lookup(servers, field.getAnnotation(InjectWireMock.class).value(), testClass));
    }
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    Class<?> testClass = context.getRequiredTestClass();
    Map<String, RunningServer> servers = WireMockServers.getOrStart(testClass);
    servers.values().stream()
        .filter(running -> running.options().resetWireMockServer())
        .forEach(running -> running.server().resetAll());
    if (servers.size() == 1) {
      WireMockServer server = servers.values().iterator().next().server();
      WireMock.configureFor(
          server.isHttpsEnabled()
              ? WireMock.create().https().host("localhost").port(server.httpsPort()).build()
              : WireMock.create().http().host("localhost").port(server.port()).build());
    } else {
      LOG.debug(
          "{} WireMock servers configured for {}; the static WireMock client is not configured",
          servers.size(),
          testClass.getName());
    }
  }

  @Override
  public void afterEach(ExtensionContext context) {
    WireMock.configureFor(-1);
  }

  @Override
  public void afterAll(ExtensionContext context) {
    Class<?> testClass = context.getRequiredTestClass();
    if (!ConfigurationResolver.isNested(testClass)) {
      WireMockServers.stop(testClass);
    }
  }

  @Override
  public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext context) {
    return parameterContext.isAnnotated(InjectWireMock.class);
  }

  @Override
  public Object resolveParameter(ParameterContext parameterContext, ExtensionContext context) {
    Parameter parameter = parameterContext.getParameter();
    requireServerType(
        parameter.getType(),
        "parameter '%s' of %s"
            .formatted(parameter.getName(), parameterContext.getDeclaringExecutable()));
    Class<?> testClass = context.getRequiredTestClass();
    String name = parameterContext.findAnnotation(InjectWireMock.class).orElseThrow().value();
    return lookup(WireMockServers.getOrStart(testClass), name, testClass);
  }

  private static WireMockServer lookup(
      Map<String, RunningServer> servers, String name, Class<?> testClass) {
    RunningServer running = servers.get(name);
    if (running == null) {
      throw new ExtensionConfigurationException(
          "No WireMock server named '%s' is configured for %s. Configured servers: %s"
              .formatted(
                  name,
                  ConfigurationResolver.rootTestClass(testClass).getName(),
                  servers.keySet()));
    }
    return running.server();
  }

  private static void requireServerType(Class<?> type, String member) {
    if (!type.isAssignableFrom(WireMockServer.class)) {
      throw new ExtensionConfigurationException(
          "@InjectWireMock %s has type %s; only WireMockServer (or a supertype) is supported"
              .formatted(member, type.getName()));
    }
  }

  private static boolean hasMicronautTest(Class<?> root) {
    try {
      Class<? extends Annotation> micronautTest =
          Class.forName(MICRONAUT_TEST, false, root.getClassLoader()).asSubclass(Annotation.class);
      return AnnotationSupport.isAnnotated(root, micronautTest);
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
}
