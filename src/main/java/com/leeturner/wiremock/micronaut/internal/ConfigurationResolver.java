package com.leeturner.wiremock.micronaut.internal;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import com.leeturner.wiremock.micronaut.EnableWireMock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.platform.commons.support.AnnotationSupport;

/** Turns WireMock annotations on a test class into a validated server list. */
public final class ConfigurationResolver {

  public static final Set<String> DEFAULT_PROPERTY_NAMES =
      Set.of(
          "wiremock.server.port",
          "wiremock.server.httpsPort",
          "wiremock.server.baseUrl",
          "wiremock.server.httpsBaseUrl");

  private static final Pattern BEAN_NAME = Pattern.compile("[a-z0-9][a-z0-9-]*");

  private static final String NESTED_DECLARATION =
      "%s cannot be declared on the @Nested class %s. A nested class shares the WireMock servers"
          + " of its enclosing class %s, so this configuration would be ignored. Move it to %s.";

  @ConfigureWireMock
  private static final class DefaultServer {}

  private static final ConfigureWireMock DEFAULT_SERVER =
      DefaultServer.class.getAnnotation(ConfigureWireMock.class);

  private ConfigurationResolver() {}

  public static boolean isNested(Class<?> testClass) {
    return AnnotationSupport.isAnnotated(testClass, Nested.class);
  }

  public static Class<?> rootTestClass(Class<?> testClass) {
    Class<?> current = testClass;
    while (isNested(current) && current.getEnclosingClass() != null) {
      current = current.getEnclosingClass();
    }
    return current;
  }

  public static boolean isManaged(Class<?> testClass) {
    Class<?> root = rootTestClass(testClass);
    return AnnotationSupport.isAnnotated(root, EnableWireMock.class)
        || !AnnotationSupport.findRepeatableAnnotations(root, ConfigureWireMock.class).isEmpty();
  }

  public static List<ConfigureWireMock> resolve(Class<?> testClass) {
    rejectNestedDeclarations(testClass);
    Class<?> root = rootTestClass(testClass);
    if (!isManaged(root)) {
      return List.of();
    }
    List<ConfigureWireMock> configs = new ArrayList<>();
    AnnotationSupport.findAnnotation(root, EnableWireMock.class)
        .ifPresent(enable -> configs.addAll(Arrays.asList(enable.value())));
    configs.addAll(AnnotationSupport.findRepeatableAnnotations(root, ConfigureWireMock.class));
    if (configs.isEmpty()) {
      configs.add(DEFAULT_SERVER);
    }
    validate(root, configs);
    return List.copyOf(configs);
  }

  public static Map<String, List<String>> propertyOwners(List<ConfigureWireMock> configs) {
    Map<String, List<String>> owners = new LinkedHashMap<>();
    for (ConfigureWireMock config : configs) {
      Set<String> names = new LinkedHashSet<>();
      Stream.of(
              config.portProperties(),
              config.httpsPortProperties(),
              config.baseUrlProperties(),
              config.httpsBaseUrlProperties())
          .flatMap(Arrays::stream)
          .filter(name -> !name.isBlank())
          .forEach(names::add);
      names.forEach(
          name -> owners.computeIfAbsent(name, k -> new ArrayList<>()).add(config.name()));
    }
    return owners;
  }

  private static void rejectNestedDeclarations(Class<?> testClass) {
    for (Class<?> c = testClass;
        isNested(c) && c.getEnclosingClass() != null;
        c = c.getEnclosingClass()) {
      String annotation = null;
      if (c.getDeclaredAnnotation(EnableWireMock.class) != null) {
        annotation = "@EnableWireMock";
      } else if (c.getDeclaredAnnotationsByType(ConfigureWireMock.class).length > 0) {
        annotation = "@ConfigureWireMock";
      }
      if (annotation != null) {
        Class<?> root = rootTestClass(c);
        throw fail(
            NESTED_DECLARATION, annotation, c.getName(), root.getName(), root.getSimpleName());
      }
    }
  }

  private static void validate(Class<?> root, List<ConfigureWireMock> configs) {
    String testClass = root.getName();
    List<String> duplicateNames =
        configs.stream()
            .collect(groupingBy(ConfigureWireMock::name, LinkedHashMap::new, counting()))
            .entrySet()
            .stream()
            .filter(e -> e.getValue() > 1)
            .map(Map.Entry::getKey)
            .toList();
    if (!duplicateNames.isEmpty()) {
      throw fail(
          "Duplicate WireMock server name(s) %s on %s. Give each @ConfigureWireMock a unique name.",
          duplicateNames, testClass);
    }
    for (ConfigureWireMock c : configs) {
      if (c.port() == -1 && c.httpsPort() == -1) {
        throw fail(
            "WireMock server '%s' on %s has both HTTP (port = -1) and HTTPS (httpsPort = -1)"
                + " disabled.",
            c.name(), testClass);
      }
      if (c.port() > 0 && c.port() == c.httpsPort()) {
        throw fail(
            "WireMock server '%s' on %s uses port %d for both HTTP and HTTPS.",
            c.name(), testClass, c.port());
      }
      if (c.registerBean() && !BEAN_NAME.matcher(c.name()).matches()) {
        throw fail(
            "WireMock server '%s' on %s has registerBean = true, so its name must match %s (it"
                + " becomes part of a Micronaut property key).",
            c.name(), testClass, BEAN_NAME.pattern());
      }
    }
    List<Integer> reusedPorts =
        configs.stream()
            .flatMap(c -> Stream.of(c.port(), c.httpsPort()))
            .filter(port -> port > 0)
            .collect(groupingBy(identity(), TreeMap::new, counting()))
            .entrySet()
            .stream()
            .filter(e -> e.getValue() > 1)
            .map(Map.Entry::getKey)
            .toList();
    if (!reusedPorts.isEmpty()) {
      throw fail(
          "Static port(s) %s are used by more than one WireMock server on %s.",
          reusedPorts, testClass);
    }
    propertyOwners(configs)
        .forEach(
            (property, owners) -> {
              if (owners.size() > 1 && !DEFAULT_PROPERTY_NAMES.contains(property)) {
                throw fail(
                    "Property '%s' is bound by more than one WireMock server on %s: %s. Give each"
                        + " server its own property name.",
                    property, testClass, owners);
              }
            });
  }

  private static ExtensionConfigurationException fail(String format, Object... args) {
    return new ExtensionConfigurationException(format.formatted(args));
  }
}
