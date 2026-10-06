package com.leeturner.wiremock.micronaut;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leeturner.wiremock.micronaut.internal.WireMockMicronautExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

class PublicApiTest {

  @ConfigureWireMock
  static class Defaults {}

  @EnableWireMock
  static class Enabled {}

  static class InheritsEnabled extends Enabled {}

  static class Injected {
    @InjectWireMock WireMockServer server;
  }

  @Test
  void configureWireMockDefaultsMatchWireMockSpringBoot() {
    ConfigureWireMock d = Defaults.class.getAnnotation(ConfigureWireMock.class);
    assertThat(d.name()).isEqualTo("wiremock");
    assertThat(d.port()).isZero();
    assertThat(d.httpsPort()).isEqualTo(-1);
    assertThat(d.portProperties()).containsExactly("wiremock.server.port");
    assertThat(d.httpsPortProperties()).containsExactly("wiremock.server.httpsPort");
    assertThat(d.baseUrlProperties()).containsExactly("wiremock.server.baseUrl");
    assertThat(d.httpsBaseUrlProperties()).containsExactly("wiremock.server.httpsBaseUrl");
    assertThat(d.filesUnderClasspath()).isEmpty();
    assertThat(d.filesUnderDirectory()).isEmpty();
    assertThat(d.extensions()).isEmpty();
    assertThat(d.extensionFactories()).isEmpty();
    assertThat(d.configurationCustomizers()).isEmpty();
    assertThat(d.resetWireMockServer()).isTrue();
    assertThat(d.registerBean()).isFalse();
    assertThat(d.globalTemplating()).isFalse();
    assertThat(d.keystorePath()).isEmpty();
    assertThat(d.needClientAuth()).isFalse();
    assertThat(ConfigureWireMock.DEFAULT_FILES_UNDER_DIRECTORY)
        .startsWith("wiremock", "stubs", "mappings", "src/test/resources/wiremock");
  }

  @Test
  void enableWireMockIsInheritedAndRegistersTheExtension() {
    assertThat(InheritsEnabled.class.isAnnotationPresent(EnableWireMock.class)).isTrue();
    assertThat(EnableWireMock.class.getAnnotation(ExtendWith.class).value())
        .containsExactly(WireMockMicronautExtension.class);
    assertThat(ConfigureWireMock.class.getAnnotation(ExtendWith.class).value())
        .containsExactly(WireMockMicronautExtension.class);
  }

  @Test
  void injectWireMockDefaultsToTheDefaultServerName() throws Exception {
    InjectWireMock inject =
        Injected.class.getDeclaredField("server").getAnnotation(InjectWireMock.class);
    assertThat(inject.value()).isEqualTo("wiremock");
  }
}
