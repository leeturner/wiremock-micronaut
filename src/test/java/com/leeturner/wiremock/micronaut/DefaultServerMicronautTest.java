package com.leeturner.wiremock.micronaut;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.leeturner.wiremock.micronaut.testsupport.Http;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@MicronautTest
@EnableWireMock
class DefaultServerMicronautTest {

  @Inject ApplicationContext context;

  @InjectWireMock WireMockServer wiremock;

  @Test
  void defaultServerBindsTheDefaultProperties() {
    assertThat(context.getRequiredProperty("wiremock.server.baseUrl", String.class))
        .isEqualTo("http://localhost:" + wiremock.port());
    assertThat(context.getRequiredProperty("wiremock.server.port", Integer.class))
        .isEqualTo(wiremock.port());
  }

  @Test
  void staticDslWorksInsideMicronautTests() {
    stubFor(get("/hello").willReturn(ok("hi")));
    assertThat(Http.get(wiremock.baseUrl() + "/hello").body()).isEqualTo("hi");
  }
}
