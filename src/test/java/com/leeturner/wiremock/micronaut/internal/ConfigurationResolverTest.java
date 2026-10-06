package com.leeturner.wiremock.micronaut.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.leeturner.wiremock.micronaut.ConfigureWireMock;
import com.leeturner.wiremock.micronaut.EnableWireMock;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;

class ConfigurationResolverTest {

  static class Plain {}

  @EnableWireMock
  static class Bare {}

  @EnableWireMock({@ConfigureWireMock(name = "a"), @ConfigureWireMock(name = "b")})
  static class Two {}

  @ConfigureWireMock(name = "a")
  @ConfigureWireMock(name = "b")
  static class Standalone {}

  @EnableWireMock
  @ConfigureWireMock(name = "users")
  static class BarePlusStandalone {}

  static class InheritsBare extends Bare {}

  @ConfigureWireMock(name = "users", baseUrlProperties = "users.url")
  abstract static class ConfiguredBase {}

  static class ConfiguredChild extends ConfiguredBase {}

  @EnableWireMock
  static class Outer {
    @Nested
    class Inner {
      @Nested
      class Deeper {}
    }

    @Nested
    @ConfigureWireMock(name = "x")
    class BadInner {}

    @Nested
    @EnableWireMock
    class BadEnableInner {}
  }

  @EnableWireMock(@ConfigureWireMock(name = "users-api-2", registerBean = true))
  static class GoodBeanName {}

  @Retention(RetentionPolicy.RUNTIME)
  @ConfigureWireMock(name = "meta")
  @interface MetaServer {}

  @MetaServer
  static class MetaAnnotated {}

  @ConfigureWireMock(name = "users")
  static class UsersParent {}

  @ConfigureWireMock(name = "users", baseUrlProperties = "child.url")
  static class UsersChild extends UsersParent {}

  @EnableWireMock({@ConfigureWireMock(name = "a"), @ConfigureWireMock(name = "a")})
  static class DuplicateNames {}

  @EnableWireMock(@ConfigureWireMock(name = "off", port = -1, httpsPort = -1))
  static class BothDisabled {}

  @EnableWireMock(@ConfigureWireMock(name = "same", port = 18080, httpsPort = 18080))
  static class SamePort {}

  @EnableWireMock({
    @ConfigureWireMock(name = "a", port = 18081),
    @ConfigureWireMock(name = "b", port = 18081)
  })
  static class ReusedPort {}

  @EnableWireMock(@ConfigureWireMock(name = "Users", registerBean = true))
  static class BadBeanName {}

  @EnableWireMock({
    @ConfigureWireMock(name = "a", baseUrlProperties = "shared.url"),
    @ConfigureWireMock(name = "b", baseUrlProperties = "shared.url")
  })
  static class ExplicitClash {}

  @Test
  void configureWireMockOnASuperclassIsInherited() {
    assertThat(ConfigurationResolver.resolve(ConfiguredChild.class))
        .extracting(ConfigureWireMock::name)
        .containsExactly("users");
  }

  @Test
  void unannotatedClassIsNotManaged() {
    assertThat(ConfigurationResolver.isManaged(Plain.class)).isFalse();
    assertThat(ConfigurationResolver.resolve(Plain.class)).isEmpty();
  }

  @Test
  void bareEnableWireMockGivesTheDefaultServer() {
    assertThat(ConfigurationResolver.resolve(Bare.class))
        .singleElement()
        .satisfies(c -> assertThat(c.name()).isEqualTo("wiremock"));
  }

  @Test
  void enableAndStandaloneAnnotationsAreCollected() {
    assertThat(ConfigurationResolver.resolve(Two.class))
        .extracting(ConfigureWireMock::name)
        .containsExactly("a", "b");
    assertThat(ConfigurationResolver.resolve(Standalone.class))
        .extracting(ConfigureWireMock::name)
        .containsExactly("a", "b");
    assertThat(ConfigurationResolver.isManaged(Standalone.class)).isTrue();
  }

  @Test
  void defaultServerIsOnlyAddedWhenNothingIsConfigured() {
    assertThat(ConfigurationResolver.resolve(BarePlusStandalone.class))
        .extracting(ConfigureWireMock::name)
        .containsExactly("users");
  }

  @Test
  void enableWireMockIsInherited() {
    assertThat(ConfigurationResolver.resolve(InheritsBare.class)).hasSize(1);
  }

  @Test
  void nestedClassesResolveToTheirOutermostClass() {
    assertThat(ConfigurationResolver.isNested(Outer.Inner.class)).isTrue();
    assertThat(ConfigurationResolver.rootTestClass(Outer.Inner.Deeper.class))
        .isEqualTo(Outer.class);
    assertThat(ConfigurationResolver.resolve(Outer.Inner.Deeper.class))
        .extracting(ConfigureWireMock::name)
        .containsExactly("wiremock");
  }

  @Test
  void declarationsOnNestedClassesAreRejected() {
    assertThatThrownBy(() -> ConfigurationResolver.resolve(Outer.BadInner.class))
        .isInstanceOf(ExtensionConfigurationException.class)
        .hasMessageContaining("@ConfigureWireMock cannot be declared on the @Nested class")
        .hasMessageContaining("Move it to Outer");
  }

  @Test
  void duplicateNamesFail() {
    assertThatThrownBy(() -> ConfigurationResolver.resolve(DuplicateNames.class))
        .isInstanceOf(ExtensionConfigurationException.class)
        .hasMessageContaining("Duplicate WireMock server name(s) [a]");
  }

  @Test
  void invalidPortsFail() {
    assertThatThrownBy(() -> ConfigurationResolver.resolve(BothDisabled.class))
        .hasMessageContaining("'off'")
        .hasMessageContaining("both HTTP (port = -1) and HTTPS (httpsPort = -1) disabled");
    assertThatThrownBy(() -> ConfigurationResolver.resolve(SamePort.class))
        .hasMessageContaining("uses port 18080 for both HTTP and HTTPS");
    assertThatThrownBy(() -> ConfigurationResolver.resolve(ReusedPort.class))
        .hasMessageContaining("Static port(s) [18081]");
  }

  @Test
  void registerBeanNamesMustBePropertyKeySafe() {
    assertThatThrownBy(() -> ConfigurationResolver.resolve(BadBeanName.class))
        .isInstanceOf(ExtensionConfigurationException.class)
        .hasMessageContaining("'Users'")
        .hasMessageContaining("[a-z0-9][a-z0-9-]*");
  }

  @Test
  void explicitPropertyClashFailsButSharedDefaultsDoNot() {
    assertThatThrownBy(() -> ConfigurationResolver.resolve(ExplicitClash.class))
        .isInstanceOf(ExtensionConfigurationException.class)
        .hasMessageContaining("Property 'shared.url' is bound by more than one WireMock server")
        .hasMessageContaining("[a, b]");
    // Two servers share every default property name; that is allowed.
    assertThat(ConfigurationResolver.resolve(Two.class)).hasSize(2);
    assertThat(ConfigurationResolver.propertyOwners(ConfigurationResolver.resolve(Two.class)))
        .containsEntry("wiremock.server.port", java.util.List.of("a", "b"));
  }

  @Test
  void enableWireMockOnNestedClassesIsRejected() {
    assertThatThrownBy(() -> ConfigurationResolver.resolve(Outer.BadEnableInner.class))
        .isInstanceOf(ExtensionConfigurationException.class)
        .hasMessageContaining("@EnableWireMock cannot be declared on the @Nested class")
        .hasMessageContaining("Move it to Outer");
  }

  @Test
  void validRegisterBeanNameIsAccepted() {
    assertThat(ConfigurationResolver.resolve(GoodBeanName.class))
        .extracting(ConfigureWireMock::name)
        .containsExactly("users-api-2");
  }

  @Test
  void metaAnnotatedConfigureWireMockIsResolved() {
    assertThat(ConfigurationResolver.isManaged(MetaAnnotated.class)).isTrue();
    assertThat(ConfigurationResolver.resolve(MetaAnnotated.class))
        .extracting(ConfigureWireMock::name)
        .containsExactly("meta");
  }

  @Test
  void subclassRedeclaringAParentServerNameFails() {
    assertThatThrownBy(() -> ConfigurationResolver.resolve(UsersChild.class))
        .isInstanceOf(ExtensionConfigurationException.class)
        .hasMessageContaining("Duplicate WireMock server name(s) [users]");
  }
}
